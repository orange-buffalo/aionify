package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing short-lived opaque tokens for SSE authentication.
 * Tokens are stored in memory with a 30-second TTL to reduce security risks
 * compared to passing JWT tokens in URL query parameters.
 */
@Singleton
class SseTokenService(
    private val timeService: TimeService,
) {
    private val log = LoggerFactory.getLogger(SseTokenService::class.java)
    private val secureRandom = SecureRandom()

    // Map of token -> (userId, expirationTime)
    private val tokens = ConcurrentHashMap<String, SseTokenData>()

    companion object {
        private const val TOKEN_TTL_SECONDS = 30L
        private const val TOKEN_LENGTH_BYTES = 32
    }

    /**
     * Generates a new short-lived token for the given user.
     * The token expires after 30 seconds.
     */
    fun generateToken(userId: Long): String {
        // Clean up expired tokens periodically
        cleanupExpiredTokens()

        // Generate random token
        val tokenBytes = ByteArray(TOKEN_LENGTH_BYTES)
        secureRandom.nextBytes(tokenBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)

        val expiresAt = timeService.now().plusSeconds(TOKEN_TTL_SECONDS)
        tokens[token] = SseTokenData(userId, expiresAt)

        log.debug("Generated SSE token for user {} (expires at {})", userId, expiresAt)
        return token
    }

    /**
     * Validates a token and returns the associated user ID.
     * Returns null if the token is invalid or expired.
     */
    fun validateToken(token: String): Long? {
        val tokenData = tokens[token] ?: return null

        if (timeService.now().isAfter(tokenData.expiresAt)) {
            log.debug("SSE token has expired")
            tokens.remove(token)
            return null
        }

        log.debug("SSE token validated for user {}", tokenData.userId)
        return tokenData.userId
    }

    /**
     * Removes expired tokens from memory.
     *
     * Note: This simple cleanup approach is acceptable for the current use case where
     * SSE connections are relatively infrequent (user actions). For high-traffic scenarios,
     * consider implementing a scheduled cleanup task or using a time-based eviction cache
     * like Caffeine.
     */
    private fun cleanupExpiredTokens() {
        val now = timeService.now()
        val expiredTokens = tokens.filterValues { it.expiresAt.isBefore(now) }.keys

        if (expiredTokens.isNotEmpty()) {
            expiredTokens.forEach { tokens.remove(it) }
            log.debug("Cleaned up {} expired SSE tokens", expiredTokens.size)
        }
    }

    /**
     * Data associated with an SSE token.
     */
    private data class SseTokenData(
        val userId: Long,
        val expiresAt: Instant,
    )
}
