package io.orangebuffalo.aionify.domain

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * Tests for SSE token authentication and authorization.
 * Verifies that invalid or missing tokens are properly rejected.
 */
@MicronautTest(transactional = false)
class SseTokenAuthenticationTest {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `should reject SSE connection without token`() {
        // When: Attempting to connect to SSE endpoint without a token
        val request = HttpRequest.GET<Any>("/api-ui/time-log-entries/events")

        // Then: Should return 401 UNAUTHORIZED
        try {
            httpClient.toBlocking().exchange(request, String::class.java)
            fail("Expected request to fail without authentication")
        } catch (e: HttpClientResponseException) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.status, "Expected 401 UNAUTHORIZED")
        }
    }

    @Test
    fun `should reject SSE connection with invalid token`() {
        // When: Attempting to connect with an invalid token
        val request = HttpRequest.GET<Any>("/api-ui/time-log-entries/events?token=invalid-token-xyz")

        // Then: Should return 401 UNAUTHORIZED
        try {
            httpClient.toBlocking().exchange(request, String::class.java)
            fail("Expected request to fail with invalid token")
        } catch (e: HttpClientResponseException) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.status, "Expected 401 UNAUTHORIZED")
        }
    }
}
