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
         * Fixed time for all tests: 2024-03-15T14:30:00Z (Friday, March 15, 2024 at 2:30 PM UTC)
         * This ensures deterministic behavior for time-sensitive tests.
         */
        val FIXED_TEST_TIME: Instant = ZonedDateTime.of(2024, 3, 15, 14, 30, 0, 0, ZoneId.of("UTC")).toInstant()
    }
    
    /**
     * Returns the fixed test time instead of the current time.
     */
    override fun now(): Instant = FIXED_TEST_TIME
}
