package io.orangebuffalo.aionify

import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Singleton
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

/**
 * Singleton that provides commonly used test users and credentials.
 * Simplifies test setup by centralizing user creation patterns.
 */
@Singleton
class TestUsers {
    
    companion object {
        const val TEST_PASSWORD = "testPassword123"
        const val ADMIN_USERNAME = "admin"
        const val ADMIN_GREETING = "Admin User"
        const val REGULAR_USERNAME = "testuser"
        const val REGULAR_GREETING = "Test User"
    }
    
    /**
     * Creates and saves an admin user with standard test credentials.
     */
    fun createAdmin(userRepository: UserRepository, username: String = ADMIN_USERNAME, greeting: String = ADMIN_GREETING): User {
        return userRepository.save(
            User.create(
                userName = username,
                passwordHash = BCrypt.hashpw(TEST_PASSWORD, BCrypt.gensalt()),
                greeting = greeting,
                isAdmin = true,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )
    }
    
    /**
     * Creates and saves a regular (non-admin) user with standard test credentials.
     */
    fun createRegularUser(userRepository: UserRepository, username: String = REGULAR_USERNAME, greeting: String = REGULAR_GREETING): User {
        return userRepository.save(
            User.create(
                userName = username,
                passwordHash = BCrypt.hashpw(TEST_PASSWORD, BCrypt.gensalt()),
                greeting = greeting,
                isAdmin = false,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )
    }
    
    /**
     * Creates and saves a user with custom locale.
     */
    fun createUserWithLocale(
        userRepository: UserRepository,
        username: String,
        greeting: String,
        isAdmin: Boolean = false,
        locale: Locale,
        languageCode: String
    ): User {
        return userRepository.save(
            User.create(
                userName = username,
                passwordHash = BCrypt.hashpw(TEST_PASSWORD, BCrypt.gensalt()),
                greeting = greeting,
                isAdmin = isAdmin,
                locale = locale,
                languageCode = languageCode
            )
        )
    }
}
