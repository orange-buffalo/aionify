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
}
