package io.orangebuffalo.aionify.auth

import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.Authentication
import io.orangebuffalo.aionify.domain.User

/**
 * Helper object for creating and managing Micronaut Security Authentication objects.
 * Centralizes the logic for determining roles, attributes, and request attribute handling.
 */
object AuthenticationHelper {
    /**
     * The key used to store authentication in request attributes.
     */
    private const val AUTHENTICATION_ATTRIBUTE = "micronaut.security.AUTHENTICATION"

    /**
     * Creates a Micronaut Security Authentication object from a User.
     * This is used by filters and services that need to establish authentication context.
     */
    fun createAuthentication(user: User): Authentication {
        val roles = if (user.isAdmin) listOf("admin", "user") else listOf("user")
        return Authentication.build(user.userName, roles)
    }

    /**
     * Gets the authentication from the request attributes, if present.
     *
     * @param request The HTTP request
     * @return The Authentication object or null if not present
     */
    fun getAuthenticationOrNull(request: HttpRequest<*>): Authentication? =
        request.getAttribute(AUTHENTICATION_ATTRIBUTE, Authentication::class.java).orElse(null)

    /**
     * Sets authentication on a request and returns the modified request.
     * This creates a new mutable request with authentication set.
     *
     * @param request The HTTP request
     * @param user The user to authenticate
     * @return The modified request with authentication set
     */
    fun setAuthentication(
        request: HttpRequest<*>,
        user: User,
    ): HttpRequest<*> {
        val authentication = createAuthentication(user)
        return request.setAttribute(AUTHENTICATION_ATTRIBUTE, authentication)
    }
}
