package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.time.Instant
import java.util.Optional

@JdbcRepository(dialect = Dialect.POSTGRES)
interface TimeLogEntryRepository : CrudRepository<TimeLogEntry, Long> {
    fun findAllOrderById(): List<TimeLogEntry>

    /**
     * Find all log entries for a specific owner within a time range, ordered by start time descending.
     */
    fun findByOwnerIdAndStartTimeGreaterThanEqualsAndStartTimeLessThanOrderByStartTimeDesc(
        ownerId: Long,
        startTimeFrom: Instant,
        startTimeTo: Instant,
    ): List<TimeLogEntry>

    /**
     * Find all log entries for a specific owner within a time range with pagination, ordered by start time descending.
     */
    @Query(
        """SELECT * FROM time_log_entry
           WHERE owner_id = :ownerId
           AND start_time >= :startTimeFrom
           AND start_time < :startTimeTo
           ORDER BY start_time DESC
           LIMIT :limit OFFSET :offset""",
    )
    fun findByOwnerIdAndTimeRangeWithPagination(
        ownerId: Long,
        startTimeFrom: Instant,
        startTimeTo: Instant,
        limit: Int,
        offset: Int,
    ): List<TimeLogEntry>

    /**
     * Count all log entries for a specific owner within a time range.
     */
    @Query(
        """SELECT COUNT(*) FROM time_log_entry
           WHERE owner_id = :ownerId
           AND start_time >= :startTimeFrom
           AND start_time < :startTimeTo""",
    )
    fun countByOwnerIdAndTimeRange(
        ownerId: Long,
        startTimeFrom: Instant,
        startTimeTo: Instant,
    ): Long

    /**
     * Find the active log entry (with null endTime) for a specific owner.
     */
    fun findByOwnerIdAndEndTimeIsNull(ownerId: Long): Optional<TimeLogEntry>

    /**
     * Find log entry by id and owner (for security checks).
     */
    fun findByIdAndOwnerId(
        id: Long,
        ownerId: Long,
    ): Optional<TimeLogEntry>

    /**
     * Find log entry by owner, title, and start time for duplicate detection.
     */
    fun findByOwnerIdAndTitleAndStartTime(
        ownerId: Long,
        title: String,
        startTime: Instant,
    ): Optional<TimeLogEntry>

    /**
     * Search for entries by title tokens (case-insensitive).
     * Returns distinct entries by title, taking the latest (by start time) for each duplicate.
     * Results are ordered by start time descending (latest first).
     * Searches for entries where the title contains all the provided tokens.
     */
    @Query(
        """SELECT DISTINCT ON (title) *
           FROM time_log_entry
           WHERE owner_id = :ownerId
           AND (:searchTokens = '' OR (
               SELECT bool_and(LOWER(title) LIKE LOWER('%' || token || '%'))
               FROM unnest(string_to_array(:searchTokens, ' ')) AS token
               WHERE token != ''
           ))
           ORDER BY title, start_time DESC""",
    )
    fun searchByTitleTokens(
        ownerId: Long,
        searchTokens: String,
    ): List<TimeLogEntry>
}
