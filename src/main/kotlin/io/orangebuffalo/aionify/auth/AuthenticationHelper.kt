package io.orangebuffalo.aionify.auth

import io.micronaut.security.authentication.Authentication
import io.orangebuffalo.aionify.domain.User

/**
 * Helper object for creating Micronaut Security Authentication objects from User entities.
 * Centralizes the logic for determining roles and attributes.
 */
object AuthenticationHelper {
    /**
     * Creates a Micronaut Security Authentication object from a User.
     * This is used by filters and services that need to establish authentication context.
     */
    fun createAuthentication(user: User): Authentication {
        val roles = if (user.isAdmin) listOf("admin", "user") else listOf("user")
        return Authentication.build(user.userName, roles)
    }

    /**
     * Creates a Micronaut Security Authentication object from a User with additional attributes.
     * This is used when generating JWT tokens that need to include extra user information.
     */
    fun createAuthenticationWithAttributes(user: User): Authentication {
        val roles = if (user.isAdmin) listOf("admin", "user") else listOf("user")
        val attributes =
            mapOf(
                "userId" to requireNotNull(user.id) { "User ID must not be null" },
                "greeting" to user.greeting,
                "isAdmin" to user.isAdmin,
            )
        return Authentication.build(user.userName, roles, attributes)
    }
}
