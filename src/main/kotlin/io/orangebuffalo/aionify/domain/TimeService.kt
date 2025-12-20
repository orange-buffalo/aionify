package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import java.time.Instant

/**
 * Service for providing current time in the application.
 * This abstraction allows for mocking time in tests, making time-sensitive tests deterministic.
 */
@Singleton
open class TimeService {
    
    /**
     * Returns the current instant in time.
     * In tests, this can be mocked to return a fixed time for deterministic behavior.
     */
    open fun now(): Instant = Instant.now()
}
