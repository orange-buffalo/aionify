package io.orangebuffalo.aionify.auth

import io.micronaut.context.annotation.Property
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.orangebuffalo.aionify.domain.UserRepository
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

/**
 * Filter that implements "Remember Me" authentication.
 *
 * This filter runs after JWT authentication has been attempted. If no authentication
 * is present (JWT missing or expired), it checks for a remember me cookie and attempts
 * to authenticate the user using that token.
 *
 * If successful, it sets the authentication context so the user is automatically
 * logged in without requiring manual login.
 */
@Filter(patterns = ["/api-ui/**", "/portal/**", "/admin/**"])
class RememberMeAuthenticationFilter(
    private val rememberMeService: RememberMeService,
    private val userRepository: UserRepository,
    @Property(name = "aionify.auth.remember-me.cookie-name", defaultValue = "aionify_remember_me")
    private val rememberMeCookieName: String,
) : HttpServerFilter,
    Ordered {
    private val log = LoggerFactory.getLogger(RememberMeAuthenticationFilter::class.java)

    /**
     * Order is set to run after JWT authentication (which is at HIGHEST - 100).
     * We want to run before other filters that might need authentication.
     */
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
    ): Publisher<MutableHttpResponse<*>> {
        // Skip if already authenticated via JWT
        val existingAuth = AuthenticationHelper.getAuthenticationOrNull(request)
        if (existingAuth != null) {
            log.trace("Request already authenticated via JWT, skipping remember me")
            return chain.proceed(request)
        }

        // Skip login and logout endpoints to avoid interference
        val path = request.path
        if (path == "/api-ui/auth/login" || path == "/api-ui/auth/logout") {
            log.trace("Skipping remember me for auth endpoint: {}", path)
            return chain.proceed(request)
        }

        // Check for remember me cookie
        val rememberMeCookie = request.cookies.get(rememberMeCookieName)
        if (rememberMeCookie == null) {
            log.trace("No remember me cookie found, continuing without authentication")
            return chain.proceed(request)
        }

        // Validate remember me token
        val token = rememberMeCookie.value
        val userAgent = request.headers.get("User-Agent")
        val userId = rememberMeService.validateToken(token, userAgent)

        if (userId == null) {
            log.debug("Invalid or expired remember me token, continuing without authentication")
            return chain.proceed(request)
        }

        // Load user from database
        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            log.warn("Remember me token references non-existent user ID: {}", userId)
            // Invalidate the token since user no longer exists
            rememberMeService.invalidateToken(token)
            return chain.proceed(request)
        }

        // Set authentication on the request
        log.info("User authenticated via remember me token: {}", user.userName)
        return chain.proceed(AuthenticationHelper.setAuthentication(request, user))
    }
}
