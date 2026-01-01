package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

/**
 * Service for managing time log entries.
 * Provides common business logic used by both UI and public API endpoints.
 */
@Singleton
class TimeLogEntryService(
    private val timeLogEntryRepository: TimeLogEntryRepository,
    private val timeService: TimeService,
) {
    private val log = LoggerFactory.getLogger(TimeLogEntryService::class.java)

    /**
     * Starts a new time log entry for the user.
     * If there is an active entry, it will be stopped first.
     *
     * @param userId The ID of the user
     * @param title The title of the new entry
     * @param tags Optional tags for the entry
     * @return The created time log entry
     */
    fun startEntry(
        userId: Long,
        title: String,
        tags: Array<String> = emptyArray(),
    ): TimeLogEntry {
        // Stop any active entry first
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)
        if (activeEntry != null) {
            log.debug("Stopping active entry before starting new one for user ID: {}", userId)
            timeLogEntryRepository.update(
                activeEntry.copy(endTime = timeService.now()),
            )
        }

        // Create new entry
        val newEntry =
            timeLogEntryRepository.save(
                TimeLogEntry(
                    startTime = timeService.now(),
                    endTime = null,
                    title = title,
                    ownerId = userId,
                    tags = tags,
                ),
            )

        log.info("Time log entry started for user ID: {}, entry ID: {}", userId, newEntry.id)
        return newEntry
    }

    /**
     * Stops the active time log entry for the user.
     *
     * @param userId The ID of the user
     * @return The stopped entry if one was active, null otherwise
     */
    fun stopActiveEntry(userId: Long): TimeLogEntry? {
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)

        return if (activeEntry != null) {
            val stoppedEntry =
                timeLogEntryRepository.update(
                    activeEntry.copy(endTime = timeService.now()),
                )
            log.info("Time log entry stopped for user ID: {}, entry ID: {}", userId, activeEntry.id)
            stoppedEntry
        } else {
            log.debug("No active time log entry to stop for user ID: {}", userId)
            null
        }
    }

    /**
     * Gets the active time log entry for the user.
     *
     * @param userId The ID of the user
     * @return The active entry if one exists, null otherwise
     */
    fun getActiveEntry(userId: Long): TimeLogEntry? {
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)

        if (activeEntry != null) {
            log.trace("Found active time log entry for user ID: {}, entry ID: {}", userId, activeEntry.id)
        } else {
            log.trace("No active time log entry for user ID: {}", userId)
        }

        return activeEntry
    }
}
