package io.orangebuffalo.aionify.auth

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * Test for AuthService to verify JWT token generation and authentication.
 *
 * The application uses Micronaut Security JWT to generate tokens with configured signing keys.
 */
@MicronautTest(transactional = false)
class AuthServiceTest {
    @Inject
    lateinit var authService: AuthService

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    private val testPassword = "testPassword123"
    private val testUserName = "testuser"
    private val testGreeting = "Test User"

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()
    }

    @Test
    fun `should authenticate and return valid JWT token`() {
        // Given: A user exists in the database
        val user =
            testDatabaseSupport.insert(
                User.create(
                    userName = testUserName,
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = testGreeting,
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )

        // When: Authenticating with correct credentials
        val response = authService.authenticate(testUserName, testPassword)

        // Then: Response should contain valid token and user info
        assertNotNull(response.token, "Token should not be null")
        assertTrue(response.token.isNotEmpty(), "Token should not be empty")
        assertEquals(testUserName, response.userName)
        assertEquals(testGreeting, response.greeting)
        assertFalse(response.admin)

        // Verify token contains expected parts (header.payload.signature)
        val tokenParts = response.token.split(".")
        assertEquals(3, tokenParts.size, "JWT token should have 3 parts separated by dots")
    }

    @Test
    fun `should throw exception for invalid username`() {
        // Given: User does not exist

        // When/Then: Authentication should fail
        assertThrows(AuthenticationException::class.java) {
            authService.authenticate("nonexistent", "password")
        }
    }

    @Test
    fun `should throw exception for invalid password`() {
        // Given: A user exists
        testDatabaseSupport.insert(
            User.create(
                userName = testUserName,
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = testGreeting,
                isAdmin = false,
                locale = java.util.Locale.US,
            ),
        )

        // When/Then: Authentication with wrong password should fail
        assertThrows(AuthenticationException::class.java) {
            authService.authenticate(testUserName, "wrongPassword")
        }
    }

    @Test
    fun `should generate token for admin user`() {
        // Given: An admin user exists
        val adminUser =
            testDatabaseSupport.insert(
                User.create(
                    userName = "admin",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Admin User",
                    isAdmin = true,
                    locale = java.util.Locale.US,
                ),
            )

        // When: Authenticating as admin
        val response = authService.authenticate("admin", testPassword)

        // Then: Token should be generated and admin flag should be true
        assertNotNull(response.token)
        assertTrue(response.token.isNotEmpty())
        assertTrue(response.admin)
        assertEquals("Admin User", response.greeting)
    }

    @Test
    fun `should refresh token for authenticated user`() {
        // Given: A user exists in the database
        val user =
            testDatabaseSupport.insert(
                User.create(
                    userName = testUserName,
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = testGreeting,
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )

        // When: Refreshing token
        val response = authService.refreshToken(testUserName)

        // Then: Response should contain a valid new token
        assertNotNull(response.token, "Token should not be null")
        assertTrue(response.token.isNotEmpty(), "Token should not be empty")

        // Verify token contains expected parts (header.payload.signature)
        val tokenParts = response.token.split(".")
        assertEquals(3, tokenParts.size, "JWT token should have 3 parts separated by dots")
    }

    @Test
    fun `should throw exception when refreshing token for non-existent user`() {
        // Given: User does not exist

        // When/Then: Token refresh should fail
        assertThrows(AuthenticationException::class.java) {
            authService.refreshToken("nonexistent")
        }
    }
}
