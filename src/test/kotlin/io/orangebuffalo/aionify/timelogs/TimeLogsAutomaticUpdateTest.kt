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
import io.orangebuffalo.aionify.WeekNavigationState
import io.orangebuffalo.aionify.api.StartTimeLogEntryRequest
import io.orangebuffalo.aionify.api.StartTimeLogEntryResponse
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import io.orangebuffalo.aionify.timeInTestTz
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for automatic UI updates when time entries are changed via the public API.
 * Verifies that the UI automatically updates via SSE when entries are started/stopped.
 */
@MicronautTest(transactional = false)
class TimeLogsAutomaticUpdateTest : TimeLogsPageTestBase() {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `should automatically update UI when entry is started via public API`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        setBaseTime("2024-03-16", "03:30")

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

        // Then: UI should automatically update via SSE
        // Playwright assertions auto-wait for the expected state
        val updatedState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "API Started Task",
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
                                        title = "API Started Task",
                                        timeRange = "03:30 - in progress",
                                        duration = "00:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(updatedState)
    }

    @Test
    fun `should automatically update UI when entry is stopped via public API`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Given: User has an active entry and is viewing the time logs page
        val activeEntry =
            testDatabaseSupport.insert(
                io.orangebuffalo.aionify.domain.TimeLogEntry(
                    startTime = baseTime,
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

        // Then: UI should automatically update via SSE
        // Playwright assertions auto-wait for the expected state
        val updatedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Active Task",
                                        timeRange = "03:30 - 03:30",
                                        duration = "00:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(updatedState)
    }

    @Test
    fun `should handle race condition when starting new entry stops active one`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Given: User has an active entry and is viewing the time logs page
        val activeEntry =
            testDatabaseSupport.insert(
                io.orangebuffalo.aionify.domain.TimeLogEntry(
                    startTime = timeInTestTz("2024-03-16", "03:00"),
                    endTime = null,
                    title = "First Task",
                    ownerId = requireNotNull(testUser.id),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "First Task",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "First Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
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

        // When: Start a new entry via public API
        // This will trigger two SSE events: ENTRY_STOPPED for "First Task" and ENTRY_STARTED for "Second Task"
        // The race condition is that these two events trigger concurrent API requests,
        // and we need to ensure the latest data is displayed regardless of response order
        val request =
            HttpRequest
                .POST(
                    "/api/time-log-entries/start",
                    StartTimeLogEntryRequest(title = "Second Task"),
                ).bearerAuth(apiToken)

        val response = httpClient.toBlocking().exchange(request, StartTimeLogEntryResponse::class.java)
        assertEquals(200, response.status.code)

        // Then: UI should show the new active entry with both entries in the list
        // Even if there's a race condition in request completion order, the final state must be correct
        val finalState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Second Task",
                        duration = "00:00:00",
                        startedAt = "16 Mar, 03:30",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Second Task",
                                        timeRange = "03:30 - in progress",
                                        duration = "00:00:00",
                                    ),
                                    EntryState(
                                        title = "First Task",
                                        timeRange = "03:00 - 03:30",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(finalState)
    }
}
