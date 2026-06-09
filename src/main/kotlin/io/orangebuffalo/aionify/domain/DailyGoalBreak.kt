package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.time.LocalTime

@MappedEntity("daily_goal_break")
data class DailyGoalBreak(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    @field:MappedProperty("goals_settings_id")
    val goalsSettingsId: Long,
    @field:MappedProperty("sort_order")
    val sortOrder: Int,
    @field:MappedProperty("from_time")
    val fromTime: LocalTime,
    @field:MappedProperty("to_time")
    val toTime: LocalTime,
)
