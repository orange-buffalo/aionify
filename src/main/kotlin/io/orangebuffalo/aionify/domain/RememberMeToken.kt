package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import java.time.Instant

@MappedEntity("remember_me_token")
data class RememberMeToken(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long? = null,
    @field:MappedProperty("user_id")
    val userId: Long,
    @field:MappedProperty("token_hash")
    val tokenHash: String,
    @field:MappedProperty("created_at")
    val createdAt: Instant,
    @field:MappedProperty("expires_at")
    val expiresAt: Instant,
    @field:MappedProperty("user_agent")
    val userAgent: String?,
)
