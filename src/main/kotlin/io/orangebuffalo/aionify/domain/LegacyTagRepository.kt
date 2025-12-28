package io.orangebuffalo.aionify.domain

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

@JdbcRepository(dialect = Dialect.POSTGRES)
interface LegacyTagRepository : CrudRepository<LegacyTag, Long> {
    fun findByUserIdAndName(
        userId: Long,
        name: String,
    ): Optional<LegacyTag>

    fun deleteByUserIdAndName(
        userId: Long,
        name: String,
    ): Long

    fun findByUserId(userId: Long): List<LegacyTag>
}
