package io.orangebuffalo.aionify.domain

import jakarta.enterprise.context.ApplicationScoped
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.mapTo
import java.util.Locale

@ApplicationScoped
class UserRepository(private val jdbi: Jdbi) {

    fun insert(user: User): User {
        return jdbi.withHandle<User, Exception> { handle ->
            val id = handle.createUpdate(
                """
                INSERT INTO app_user (user_name, password_hash, greeting, is_admin, locale, language_code) 
                VALUES (:userName, :passwordHash, :greeting, :isAdmin, :locale, :languageCode)
                """.trimIndent()
            )
                .bind("userName", user.userName)
                .bind("passwordHash", user.passwordHash)
                .bind("greeting", user.greeting)
                .bind("isAdmin", user.isAdmin)
                .bind("locale", user.locale.toLanguageTag())
                .bind("languageCode", user.languageCode)
                .executeAndReturnGeneratedKeys("id")
                .mapTo<Long>()
                .one()
            user.copy(id = id)
        }
    }

    fun existsAdmin(): Boolean {
        return jdbi.withHandle<Boolean, Exception> { handle ->
            handle.createQuery("SELECT EXISTS(SELECT 1 FROM app_user WHERE is_admin = true)")
                .mapTo<Boolean>()
                .one()
        }
    }

    fun findByUserName(userName: String): User? {
        return jdbi.withHandle<User?, Exception> { handle ->
            handle.createQuery(
                """
                SELECT id, user_name, password_hash, greeting, is_admin, locale, language_code 
                FROM app_user 
                WHERE user_name = :userName
                """.trimIndent()
            )
                .bind("userName", userName)
                .map { rs, _ ->
                    User(
                        id = rs.getLong("id"),
                        userName = rs.getString("user_name"),
                        passwordHash = rs.getString("password_hash"),
                        greeting = rs.getString("greeting"),
                        isAdmin = rs.getBoolean("is_admin"),
                        locale = Locale.forLanguageTag(rs.getString("locale")),
                        languageCode = rs.getString("language_code")
                    )
                }
                .findOne()
                .orElse(null)
        }
    }

    fun updatePasswordHash(userName: String, newPasswordHash: String) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate(
                """
                UPDATE app_user 
                SET password_hash = :passwordHash 
                WHERE user_name = :userName
                """.trimIndent()
            )
                .bind("userName", userName)
                .bind("passwordHash", newPasswordHash)
                .execute()
        }
    }

    fun updateProfile(userName: String, greeting: String, languageCode: String, locale: Locale) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate(
                """
                UPDATE app_user 
                SET greeting = :greeting, language_code = :languageCode, locale = :locale 
                WHERE user_name = :userName
                """.trimIndent()
            )
                .bind("userName", userName)
                .bind("greeting", greeting)
                .bind("languageCode", languageCode)
                .bind("locale", locale.toLanguageTag())
                .execute()
        }
    }

    fun findAllPaginated(page: Int, size: Int): PagedUsers {
        return jdbi.withHandle<PagedUsers, Exception> { handle ->
            val offset = page * size
            
            val total = handle.createQuery("SELECT COUNT(*) FROM app_user")
                .mapTo<Long>()
                .one()
            
            val users = handle.createQuery(
                """
                SELECT id, user_name, password_hash, greeting, is_admin, locale, language_code 
                FROM app_user 
                ORDER BY user_name
                LIMIT :size OFFSET :offset
                """.trimIndent()
            )
                .bind("size", size)
                .bind("offset", offset)
                .map { rs, _ ->
                    User(
                        id = rs.getLong("id"),
                        userName = rs.getString("user_name"),
                        passwordHash = rs.getString("password_hash"),
                        greeting = rs.getString("greeting"),
                        isAdmin = rs.getBoolean("is_admin"),
                        locale = Locale.forLanguageTag(rs.getString("locale")),
                        languageCode = rs.getString("language_code")
                    )
                }
                .list()
            
            PagedUsers(
                users = users,
                total = total,
                page = page,
                size = size
            )
        }
    }

    fun deleteById(id: Long): Boolean {
        return jdbi.withHandle<Boolean, Exception> { handle ->
            val rowsDeleted = handle.createUpdate("DELETE FROM app_user WHERE id = :id")
                .bind("id", id)
                .execute()
            rowsDeleted > 0
        }
    }

    fun findById(id: Long): User? {
        return jdbi.withHandle<User?, Exception> { handle ->
            handle.createQuery(
                """
                SELECT id, user_name, password_hash, greeting, is_admin, locale, language_code 
                FROM app_user 
                WHERE id = :id
                """.trimIndent()
            )
                .bind("id", id)
                .map { rs, _ ->
                    User(
                        id = rs.getLong("id"),
                        userName = rs.getString("user_name"),
                        passwordHash = rs.getString("password_hash"),
                        greeting = rs.getString("greeting"),
                        isAdmin = rs.getBoolean("is_admin"),
                        locale = Locale.forLanguageTag(rs.getString("locale")),
                        languageCode = rs.getString("language_code")
                    )
                }
                .findOne()
                .orElse(null)
        }
    }
}

data class PagedUsers(
    val users: List<User>,
    val total: Long,
    val page: Int,
    val size: Int
)
