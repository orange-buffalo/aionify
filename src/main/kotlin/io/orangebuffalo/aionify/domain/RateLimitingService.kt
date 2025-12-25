package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
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
    
    private val log = LoggerFactory.getLogger(RateLimitingService::class.java)
    
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
        val isLimited = attemptList.size >= MAX_ATTEMPTS
        
        if (isLimited) {
            log.debug("Rate limit exceeded for key: {}, attempts: {}", key, attemptList.size)
        } else {
            log.trace("Rate limit check for key: {}, attempts: {}/{}", key, attemptList.size, MAX_ATTEMPTS)
        }
        
        return isLimited
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
        log.trace("Recorded attempt for key: {}, total attempts: {}", key, attempts[key]?.size ?: 0)
    }
    
    /**
     * Clears all attempts for the given key.
     */
    fun clearAttempts(key: String) {
        attempts.remove(key)
        log.trace("Cleared all attempts for key: {}", key)
    }
    
    private fun cleanupOldAttempts(key: String) {
        val cutoff = timeService.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES)
        attempts.computeIfPresent(key) { _, list ->
            val sizeBefore = list.size
            list.removeIf { it.isBefore(cutoff) }
            if (list.size < sizeBefore) {
                log.trace("Cleaned up {} old attempts for key: {}", sizeBefore - list.size, key)
            }
            if (list.isEmpty()) null else list
        }
    }
}
