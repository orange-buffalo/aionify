package io.orangebuffalo.aionify

import io.orangebuffalo.aionify.auth.JwtTokenService
import io.orangebuffalo.aionify.domain.User
import jakarta.enterprise.context.ApplicationScoped

/**
 * Support class for authentication-related test utilities.
 * Provides methods for generating JWT tokens and creating test users.
 */
@ApplicationScoped
class TestAuthSupport(
    private val jwtTokenService: JwtTokenService
) {

    /**
     * Generates a valid JWT token for the given user.
     * This token can be used to authenticate without going through the login page.
     * 
     * @throws IllegalArgumentException if the user has no ID (i.e., was not persisted to the database)
     */
    fun generateToken(user: User): String {
        val userId = requireNotNull(user.id) { "User must be persisted (have an ID) before generating a token" }
        return jwtTokenService.generateToken(
            userName = user.userName,
            userId = userId,
            isAdmin = user.isAdmin,
            greeting = user.greeting
        )
    }

    /**
     * Data class representing authentication information that should be stored in browser storage.
     */
    data class AuthStorageData(
        val token: String,
        val userName: String,
        val greeting: String
    )

    /**
     * Generates the complete authentication data needed for browser storage.
     */
    fun generateAuthStorageData(user: User): AuthStorageData {
        return AuthStorageData(
            token = generateToken(user),
            userName = user.userName,
            greeting = user.greeting
        )
    }
}
