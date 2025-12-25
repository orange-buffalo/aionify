package io.orangebuffalo.aionify

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.orangebuffalo.aionify.domain.TimeService
import jakarta.inject.Singleton
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Test implementation of TimeService that returns a fixed time for deterministic tests.
 * This replaces the real TimeService in test contexts.
 * 
 * The time can be overridden on a per-instance basis to allow testing scenarios where
 * backend time differs from frontend time.
 */
@Singleton
@Requires(env = ["test"])
@Replaces(TimeService::class)
class TestTimeService : TimeService() {
    
    companion object {
        /**
         * Fixed time for all tests defined in NZDT (New Zealand Daylight Time, UTC+13):
         * Saturday, March 16, 2024 at 03:30:00 NZDT
         * 
         * This corresponds to Friday, March 15, 2024 at 14:30:00 UTC.
         * All Playwright tests run in Pacific/Auckland timezone, so test expectations
         * should use the NZDT values (Saturday 03:30).
         */
        val FIXED_TEST_TIME: Instant = ZonedDateTime.of(2024, 3, 16, 3, 30, 0, 0, ZoneId.of("Pacific/Auckland")).toInstant()
        
        /**
         * Backend time offset from frontend time.
         * By default, backend time is 1 minute ahead of frontend time to help verify that
         * timestamps come from the backend, not the frontend.
         */
        val BACKEND_TIME_OFFSET_SECONDS: Long = 60L
    }
    
    /**
     * The current time offset from FIXED_TEST_TIME.
     * This is volatile to support concurrent access patterns.
     */
    @Volatile
    private var timeOffsetSeconds: Long = BACKEND_TIME_OFFSET_SECONDS
    
    /**
     * Returns the fixed test time plus any configured offset.
     * By default, returns FIXED_TEST_TIME + 1 minute to differentiate from frontend time.
     */
    override fun now(): Instant = FIXED_TEST_TIME.plusSeconds(timeOffsetSeconds)
    
    /**
     * Sets the time offset from FIXED_TEST_TIME for this instance.
     * This allows tests to control backend time independently from frontend time.
     */
    fun setTimeOffset(seconds: Long) {
        timeOffsetSeconds = seconds
    }
    
    /**
     * Resets the time offset to the default backend offset.
     */
    fun resetTimeOffset() {
        timeOffsetSeconds = BACKEND_TIME_OFFSET_SECONDS
    }
}
