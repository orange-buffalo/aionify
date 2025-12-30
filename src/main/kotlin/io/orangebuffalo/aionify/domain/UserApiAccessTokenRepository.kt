package io.orangebuffalo.aionify.domain

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

@JdbcRepository(dialect = Dialect.POSTGRES)
interface UserApiAccessTokenRepository : CrudRepository<UserApiAccessToken, Long> {
    fun findByUserId(userId: Long): Optional<UserApiAccessToken>

    fun deleteByUserId(userId: Long)
}
