package io.orangebuffalo.aionify

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.orangebuffalo.aionify.domain.TimeService
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Test implementation of TimeService that returns a fixed time for deterministic tests.
 * This replaces the real TimeService in test contexts.
 *
 * Supports advancing time for testing time-sensitive scenarios like token expiration.
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
    }

    /**
     * Current time offset from FIXED_TEST_TIME.
     * This allows tests to advance time without affecting other tests.
     */
    private var timeOffset: Duration = Duration.ZERO

    /**
     * Returns the fixed test time plus any offset.
     */
    override fun now(): Instant = FIXED_TEST_TIME.plus(timeOffset)

    /**
     * Advances the test time by the given duration.
     * This is useful for testing time-sensitive scenarios like token expiration.
     */
    fun advanceTime(duration: Duration) {
        timeOffset = timeOffset.plus(duration)
    }

    /**
     * Resets the time offset to zero.
     * This is useful for ensuring tests start from a clean state.
     */
    fun resetTime() {
        timeOffset = Duration.ZERO
    }
}
