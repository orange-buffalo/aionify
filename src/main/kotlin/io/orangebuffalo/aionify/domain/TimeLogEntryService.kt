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
            stopEntry(activeEntry)
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
            stopEntry(activeEntry)
        } else {
            log.debug("No active time log entry to stop for user ID: {}", userId)
            null
        }
    }

    /**
     * Stops the given active entry and notifies subscribers about the change.
     * All ways of stopping an entry must go through this method, so that integrations are notified.
     *
     * @param entry The active entry to stop
     * @return The stopped entry
     */
    fun stopEntry(entry: TimeLogEntry): TimeLogEntry {
        val stoppedEntry = timeLogEntryRepository.update(entry.copy(endTime = timeService.now()))
        log.info("Time log entry stopped for user ID: {}, entry ID: {}", entry.ownerId, entry.id)

        eventService.emitEvent(entry.ownerId, TimeLogEntryEventType.ENTRY_STOPPED, stoppedEntry)

        return stoppedEntry
    }

    /**
     * Updates an entry and notifies subscribers about the change.
     * All ways of editing an entry must go through this method, so that integrations are notified.
     */
    fun updateEntry(entry: TimeLogEntry): TimeLogEntry {
        val updatedEntry = timeLogEntryRepository.update(entry)
        log.info("Time log entry updated for user ID: {}, entry ID: {}", entry.ownerId, entry.id)

        eventService.emitEvent(entry.ownerId, TimeLogEntryEventType.ENTRY_UPDATED, updatedEntry)

        return updatedEntry
    }

    /**
     * Deletes an entry and notifies subscribers about the change.
     * All ways of deleting an entry must go through this method, so that integrations are notified.
     */
    fun deleteEntry(entry: TimeLogEntry) {
        timeLogEntryRepository.delete(entry)
        log.info("Time log entry deleted for user ID: {}, entry ID: {}", entry.ownerId, entry.id)

        eventService.emitEvent(entry.ownerId, TimeLogEntryEventType.ENTRY_DELETED, entry)
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
     * Gets time log entries for a user within a specific time range with pagination.
     *
     * @param userId The ID of the user
     * @param startTimeFrom Start of time range (inclusive)
     * @param startTimeTo End of time range (exclusive)
     * @param page Page number (zero-based)
     * @param size Number of entries per page
     * @return Pair of (entries list, total count)
     */
    fun getEntriesInRangePaginated(
        userId: Long,
        startTimeFrom: Instant,
        startTimeTo: Instant,
        page: Int,
        size: Int,
    ): Pair<List<TimeLogEntry>, Long> {
        // Use Long arithmetic to avoid potential integer overflow for large page numbers
        val offset = page.toLong() * size
        val entries =
            timeLogEntryRepository.findByOwnerIdAndTimeRangeWithPagination(
                userId,
                startTimeFrom,
                startTimeTo,
                size,
                offset.toInt(),
            )
        val totalCount = timeLogEntryRepository.countByOwnerIdAndTimeRange(userId, startTimeFrom, startTimeTo)

        log.trace(
            "Found {} time log entries (page {}, size {}) for user ID: {} in range {} - {}, total: {}",
            entries.size,
            page,
            size,
            userId,
            startTimeFrom,
            startTimeTo,
            totalCount,
        )

        return Pair(entries, totalCount)
    }
}
