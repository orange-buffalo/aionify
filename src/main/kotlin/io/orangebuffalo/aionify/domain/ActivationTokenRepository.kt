package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.time.Instant
import java.util.Optional

@JdbcRepository(dialect = Dialect.POSTGRES)
interface ActivationTokenRepository : CrudRepository<ActivationToken, Long> {
    
    fun findByToken(token: String): Optional<ActivationToken>
    
    fun findByUserId(userId: Long): Optional<ActivationToken>
    
    @Query("DELETE FROM activation_token WHERE expires_at < :now")
    fun deleteExpiredTokens(now: Instant): Int
    
    @Query("DELETE FROM activation_token WHERE user_id = :userId")
    fun deleteByUserId(userId: Long): Int
}
