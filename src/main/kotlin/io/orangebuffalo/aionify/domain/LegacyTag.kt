package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty

@MappedEntity("legacy_tag")
data class LegacyTag(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    
    @field:MappedProperty("user_id")
    val userId: Long,
    
    val name: String
)
