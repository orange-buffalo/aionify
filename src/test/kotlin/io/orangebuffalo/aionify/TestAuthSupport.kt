package io.orangebuffalo.aionify

import io.orangebuffalo.aionify.domain.User
import io.smallrye.jwt.build.Jwt
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

/**
 * Support class for authentication-related test utilities.
 * Provides methods for generating JWT tokens and creating test users.
 */
@ApplicationScoped
class TestAuthSupport(
    @param:ConfigProperty(name = "aionify.jwt.issuer", defaultValue = "aionify")
    private val jwtIssuer: String,
    @param:ConfigProperty(name = "aionify.jwt.expiration-minutes", defaultValue = "1440")
    private val jwtExpirationMinutes: Long
) {

    /**
     * Generates a valid JWT token for the given user.
     * This token can be used to authenticate without going through the login page.
     */
    fun generateToken(user: User): String {
        return Jwt.issuer(jwtIssuer)
            .subject(user.userName)
            .claim("userId", user.id)
            .claim("isAdmin", user.isAdmin)
            .claim("greeting", user.greeting)
            .expiresIn(Duration.ofMinutes(jwtExpirationMinutes))
            .sign()
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
