package org.aionify.domain

import jakarta.enterprise.context.ApplicationScoped
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.mapTo
import java.time.Instant

@ApplicationScoped
class TimeEntryRepository(private val jdbi: Jdbi) {

    fun insert(startTime: Instant): TimeEntry {
        return jdbi.withHandle<TimeEntry, Exception> { handle ->
            val id = handle.createUpdate("INSERT INTO time_entry (start_time) VALUES (:startTime)")
                .bind("startTime", startTime)
                .executeAndReturnGeneratedKeys("id")
                .mapTo<Long>()
                .one()
            TimeEntry(id = id, startTime = startTime)
        }
    }

    fun findAll(): List<TimeEntry> {
        return jdbi.withHandle<List<TimeEntry>, Exception> { handle ->
            handle.createQuery("SELECT id, start_time FROM time_entry ORDER BY id")
                .map { rs, _ ->
                    TimeEntry(
                        id = rs.getLong("id"),
                        startTime = rs.getObject("start_time", java.time.OffsetDateTime::class.java).toInstant()
                    )
                }
                .list()
        }
    }
}
