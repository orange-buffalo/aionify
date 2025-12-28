package io.orangebuffalo.aionify.domain

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
}
