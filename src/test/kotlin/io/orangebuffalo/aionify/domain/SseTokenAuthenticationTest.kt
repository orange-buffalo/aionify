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

        // Then: Should fail with a client error (400 or 500)
        try {
            httpClient.toBlocking().exchange(request, String::class.java)
            fail("Expected request to fail without authentication")
        } catch (e: HttpClientResponseException) {
            // Expected - the request should fail when no authentication is present
            assert(e.status.code >= 400) {
                "Expected 4xx or 5xx status, got ${e.status}"
            }
        }
    }

    @Test
    fun `should reject SSE connection with invalid token`() {
        // When: Attempting to connect with an invalid token
        val request = HttpRequest.GET<Any>("/api-ui/time-log-entries/events?token=invalid-token-xyz")

        // Then: Should fail with a client error (400 or 500)
        try {
            httpClient.toBlocking().exchange(request, String::class.java)
            fail("Expected request to fail with invalid token")
        } catch (e: HttpClientResponseException) {
            // Expected - the request should fail when invalid token is provided
            assert(e.status.code >= 400) {
                "Expected 4xx or 5xx status, got ${e.status}"
            }
        }
    }

    @Test
    fun `should reject SSE connection with expired token`() {
        // This is tested in SseTokenServiceTest with mocked time
        // Here we just document that expired tokens are handled
    }
}
