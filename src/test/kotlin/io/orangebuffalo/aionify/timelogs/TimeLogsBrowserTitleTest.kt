package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.orangebuffalo.aionify.api.StartTimeLogEntryRequest
import io.orangebuffalo.aionify.api.StartTimeLogEntryResponse
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for browser title updates based on active entry state.
 */
class TimeLogsBrowserTitleTest : TimeLogsPageTestBase() {
    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Test
    fun `should update browser tab title based on active entry`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial page title is the default app name
        assertThat(page).hasTitle("Aionify - Time Tracking")

        // Start a new entry
        timeLogsPage.fillNewEntryTitle("Working on browser title feature")
        timeLogsPage.clickStart()

        // Verify browser title updates to the active task title
        page.waitForCondition {
            page.title() == "Working on browser title feature"
        }
        assertThat(page).hasTitle("Working on browser title feature")

        // Stop the entry
        timeLogsPage.clickStop()

        // Verify browser title returns to default
        page.waitForCondition {
            page.title() == "Aionify - Time Tracking"
        }
        assertThat(page).hasTitle("Aionify - Time Tracking")
    }

    @Test
    fun `should update browser tab title when entry is started and stopped via public API`() {
        // Given: User is logged in and viewing the time logs page
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial page title is the default app name
        assertThat(page).hasTitle("Aionify - Time Tracking")

        // Create API token for public API access
        val apiToken = "test-api-token"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = requireNotNull(testUser.id),
                token = apiToken,
            ),
        )

        // When: Start a time entry via public API
        val startRequest =
            HttpRequest
                .POST(
                    "/api/time-log-entries/start",
                    StartTimeLogEntryRequest(title = "API Started Task"),
                ).bearerAuth(apiToken)

        val startResponse = httpClient.toBlocking().exchange(startRequest, StartTimeLogEntryResponse::class.java)
        assertEquals(200, startResponse.status.code)

        // Then: Browser title should automatically update via SSE
        page.waitForCondition {
            page.title() == "API Started Task"
        }
        assertThat(page).hasTitle("API Started Task")

        // When: Stop the entry via public API
        val stopRequest =
            HttpRequest
                .POST<Any>("/api/time-log-entries/stop", null)
                .bearerAuth(apiToken)

        val stopResponse = httpClient.toBlocking().exchange(stopRequest, Map::class.java)
        assertEquals(200, stopResponse.status.code)

        // Then: Browser title should return to default via SSE
        page.waitForCondition {
            page.title() == "Aionify - Time Tracking"
        }
        assertThat(page).hasTitle("Aionify - Time Tracking")
    }
}
