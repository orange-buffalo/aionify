package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty

@MappedEntity("user_settings")
data class UserSettings(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    @field:MappedProperty("user_id")
    val userId: Long,
    @field:MappedProperty("start_of_week")
    val startOfWeek: WeekDay,
) {
    companion object {
        fun create(
            userId: Long,
            startOfWeek: WeekDay = WeekDay.MONDAY,
        ) = UserSettings(
            userId = userId,
            startOfWeek = startOfWeek,
        )
    }
}

enum class WeekDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}
