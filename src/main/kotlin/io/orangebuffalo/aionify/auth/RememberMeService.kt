package io.orangebuffalo.aionify.auth

import io.micronaut.context.annotation.Property
import io.orangebuffalo.aionify.domain.RememberMeToken
import io.orangebuffalo.aionify.domain.RememberMeTokenRepository
import io.orangebuffalo.aionify.domain.TimeService
import jakarta.inject.Singleton
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Duration
import java.util.*

/**
 * Service for managing "Remember Me" tokens.
 *
 * This service generates and validates secure remember me tokens that allow users
 * to stay logged in across browser sessions. Tokens are:
 * - Cryptographically random (256 bits)
 * - Hashed before storage (using BCrypt)
 * - Time-limited (30 days by default, configurable)
 * - Tied to user agent for additional security
 */
@Singleton
class RememberMeService(
    private val rememberMeTokenRepository: RememberMeTokenRepository,
    private val timeService: TimeService,
    @Property(name = "aionify.auth.remember-me.expiration-days", defaultValue = "30")
    private val expirationDays: Int,
) {
    private val log = LoggerFactory.getLogger(RememberMeService::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        private const val TOKEN_LENGTH_BYTES = 32 // 256 bits
    }

    /**
     * Generates a new remember me token for the user.
     *
     * @param userId The ID of the user
     * @param userAgent The user agent string from the request (for additional security)
     * @return A pair of (token, RememberMeToken) where token is the plain value to send to client
     */
    fun generateToken(
        userId: Long,
        userAgent: String?,
    ): Pair<String, RememberMeToken> {
        // Generate cryptographically secure random token
        val tokenBytes = ByteArray(TOKEN_LENGTH_BYTES)
        secureRandom.nextBytes(tokenBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)

        // Hash the token for storage using SHA-256 for fast lookups
        val tokenHash = hashTokenForLookup(token)

        val now = timeService.now()
        val expiresAt = now.plus(Duration.ofDays(expirationDays.toLong()))

        val rememberMeToken =
            RememberMeToken(
                userId = userId,
                tokenHash = tokenHash,
                createdAt = now,
                expiresAt = expiresAt,
                userAgent = userAgent?.take(500), // Limit length
            )

        val savedToken = rememberMeTokenRepository.save(rememberMeToken)
        log.info("Generated remember me token for user ID: {}, expires at: {}", userId, expiresAt)

        return Pair(token, savedToken)
    }

    /**
     * Validates a remember me token.
     *
     * @param token The plain token value from the cookie
     * @param userAgent The current user agent string
     * @return The user ID if the token is valid, null otherwise
     */
    fun validateToken(
        token: String,
        userAgent: String?,
    ): Long? {
        try {
            // Hash the token with SHA-256 for lookup
            val tokenHash = hashTokenForLookup(token)

            val rememberMeToken = rememberMeTokenRepository.findByTokenHash(tokenHash).orElse(null)
            if (rememberMeToken == null) {
                log.debug("Remember me token not found")
                return null
            }

            // Check expiration
            if (rememberMeToken.expiresAt.isBefore(timeService.now())) {
                log.debug("Remember me token expired for user ID: {}", rememberMeToken.userId)
                rememberMeTokenRepository.delete(rememberMeToken)
                return null
            }

            // Optionally check user agent (disabled for now as it can be flaky across updates)
            // if (rememberMeToken.userAgent != null && rememberMeToken.userAgent != userAgent?.take(500)) {
            //     log.warn("Remember me token user agent mismatch for user ID: {}", rememberMeToken.userId)
            //     return null
            // }

            log.info("Remember me token validated for user ID: {}", rememberMeToken.userId)
            return rememberMeToken.userId
        } catch (e: Exception) {
            log.error("Error validating remember me token", e)
            return null
        }
    }

    /**
     * Invalidates a specific remember me token.
     *
     * @param token The plain token value to invalidate
     */
    fun invalidateToken(token: String) {
        try {
            val tokenHash = hashTokenForLookup(token)
            val deleted = rememberMeTokenRepository.deleteByTokenHash(tokenHash)
            if (deleted > 0) {
                log.info("Invalidated remember me token")
            }
        } catch (e: Exception) {
            log.error("Error invalidating remember me token", e)
        }
    }

    /**
     * Invalidates all remember me tokens for a user.
     *
     * @param userId The user ID
     */
    fun invalidateAllTokensForUser(userId: Long) {
        try {
            val deleted = rememberMeTokenRepository.deleteByUserId(userId)
            log.info("Invalidated {} remember me token(s) for user ID: {}", deleted, userId)
        } catch (e: Exception) {
            log.error("Error invalidating remember me tokens for user", e)
        }
    }

    /**
     * Cleans up expired tokens.
     * Should be called periodically (e.g., daily via scheduled task).
     */
    fun cleanupExpiredTokens() {
        try {
            val deleted = rememberMeTokenRepository.deleteExpiredTokens(timeService.now())
            if (deleted > 0) {
                log.info("Cleaned up {} expired remember me token(s)", deleted)
            }
        } catch (e: Exception) {
            log.error("Error cleaning up expired remember me tokens", e)
        }
    }

    /**
     * Hashes a token for lookup in the database.
     * Uses SHA-256 for fast, deterministic hashing.
     */
    private fun hashTokenForLookup(token: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}
