package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.time.Instant
import java.util.*

@JdbcRepository(dialect = Dialect.POSTGRES)
interface RememberMeTokenRepository : CrudRepository<RememberMeToken, Long> {
    fun findByTokenHash(tokenHash: String): Optional<RememberMeToken>

    @Query("DELETE FROM remember_me_token WHERE expires_at < :now")
    fun deleteExpiredTokens(now: Instant): Long

    @Query("DELETE FROM remember_me_token WHERE user_id = :userId")
    fun deleteByUserId(userId: Long): Long

    fun deleteByTokenHash(tokenHash: String): Long
}
