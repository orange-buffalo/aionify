package io.orangebuffalo.aionify.domain

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

@JdbcRepository(dialect = Dialect.POSTGRES)
interface UserApiAccessTokenRepository : CrudRepository<UserApiAccessToken, Long> {
    fun findAllByUserId(userId: Long): List<UserApiAccessToken>

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): Optional<UserApiAccessToken>

    fun findByToken(token: String): Optional<UserApiAccessToken>

    fun existsByUserIdAndName(
        userId: Long,
        name: String,
    ): Boolean

    fun countByUserId(userId: Long): Long
}
