package io.orangebuffalo.aionify.api

import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.orangebuffalo.aionify.auth.AuthenticationHelper
import io.orangebuffalo.aionify.domain.UserApiAccessTokenRepository
import io.orangebuffalo.aionify.domain.UserRepository
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

/**
 * HTTP filter that authenticates requests to /api/ endpoints using Bearer tokens.
 * The Bearer token must match a UserApiAccessToken in the database.
 * Implements brute force protection by blocking IPs after 10 consecutive failed auth attempts.
 */
@Filter(Filter.MATCH_ALL_PATTERN)
@Requires(property = "micronaut.security.enabled", value = "true", defaultValue = "true")
class ApiAuthenticationFilter(
    private val userApiAccessTokenRepository: UserApiAccessTokenRepository,
    private val userRepository: UserRepository,
    private val apiRateLimitingService: ApiRateLimitingService,
) : HttpServerFilter,
    Ordered {
    private val log = LoggerFactory.getLogger(ApiAuthenticationFilter::class.java)

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
    ): Publisher<MutableHttpResponse<*>> {
        // Only process /api/ paths, skip all others
        if (!request.path.startsWith("/api/")) {
            return chain.proceed(request)
        }

        // Skip authentication for /api/schema endpoint
        if (request.path.startsWith("/api/schema")) {
            log.trace("Skipping authentication for /api/schema endpoint")
            return chain.proceed(request)
        }

        val ipAddress = request.remoteAddress.address.hostAddress

        // Check if IP is blocked due to too many failed attempts
        if (apiRateLimitingService.isBlocked(ipAddress)) {
            log.debug("API authentication: IP {} is blocked due to too many failed attempts", ipAddress)
            return Mono.just(
                HttpResponse
                    .status<Map<String, String>>(HttpStatus.TOO_MANY_REQUESTS)
                    .body(mapOf("error" to "Too many failed authentication attempts. Please try again later.")),
            )
        }

        // Extract Bearer token from Authorization header
        val authHeader = request.headers.get("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("API authentication: Missing or invalid Authorization header from IP {}", ipAddress)
            apiRateLimitingService.recordFailedAttempt(ipAddress)
            return Mono.just(
                HttpResponse
                    .status<Map<String, String>>(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "Missing or invalid Authorization header")),
            )
        }

        val token = authHeader.substring("Bearer ".length).trim()

        // Look up the token in the database
        val apiAccessToken = userApiAccessTokenRepository.findByToken(token).orElse(null)
        if (apiAccessToken == null) {
            log.debug("API authentication: Invalid token from IP {}", ipAddress)
            apiRateLimitingService.recordFailedAttempt(ipAddress)
            return Mono.just(
                HttpResponse
                    .status<Map<String, String>>(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "Invalid API token")),
            )
        }

        // Get the user associated with the token
        val user = userRepository.findById(apiAccessToken.userId).orElse(null)
        if (user == null) {
            log.warn("API authentication: User not found for token from IP {}", ipAddress)
            apiRateLimitingService.recordFailedAttempt(ipAddress)
            return Mono.just(
                HttpResponse
                    .status<Map<String, String>>(HttpStatus.UNAUTHORIZED)
                    .body(mapOf("error" to "Invalid API token")),
            )
        }

        // Clear failed attempts for this IP on successful authentication
        apiRateLimitingService.clearAttempts(ipAddress)

        // Create authentication using shared helper
        val authentication = AuthenticationHelper.createAuthentication(user)

        log.trace("API authentication: Successful for user {} from IP {}", user.userName, ipAddress)

        return chain.proceed(request.setAttribute("micronaut.security.AUTHENTICATION", authentication))
    }
}
