package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType
import java.time.Instant

@MappedEntity("time_log_entry")
data class TimeLogEntry(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    
    @field:MappedProperty("start_time")
    val startTime: Instant,
    
    @field:MappedProperty("end_time")
    val endTime: Instant? = null,
    
    val title: String,
    
    @field:MappedProperty("owner_id")
    val ownerId: Long,
    
    @field:TypeDef(type = DataType.STRING_ARRAY, converter = TagsConverter::class)
    val tags: List<String> = emptyList()
)
