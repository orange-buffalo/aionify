package io.orangebuffalo.aionify.domain

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.POSTGRES)
interface DailyGoalBreakRepository : CrudRepository<DailyGoalBreak, Long> {
    fun findByGoalsSettingsIdOrderBySortOrderAsc(goalsSettingsId: Long): List<DailyGoalBreak>

    fun deleteByGoalsSettingsId(goalsSettingsId: Long)
}
