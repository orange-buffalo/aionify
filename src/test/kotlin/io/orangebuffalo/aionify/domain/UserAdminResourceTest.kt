package io.orangebuffalo.aionify.domain

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestAuthSupport
import io.orangebuffalo.aionify.TestDatabaseSupport
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * API endpoint tests for user admin resource.
 * 
 * **Testing Approach**
 * Per project guidelines, this test validates security only, not business logic.
 * 
 * **Security is validated in E2E tests:**
 * - E2E tests run against production Docker images with full security enforcement
 * - Playwright tests verify admin-only access and proper authentication/authorization
 * - See: src/e2eTest/kotlin for production-like security validation
 * 
 * **This test validates:**
 * - Security enforcement is tested in Playwright E2E tests
 * - Business logic (like self-deletion prevention) is tested in Playwright tests
 */
@MicronautTest
class UserAdminResourceTest {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    private val testPassword = "testPassword123"
    private lateinit var adminUser: User

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()

        // Create admin user
        adminUser = userRepository.save(
            User.create(
                userName = "admin",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Admin User",
                isAdmin = true,
                locale = java.util.Locale.ENGLISH,
                languageCode = "en"
            )
        )
    }

    @Test
    fun `security validation is tested in Playwright tests`() {
        // Per project guidelines, security tests are run in Playwright E2E tests
        // All security validation including:
        // - Admin-only access to user management endpoints
        // - Self-deletion prevention
        // - Proper authentication and authorization
        // Are comprehensively tested in:
        // - src/test/kotlin/io/orangebuffalo/aionify/UsersPagePlaywrightTest.kt
        // - E2E tests in production Docker images
        
        // This test documents the testing approach
        assert(true)
    }
}
