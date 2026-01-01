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
     * @param emitEvents Whether to emit SSE events immediately (default true).
     *                   Set to false when caller needs to emit events after transaction commits
     *                   to ensure data visibility to SSE subscribers. This prevents race conditions
     *                   where subscribers receive events and query for data before it's committed.
     * @return A result containing the created entry and optional stopped entry
     */
    fun startEntry(
        userId: Long,
        title: String,
        tags: Array<String> = emptyArray(),
        metadata: Array<String> = emptyArray(),
        emitEvents: Boolean = true,
    ): TimeLogEntryStartResult {
        // Stop any active entry first
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)
        val stoppedEntry =
            if (activeEntry != null) {
                log.debug("Stopping active entry before starting new one for user ID: {}", userId)
                val stopped =
                    timeLogEntryRepository.update(
                        activeEntry.copy(endTime = timeService.now()),
                    )
                // Emit event for stopped entry if requested
                if (emitEvents) {
                    eventService.emitEvent(userId, TimeLogEntryEventType.ENTRY_STOPPED, stopped)
                }
                stopped
            } else {
                null
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

        // Emit event for started entry if requested
        if (emitEvents) {
            eventService.emitEvent(userId, TimeLogEntryEventType.ENTRY_STARTED, newEntry)
        }

        return TimeLogEntryStartResult(newEntry, stoppedEntry)
    }

    /**
     * Stops the active time log entry for the user.
     *
     * @param userId The ID of the user
     * @param emitEvents Whether to emit SSE events immediately (default true).
     *                   Set to false when caller needs to emit events after transaction commits
     *                   to ensure data visibility to SSE subscribers. This prevents race conditions
     *                   where subscribers receive events and query for data before it's committed.
     * @return The stopped entry if one was active, null otherwise
     */
    fun stopActiveEntry(
        userId: Long,
        emitEvents: Boolean = true,
    ): TimeLogEntry? {
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)

        return if (activeEntry != null) {
            val stoppedEntry =
                timeLogEntryRepository.update(
                    activeEntry.copy(endTime = timeService.now()),
                )
            log.info("Time log entry stopped for user ID: {}, entry ID: {}", userId, activeEntry.id)

            // Emit event for stopped entry if requested
            if (emitEvents) {
                eventService.emitEvent(userId, TimeLogEntryEventType.ENTRY_STOPPED, stoppedEntry)
            }

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

/**
 * Result of starting a time log entry.
 * Contains the new entry and the entry that was stopped (if any).
 */
data class TimeLogEntryStartResult(
    val newEntry: TimeLogEntry,
    val stoppedEntry: TimeLogEntry?,
)
