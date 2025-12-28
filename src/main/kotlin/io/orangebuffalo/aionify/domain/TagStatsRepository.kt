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
     * Includes information about whether each tag is marked as legacy.
     * Returns a list of tag statistics sorted alphabetically by tag name.
     */
    @Query(
        """SELECT t.tag, COUNT(*) AS count, (lt.id IS NOT NULL) AS is_legacy
           FROM (SELECT unnest(tags) AS tag FROM time_log_entry WHERE owner_id = :ownerId AND array_length(tags, 1) > 0) t
           LEFT JOIN legacy_tag lt ON lt.user_id = :ownerId AND lt.name = t.tag
           GROUP BY t.tag, lt.id
           ORDER BY t.tag ASC""",
    )
    fun findTagStatsByOwnerId(ownerId: Long): List<TagStatRow>
}

@Serdeable
@Introspected
data class TagStatRow(
    val tag: String,
    val count: Long,
    val isLegacy: Boolean,
)
