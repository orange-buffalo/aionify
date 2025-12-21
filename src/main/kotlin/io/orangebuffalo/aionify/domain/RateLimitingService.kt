package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory rate limiting service to prevent brute force attacks.
 */
@Singleton
class RateLimitingService(
    private val timeService: TimeService
) {
    
    private val attempts = ConcurrentHashMap<String, MutableList<Instant>>()
    
    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val WINDOW_MINUTES = 15L
    }
    
    /**
     * Checks if the given key (e.g., IP address or token) is rate limited.
     * Returns true if the key has exceeded the maximum number of attempts.
     */
    fun isRateLimited(key: String): Boolean {
        cleanupOldAttempts(key)
        val attemptList = attempts.getOrDefault(key, mutableListOf())
        return attemptList.size >= MAX_ATTEMPTS
    }
    
    /**
     * Records an attempt for the given key.
     */
    fun recordAttempt(key: String) {
        cleanupOldAttempts(key)
        attempts.compute(key) { _, list ->
            val attemptList = list ?: mutableListOf()
            attemptList.add(timeService.now())
            attemptList
        }
    }
    
    /**
     * Clears all attempts for the given key.
     */
    fun clearAttempts(key: String) {
        attempts.remove(key)
    }
    
    private fun cleanupOldAttempts(key: String) {
        val cutoff = timeService.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES)
        attempts.computeIfPresent(key) { _, list ->
            list.removeIf { it.isBefore(cutoff) }
            if (list.isEmpty()) null else list
        }
    }
}
