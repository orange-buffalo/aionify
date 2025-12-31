package io.orangebuffalo.aionify.api

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TestDatabaseSupport
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import io.orangebuffalo.aionify.domain.UserApiAccessTokenRepository
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * Tests for public API authentication and rate limiting.
 *
 * Validates:
 * 1. Bearer token authentication is required for /api/ endpoints
 * 2. Valid tokens grant access
 * 3. Invalid tokens are rejected
 * 4. Rate limiting blocks IPs after 10 failed attempts
 * 5. /api/schema is accessible without authentication
 */
@MicronautTest(transactional = false)
class ApiAuthenticationTest {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userApiAccessTokenRepository: UserApiAccessTokenRepository

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var apiRateLimitingService: ApiRateLimitingService

    private lateinit var testUser: User
    private lateinit var validToken: String

    @BeforeEach
    fun setupTestData() {
        testDatabaseSupport.truncateAllTables()

        // Create test user
        testUser =
            testDatabaseSupport.insert(
                User.create(
                    userName = "apiuser",
                    passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                    greeting = "API User",
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )

        // Create API token
        validToken = "test-api-token-12345"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = testUser.id!!,
                token = validToken,
            ),
        )

        // Clear any existing rate limit state
        apiRateLimitingService.clearAttempts("127.0.0.1")
    }

    @Test
    fun `should allow access with valid Bearer token`() {
        // When: Making a request with valid Bearer token
        val request =
            HttpRequest
                .GET<Any>("/api/version")
                .bearerAuth(validToken)

        val response = client.toBlocking().exchange(request, Map::class.java)

        // Then: Request succeeds
        assertEquals(HttpStatus.OK, response.status)
        assertEquals("1.0", response.body()?.get("version"))
    }

    @Test
    fun `should reject request without Authorization header`() {
        // When: Making a request without Authorization header
        val request = HttpRequest.GET<Any>("/api/version")

        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(request, Map::class.java)
            }

        // Then: Request is rejected with 401
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should reject request with invalid Bearer token`() {
        // When: Making a request with invalid token
        val request =
            HttpRequest
                .GET<Any>("/api/version")
                .bearerAuth("invalid-token")

        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(request, Map::class.java)
            }

        // Then: Request is rejected with 401
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
    }

    @Test
    fun `should block IP after 10 failed authentication attempts`() {
        // Given: 10 failed authentication attempts
        repeat(10) {
            try {
                val request =
                    HttpRequest
                        .GET<Any>("/api/version")
                        .bearerAuth("invalid-token-$it")
                client.toBlocking().exchange(request, Map::class.java)
            } catch (e: HttpClientResponseException) {
                // Expected - ignore
            }
        }

        // When: Making another request (11th attempt)
        val request =
            HttpRequest
                .GET<Any>("/api/version")
                .bearerAuth("another-invalid-token")

        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(request, Map::class.java)
            }

        // Then: Request is blocked with 429 Too Many Requests
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.status)
    }

    @Test
    fun `should clear failed attempts after successful authentication`() {
        // Given: Some failed attempts
        repeat(5) {
            try {
                val request =
                    HttpRequest
                        .GET<Any>("/api/version")
                        .bearerAuth("invalid-token-$it")
                client.toBlocking().exchange(request, Map::class.java)
            } catch (e: HttpClientResponseException) {
                // Expected - ignore
            }
        }

        // When: Making a successful request
        val successRequest =
            HttpRequest
                .GET<Any>("/api/version")
                .bearerAuth(validToken)
        val response = client.toBlocking().exchange(successRequest, Map::class.java)

        // Then: Request succeeds
        assertEquals(HttpStatus.OK, response.status)

        // And: Failed attempts are cleared - we can make 10 more invalid attempts
        repeat(10) {
            try {
                val request =
                    HttpRequest
                        .GET<Any>("/api/version")
                        .bearerAuth("invalid-token-after-success-$it")
                client.toBlocking().exchange(request, Map::class.java)
            } catch (e: HttpClientResponseException) {
                // Expected - ignore
            }
        }

        // And: 11th attempt is blocked
        val blockedRequest =
            HttpRequest
                .GET<Any>("/api/version")
                .bearerAuth("final-invalid-token")

        val exception =
            assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().exchange(blockedRequest, Map::class.java)
            }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.status)
    }

    @Test
    fun `should allow access to OpenAPI schema without authentication`() {
        // When: Accessing /api/schema without authentication
        val request = HttpRequest.GET<Any>("/api/schema")

        val response = client.toBlocking().exchange(request, String::class.java)

        // Then: Request succeeds
        assertEquals(HttpStatus.OK, response.status)

        // And: Response is not empty (actual OpenAPI schema content may vary)
        assertNotNull(response.body())
        assertTrue(response.body()!!.isNotEmpty())
    }

    @Test
    fun `should not apply rate limiting to schema endpoint`() {
        // Given: 10 failed authentication attempts to block the IP
        repeat(10) {
            try {
                val request =
                    HttpRequest
                        .GET<Any>("/api/version")
                        .bearerAuth("invalid-token-$it")
                client.toBlocking().exchange(request, Map::class.java)
            } catch (e: HttpClientResponseException) {
                // Expected - ignore
            }
        }

        // When: Accessing /api/schema after IP is blocked
        val request = HttpRequest.GET<Any>("/api/schema")

        val response = client.toBlocking().exchange(request, String::class.java)

        // Then: Request to schema still succeeds (not affected by rate limiting)
        assertEquals(HttpStatus.OK, response.status)
    }
}
