package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.time.Instant

@MappedEntity("time_entry")
data class TimeEntry(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    
    @field:MappedProperty("start_time")
    val startTime: Instant,
    
    @field:MappedProperty("end_time")
    val endTime: Instant? = null,
    
    val title: String,
    
    @field:MappedProperty("owner_id")
    val ownerId: Long
)
