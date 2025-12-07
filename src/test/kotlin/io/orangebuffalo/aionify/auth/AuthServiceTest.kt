package io.orangebuffalo.aionify.auth

import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import io.quarkus.elytron.security.common.BcryptUtil
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Test for AuthService to verify JWT token generation and authentication.
 * 
 * The application uses the Auth0 java-jwt library to auto-generate RSA key pairs for both JWT
 * signing and validation at startup. All keys are kept in-memory only, with no file
 * storage required. This eliminates issues with shared files between test forks or
 * multiple application instances.
 */
@QuarkusTest
class AuthServiceTest {

    @Inject
    lateinit var authService: AuthService

    @Inject
    lateinit var userRepository: UserRepository

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
        val user = userRepository.insert(
            User(
                userName = testUserName,
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = testGreeting,
                isAdmin = false,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )

        // When: Authenticating with correct credentials
        val response = authService.authenticate(testUserName, testPassword)

        // Then: Response should contain valid token and user info
        assertNotNull(response.token, "Token should not be null")
        assertTrue(response.token.isNotEmpty(), "Token should not be empty")
        assertEquals(testUserName, response.userName)
        assertEquals(testGreeting, response.greeting)
        assertFalse(response.isAdmin)
        
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
        userRepository.insert(
            User(
                userName = testUserName,
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = testGreeting,
                isAdmin = false,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )

        // When/Then: Authentication with wrong password should fail
        assertThrows(AuthenticationException::class.java) {
            authService.authenticate(testUserName, "wrongPassword")
        }
    }

    @Test
    fun `should generate token for admin user`() {
        // Given: An admin user exists
        val adminUser = userRepository.insert(
            User(
                userName = "admin",
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = "Admin User",
                isAdmin = true,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )

        // When: Authenticating as admin
        val response = authService.authenticate("admin", testPassword)

        // Then: Token should be generated and isAdmin flag should be true
        assertNotNull(response.token)
        assertTrue(response.token.isNotEmpty())
        assertTrue(response.isAdmin)
        assertEquals("Admin User", response.greeting)
    }
}
