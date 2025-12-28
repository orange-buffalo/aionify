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
     * Returns the fixed test time instead of the current time.
     */
    override fun now(): Instant = FIXED_TEST_TIME
}
