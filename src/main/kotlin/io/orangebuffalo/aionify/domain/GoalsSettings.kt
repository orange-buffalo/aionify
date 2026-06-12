package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty

private const val DEFAULT_WEEKLY_WORKING_DAYS = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"

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
    val weeklyWorkingDays: String = DEFAULT_WEEKLY_WORKING_DAYS,
) {
    companion object {
        fun create(userId: Long) = GoalsSettings(userId = userId)
    }
}
