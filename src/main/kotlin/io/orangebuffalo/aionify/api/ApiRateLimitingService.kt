package io.orangebuffalo.aionify.api

import io.orangebuffalo.aionify.domain.TimeService
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory brute force protection for public API endpoints.
 * Blocks IP addresses after 10 consecutive invalid auth requests for 10 minutes.
 */
@Singleton
class ApiRateLimitingService(
    private val timeService: TimeService,
) {
    private val log = LoggerFactory.getLogger(ApiRateLimitingService::class.java)

    private val attempts = ConcurrentHashMap<String, MutableList<Instant>>()

    companion object {
        private const val MAX_ATTEMPTS = 10
        private const val BLOCK_DURATION_MINUTES = 10L
    }

    /**
     * Checks if the given IP address is blocked due to too many failed auth attempts.
     */
    fun isBlocked(ipAddress: String): Boolean {
        cleanupOldAttempts(ipAddress)
        val attemptList = attempts.getOrDefault(ipAddress, mutableListOf())
        val isBlocked = attemptList.size >= MAX_ATTEMPTS

        if (isBlocked) {
            log.debug("API rate limit: IP {} is blocked, attempts: {}", ipAddress, attemptList.size)
        }

        return isBlocked
    }

    /**
     * Records a failed authentication attempt for the given IP address.
     */
    fun recordFailedAttempt(ipAddress: String) {
        cleanupOldAttempts(ipAddress)
        attempts.compute(ipAddress) { _, list ->
            val attemptList = list ?: mutableListOf()
            attemptList.add(timeService.now())
            attemptList
        }
        log.debug("API rate limit: Recorded failed attempt for IP {}, total attempts: {}", ipAddress, attempts[ipAddress]?.size ?: 0)
    }

    /**
     * Clears all failed attempts for the given IP address.
     * Called after a successful authentication.
     */
    fun clearAttempts(ipAddress: String) {
        attempts.remove(ipAddress)
        log.trace("API rate limit: Cleared all attempts for IP {}", ipAddress)
    }

    /**
     * Clears all failed attempts for all IP addresses.
     * Useful for testing scenarios.
     */
    fun clearAllAttempts() {
        attempts.clear()
        log.trace("API rate limit: Cleared all attempts for all IPs")
    }

    private fun cleanupOldAttempts(ipAddress: String) {
        val cutoff = timeService.now().minus(BLOCK_DURATION_MINUTES, ChronoUnit.MINUTES)
        attempts.computeIfPresent(ipAddress) { _, list ->
            list.removeIf { it.isBefore(cutoff) }
            if (list.isEmpty()) null else list
        }
    }
}
