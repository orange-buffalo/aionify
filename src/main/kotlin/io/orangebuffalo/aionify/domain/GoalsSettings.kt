package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType

private val DEFAULT_WEEKLY_WORKING_DAYS =
    setOf(
        WeekDay.MONDAY,
        WeekDay.TUESDAY,
        WeekDay.WEDNESDAY,
        WeekDay.THURSDAY,
        WeekDay.FRIDAY,
    )

@MappedEntity("goals_settings")
data class GoalsSettings(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    @field:MappedProperty("user_id")
    val userId: Long,
    @field:MappedProperty("daily_enabled")
    val dailyEnabled: Boolean = false,
    @field:MappedProperty("daily_goal_minutes")
    val dailyGoalMinutes: Int = 0,
    @field:MappedProperty("weekly_enabled")
    val weeklyEnabled: Boolean = false,
    @field:MappedProperty("weekly_goal_minutes")
    val weeklyGoalMinutes: Int = 0,
    @field:MappedProperty("weekly_working_days")
    @field:TypeDef(type = DataType.STRING, converter = WeeklyWorkingDaysConverter::class)
    val weeklyWorkingDays: Set<WeekDay> = DEFAULT_WEEKLY_WORKING_DAYS,
) {
    companion object {
        fun create(userId: Long) = GoalsSettings(userId = userId)
    }
}
