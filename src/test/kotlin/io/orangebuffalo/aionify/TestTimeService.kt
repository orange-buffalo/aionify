package io.orangebuffalo.aionify

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.orangebuffalo.aionify.domain.TimeService
import jakarta.inject.Singleton
import java.time.Instant

/**
 * Test implementation of TimeService that returns a configurable time for deterministic tests.
 * This replaces the real TimeService in test contexts.
 *
 * Tests can set specific times using setTime() to control what "now" means for the test.
 */
@Singleton
@Requires(env = ["test"])
@Replaces(TimeService::class)
class TestTimeService : TimeService() {
    /**
     * Current time for the test. Defaults to a fixed instant but should be explicitly
     * set by each test that depends on time.
     */
    private var currentTime: Instant = Instant.parse("2024-01-01T00:00:00Z")

    /**
     * Returns the current test time.
     */
    override fun now(): Instant = currentTime

    /**
     * Sets the current test time to a specific instant.
     * This is useful for testing time-dependent behavior.
     *
     * @param instant The instant to set as the current time
     */
    fun setTime(instant: Instant) {
        currentTime = instant
    }
}
