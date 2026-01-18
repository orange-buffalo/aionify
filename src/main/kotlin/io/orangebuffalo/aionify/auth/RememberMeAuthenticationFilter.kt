package io.orangebuffalo.aionify.auth

import io.micronaut.context.annotation.Property
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.security.authentication.Authentication
import io.orangebuffalo.aionify.domain.UserRepository
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
@ServerFilter(patterns = ["/api-ui/**", "/portal/**", "/admin/**"])
class RememberMeAuthenticationFilter(
    private val rememberMeService: RememberMeService,
    private val userRepository: UserRepository,
    @Property(name = "aionify.auth.remember-me.cookie-name", defaultValue = "aionify_remember_me")
    private val rememberMeCookieName: String,
) : HttpFilter, Ordered {
    private val log = LoggerFactory.getLogger(RememberMeAuthenticationFilter::class.java)

    /**
     * Order is set to run after JWT authentication (which is at HIGHEST - 100).
     * We want to run before other filters that might need authentication.
     */
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    @RequestFilter
    fun filterRequest(
        request: HttpRequest<*>,
        chain: FilterChain,
    ): MutableHttpResponse<*>? {
        // Skip if already authenticated via JWT
        val existingAuth = AuthenticationHelper.getAuthenticationOrNull(request)
        if (existingAuth != null) {
            log.trace("Request already authenticated via JWT, skipping remember me")
            return null // Continue with existing authentication
        }

        // Skip login and logout endpoints to avoid interference
        val path = request.path
        if (path == "/api-ui/auth/login" || path == "/api-ui/auth/logout") {
            log.trace("Skipping remember me for auth endpoint: {}", path)
            return null
        }

        // Check for remember me cookie
        val rememberMeCookie = request.cookies.get(rememberMeCookieName)
        if (rememberMeCookie == null) {
            log.trace("No remember me cookie found, continuing without authentication")
            return null
        }

        // Validate remember me token
        val token = rememberMeCookie.value
        val userAgent = request.headers.get("User-Agent")
        val userId = rememberMeService.validateToken(token, userAgent)

        if (userId == null) {
            log.debug("Invalid or expired remember me token, continuing without authentication")
            return null
        }

        // Load user from database
        val user = userRepository.findById(userId).orElse(null)
        if (user == null) {
            log.warn("Remember me token references non-existent user ID: {}", userId)
            // Invalidate the token since user no longer exists
            rememberMeService.invalidateToken(token)
            return null
        }

        // Create authentication and set it on the request
        val roles = if (user.isAdmin) listOf("admin", "user") else listOf("user")
        val authentication = Authentication.build(user.userName, roles)

        // We need to set authentication in a way that Micronaut Security will recognize
        // The proper way is to use SecurityFilter, but since we're a custom filter,
        // we need to set it via request attributes
        request.setAttribute("micronaut.security.AUTHENTICATION", authentication)

        log.info("User authenticated via remember me token: {}", user.userName)
        return null // Continue with the request, now authenticated
    }
}
