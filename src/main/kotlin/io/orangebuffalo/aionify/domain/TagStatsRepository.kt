package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.GenericRepository
import io.micronaut.serde.annotation.Serdeable

@JdbcRepository(dialect = Dialect.POSTGRES)
interface TagStatsRepository : GenericRepository<TimeLogEntry, Long> {
    
    /**
     * Get unique tags with their usage counts for a specific user.
     * Returns a list of tag statistics sorted alphabetically by tag name.
     */
    @Query(
        """SELECT unnest(tags) AS tag, COUNT(*) AS count 
           FROM time_log_entry 
           WHERE owner_id = :ownerId AND array_length(tags, 1) > 0
           GROUP BY tag 
           ORDER BY tag ASC"""
    )
    fun findTagStatsByOwnerId(ownerId: Long): List<TagStatRow>
}

@Serdeable
@Introspected
data class TagStatRow(
    val tag: String,
    val count: Long
)
