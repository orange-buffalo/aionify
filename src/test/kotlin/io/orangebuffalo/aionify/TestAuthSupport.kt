package io.orangebuffalo.aionify

import io.orangebuffalo.aionify.auth.JwtTokenService
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Singleton

/**
 * Support class for authentication-related test utilities.
 * Provides methods for generating JWT tokens and creating test users.
 */
@Singleton
class TestAuthSupport(
    private val jwtTokenService: JwtTokenService
) {

    companion object {
        private const val ONE_HOUR_SECONDS = 3600L
    }

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

    /**
     * Generates an expired JWT token for testing token expiration scenarios.
     * The token is properly signed but has an expiration time in the past.
     */
    fun generateExpiredToken(user: User): String {
        val userId = requireNotNull(user.id) { "User must be persisted (have an ID) before generating a token" }
        
        // Set expiration to 1 hour ago using consistent time reference
        val currentTime = System.currentTimeMillis() / 1000
        val expiredTime = currentTime - ONE_HOUR_SECONDS
        
        return jwtTokenService.generateTokenWithExpiration(
            userName = user.userName,
            userId = userId,
            isAdmin = user.isAdmin,
            greeting = user.greeting,
            expirationSeconds = expiredTime
        )
    }
}
