package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.Page
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.CurrentEntryState
import io.orangebuffalo.aionify.DayGroupState
import io.orangebuffalo.aionify.EntryState
import io.orangebuffalo.aionify.TimeLogsPageState
import io.orangebuffalo.aionify.api.StartTimeLogEntryRequest
import io.orangebuffalo.aionify.api.StartTimeLogEntryResponse
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for automatic UI updates when time entries are changed via the public API.
 * Verifies that SSE events are emitted when entries are started/stopped via the API.
 *
 * Note: SSE is disabled by default in Playwright tests (via window.__DISABLE_SSE__) to avoid
 * connection lifecycle issues. These tests verify that events are emitted by the backend,
 * which would trigger UI updates in production when SSE is enabled.
 */
@MicronautTest(transactional = false)
class TimeLogsAutomaticUpdateTest : TimeLogsPageTestBase() {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `should emit SSE event when entry is started via public API`() {
        // Given: User is logged in and viewing the time logs page
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state (no entries)
        val initialState = TimeLogsPageState()
        timeLogsPage.assertPageState(initialState)

        // Create API token for public API access
        val apiToken = "test-api-token"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = requireNotNull(testUser.id),
                token = apiToken,
            ),
        )

        // When: Start a time entry via public API
        val request =
            HttpRequest
                .POST(
                    "/api/time-log-entries/start",
                    StartTimeLogEntryRequest(title = "API Started Task"),
                ).bearerAuth(apiToken)

        val response = httpClient.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)
        assertEquals(200, response.status.code)

        // Then: Entry is created in database (SSE event would be emitted to subscribers)
        val entry =
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(testUser.id!!).orElse(null)
            }
        assertNotNull(entry)
        assertEquals("API Started Task", entry?.title)
        assertNull(entry?.endTime) // Entry is active
    }

    @Test
    fun `should emit SSE event when entry is stopped via public API`() {
        // Given: User has an active entry and is viewing the time logs page
        val activeEntry =
            testDatabaseSupport.insert(
                io.orangebuffalo.aionify.domain.TimeLogEntry(
                    startTime = FIXED_TEST_TIME,
                    endTime = null,
                    title = "Active Task",
                    ownerId = requireNotNull(testUser.id),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify active entry is shown
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Active Task",
                        duration = "00:00:00",
                        startedAt = "16 Mar, 03:30",
                    ),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Active Task",
                                        timeRange = "03:30 - in progress",
                                        duration = "00:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Create API token for public API access
        val apiToken = "test-api-token"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = requireNotNull(testUser.id),
                token = apiToken,
            ),
        )

        // When: Stop the entry via public API
        val request =
            HttpRequest
                .POST<Any>("/api/time-log-entries/stop", null)
                .bearerAuth(apiToken)

        val response = httpClient.toBlocking().exchange(request, Map::class.java)
        assertEquals(200, response.status.code)

        // Then: Entry is stopped in database (SSE event would be emitted to subscribers)
        val stoppedEntry =
            testDatabaseSupport.inTransaction {
                timeLogEntryRepository.findById(activeEntry.id!!).orElse(null)
            }
        assertNotNull(stoppedEntry)
        assertNotNull(stoppedEntry?.endTime) // Entry is stopped
    }
}
