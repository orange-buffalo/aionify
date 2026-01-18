package io.orangebuffalo.aionify.auth

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestAuthSupport
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * API endpoint tests for JWT token expiration validation.
 *
 * Tests verify that the API properly validates JWT tokens:
 * 1. Expired tokens are rejected
 * 2. Valid tokens with proper expiration are accepted
 */
@MicronautTest(transactional = false)
class JwtTokenExpirationApiTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    private val testPassword = "testPassword123"
    private lateinit var testUser: User

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()

        // Create test user
        testUser =
            testDatabaseSupport.insert(
                User.create(
                    userName = "testuser",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Test User",
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )
    }

    @Test
    fun `should reject expired token`() {
        // Given: An expired token
        val expiredToken = testAuthSupport.generateExpiredToken(testUser)

        // When: Making a request with the expired token
        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(
                    HttpRequest
                        .GET<Any>("/api-ui/users/profile")
                        .bearerAuth(expiredToken),
                    String::class.java,
                )
            }

        // Then: Request should be unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should accept token with valid expiration`() {
        // Given: A valid token with proper expiration
        val validToken = testAuthSupport.generateToken(testUser)

        // When: Making a request with the valid token
        val response =
            client.toBlocking().exchange(
                HttpRequest
                    .GET<Any>("/api-ui/users/profile")
                    .bearerAuth(validToken),
                String::class.java,
            )

        // Then: Request should succeed
        assertEquals(HttpStatus.OK, response.status)
    }
}
