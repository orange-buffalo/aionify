package io.orangebuffalo.aionify

import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Singleton
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

/**
 * Singleton that provides commonly used test users and credentials.
 * Simplifies test setup by centralizing user creation patterns.
 * 
 * **CRITICAL:** All saves are wrapped in testDatabaseSupport.insert to ensure
 * they are committed immediately and visible to browser HTTP requests.
 */
@Singleton
class TestUsers(
    private val testDatabaseSupport: TestDatabaseSupport
) {
    
    companion object {
        const val TEST_PASSWORD = "testPassword123"
        const val ADMIN_USERNAME = "admin"
        const val ADMIN_GREETING = "Admin User"
        const val REGULAR_USERNAME = "testuser"
        const val REGULAR_GREETING = "Test User"
    }
    
    /**
     * Creates and saves an admin user with standard test credentials.
     * Uses testDatabaseSupport to commit the transaction immediately,
     * making the user visible to browser HTTP requests in Playwright tests.
     */
    fun createAdmin(
        username: String = ADMIN_USERNAME,
        greeting: String = ADMIN_GREETING
    ): User {
        return testDatabaseSupport.insert(
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
     * Uses testDatabaseSupport to commit the transaction immediately,
     * making the user visible to browser HTTP requests in Playwright tests.
     */
    fun createRegularUser(
        username: String = REGULAR_USERNAME,
        greeting: String = REGULAR_GREETING
    ): User {
        return testDatabaseSupport.insert(
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
     * Uses testDatabaseSupport to commit the transaction immediately,
     * making the user visible to browser HTTP requests in Playwright tests.
     */
    fun createUserWithLocale(
        username: String,
        greeting: String,
        isAdmin: Boolean = false,
        locale: Locale,
        languageCode: String
    ): User {
        return testDatabaseSupport.insert(
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
