package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty

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
) {
    companion object {
        fun create(userId: Long) = GoalsSettings(userId = userId)
    }
}
