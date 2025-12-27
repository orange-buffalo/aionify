package io.orangebuffalo.aionify.domain

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

@JdbcRepository(dialect = Dialect.POSTGRES)
interface UserRepository : CrudRepository<User, Long> {
    fun findByUserName(userName: String): Optional<User>

    @Query("SELECT EXISTS(SELECT 1 FROM app_user WHERE is_admin = true)")
    fun existsAdmin(): Boolean

    @Query(
        """UPDATE app_user 
           SET password_hash = :passwordHash 
           WHERE user_name = :userName""",
    )
    fun updatePasswordHash(
        userName: String,
        passwordHash: String,
    ): Int

    @Query(
        """UPDATE app_user 
           SET greeting = :greeting, locale = :localeTag 
           WHERE user_name = :userName""",
    )
    fun updateProfile(
        userName: String,
        greeting: String,
        localeTag: String,
    ): Int

    @Query(
        """SELECT id, user_name, password_hash, greeting, is_admin, locale 
           FROM app_user 
           ORDER BY user_name
           LIMIT :size OFFSET :offset""",
    )
    fun findAllPaginated(
        offset: Int,
        size: Int,
    ): List<User>

    @Query("SELECT COUNT(*) FROM app_user")
    fun countAll(): Long
}

data class PagedUsers(
    val users: List<User>,
    val total: Long,
    val page: Int,
    val size: Int,
)
