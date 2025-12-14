package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.time.Instant

@MappedEntity("activation_token")
data class ActivationToken(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    
    @field:MappedProperty("user_id")
    val userId: Long,
    
    val token: String,
    
    @field:MappedProperty("expires_at")
    val expiresAt: Instant,
    
    @field:MappedProperty("created_at")
    val createdAt: Instant = Instant.now()
)
