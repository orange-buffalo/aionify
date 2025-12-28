package io.orangebuffalo.aionify.auth

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.generator.TokenGenerator
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

/**
 * Service for generating JWT tokens using Micronaut Security JWT.
 *
 * Token generation is handled by Micronaut Security JWT library.
 * The signing key is configured in application.yml (JWT_SECRET environment variable or default).
 */
@Singleton
class JwtTokenService(
    private val tokenGenerator: TokenGenerator,
) : ApplicationEventListener<ApplicationStartupEvent> {
    private val log = LoggerFactory.getLogger(JwtTokenService::class.java)

    override fun onApplicationEvent(event: ApplicationStartupEvent) {
        log.info("JWT token service initialized with Micronaut Security")
    }

    /**
     * Generates a JWT token for the given user.
     */
    fun generateToken(
        userName: String,
        userId: Long,
        isAdmin: Boolean,
        greeting: String,
    ): String {
        val roles = if (isAdmin) listOf("admin", "user") else listOf("user")

        val attributes =
            mapOf(
                "userId" to userId,
                "greeting" to greeting,
                "isAdmin" to isAdmin,
            )

        val authentication = Authentication.build(userName, roles, attributes)

        val token = tokenGenerator.generateToken(authentication, null)
        return token.orElseThrow {
            RuntimeException("Failed to generate JWT token. Did you provide the secret via AIONIFY_JWT_SECRET?")
        }
    }

    /**
     * Generates a JWT token with a custom expiration time.
     * This is primarily for testing scenarios, including expired tokens.
     *
     * @param expirationSeconds Unix timestamp in seconds when the token should expire.
     *                          Can be in the past for testing expired tokens.
     */
    fun generateTokenWithExpiration(
        userName: String,
        userId: Long,
        isAdmin: Boolean,
        greeting: String,
        expirationSeconds: Long,
    ): String {
        val roles = if (isAdmin) listOf("admin", "user") else listOf("user")

        val attributes =
            mapOf(
                "userId" to userId,
                "greeting" to greeting,
                "isAdmin" to isAdmin,
            )

        val authentication = Authentication.build(userName, roles, attributes)

        // TokenGenerator expects expiration as an Integer representing seconds from now
        // Calculate relative expiration from the absolute timestamp
        // For expired tokens (past timestamps), this will be negative, which is valid
        val currentTime = System.currentTimeMillis() / 1000
        val relativeExpiration = (expirationSeconds - currentTime).toInt()

        val token = tokenGenerator.generateToken(authentication, relativeExpiration)
        return token.orElseThrow {
            RuntimeException("Failed to generate JWT token. Did you provide the secret via AIONIFY_JWT_SECRET?")
        }
    }
}
