package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Service for managing time log entries.
 * Provides common business logic used by both UI and public API endpoints.
 */
@Singleton
class TimeLogEntryService(
    private val timeLogEntryRepository: TimeLogEntryRepository,
    private val timeService: TimeService,
    private val eventService: TimeLogEntryEventService,
) {
    private val log = LoggerFactory.getLogger(TimeLogEntryService::class.java)

    /**
     * Starts a new time log entry for the user.
     * If there is an active entry, it will be stopped first.
     *
     * @param userId The ID of the user
     * @param title The title of the new entry
     * @param tags Optional tags for the entry
     * @param metadata Optional metadata for the entry
     * @return The created time log entry
     */
    fun startEntry(
        userId: Long,
        title: String,
        tags: Array<String> = emptyArray(),
        metadata: Array<String> = emptyArray(),
    ): TimeLogEntry {
        // Stop any active entry first
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)
        if (activeEntry != null) {
            log.debug("Stopping active entry before starting new one for user ID: {}", userId)
            val stoppedEntry =
                timeLogEntryRepository.update(
                    activeEntry.copy(endTime = timeService.now()),
                )
            // Emit event for stopped entry
            eventService.emitEvent(userId, TimeLogEntryEventType.ENTRY_STOPPED, stoppedEntry)
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
                    metadata = metadata,
                ),
            )

        log.info("Time log entry started for user ID: {}, entry ID: {}", userId, newEntry.id)

        // Emit event for started entry
        eventService.emitEvent(userId, TimeLogEntryEventType.ENTRY_STARTED, newEntry)

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

            // Emit event for stopped entry
            eventService.emitEvent(userId, TimeLogEntryEventType.ENTRY_STOPPED, stoppedEntry)

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

    /**
     * Gets time log entries for a user within a specific time range.
     *
     * @param userId The ID of the user
     * @param startTimeFrom Start of time range (inclusive)
     * @param startTimeTo End of time range (exclusive)
     * @return List of time log entries ordered by start time descending
     */
    fun getEntriesInRange(
        userId: Long,
        startTimeFrom: Instant,
        startTimeTo: Instant,
    ): List<TimeLogEntry> {
        val entries =
            timeLogEntryRepository.findByOwnerIdAndStartTimeGreaterThanEqualsAndStartTimeLessThanOrderByStartTimeDesc(
                userId,
                startTimeFrom,
                startTimeTo,
            )

        log.trace("Found {} time log entries for user ID: {} in range {} - {}", entries.size, userId, startTimeFrom, startTimeTo)

        return entries
    }
}
