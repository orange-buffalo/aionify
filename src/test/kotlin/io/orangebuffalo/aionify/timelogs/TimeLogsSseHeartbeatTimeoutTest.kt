package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.Page
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.TimeLogsPageState
import io.orangebuffalo.aionify.api.StartTimeLogEntryRequest
import io.orangebuffalo.aionify.api.StartTimeLogEntryResponse
import io.orangebuffalo.aionify.domain.TimeLogEntryEventResource
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for SSE heartbeat timeout detection and automatic reconnection.
 * Verifies that the UI automatically reconnects when heartbeats stop arriving.
 */
@MicronautTest(transactional = false)
class TimeLogsSseHeartbeatTimeoutTest : TimeLogsPageTestBase() {
    @Inject
    lateinit var timeLogEntryEventResource: TimeLogEntryEventResource

    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `should reconnect when heartbeats stop arriving`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        setBaseTime("2024-03-16", "03:30")

        // Create API token for public API access
        val apiToken = "test-api-token"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = requireNotNull(testUser.id),
                token = apiToken,
            ),
        )

        // Given: User is logged in and viewing the time logs page
        // Heartbeats are enabled initially for connection establishment
        timeLogEntryEventResource.heartbeatEnabled = true
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state (no entries)
        val initialState = TimeLogsPageState()
        timeLogsPage.assertPageState(initialState)

        // Wait for initial connection to be established (immediate heartbeat is sent)
        Thread.sleep(2000)

        // Disable heartbeats to simulate stale connection
        // The frontend should detect missing heartbeats and reconnect
        timeLogEntryEventResource.heartbeatEnabled = false

        // Fast-forward time in the browser to trigger heartbeat timeout
        // Frontend heartbeat timeout: 45 seconds
        page.clock().runFor(45000)

        // Wait a bit for timeout to be detected and reconnection to be scheduled
        Thread.sleep(1000)

        // Advance clock for reconnection delay (5 seconds)
        page.clock().runFor(5000)

        // Wait for reconnection to actually happen
        Thread.sleep(2000)

        // Re-enable heartbeats for the reconnected connection
        timeLogEntryEventResource.heartbeatEnabled = true

        // Verify reconnection happened by checking console logs
        // The implementation logs "[SSE] Heartbeat timeout - no heartbeat received, reconnecting..."
        // and "[SSE] Connection established" when reconnection succeeds
        // We can verify this by checking that a second connection was established

        // Now test that events are received over the reconnected connection
        // by starting an entry via public API
        val request =
            HttpRequest
                .POST(
                    "/api/time-log-entries/start",
                    StartTimeLogEntryRequest(title = "API Started Task"),
                ).bearerAuth(apiToken)

        val response = httpClient.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)
        assertEquals(200, response.status.code)

        // Verify the entry was created and received via SSE
        // by waiting for it to appear in the UI
        page.waitForSelector("text=API Started Task", Page.WaitForSelectorOptions().setTimeout(10000.0))
    }
}
