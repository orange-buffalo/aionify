package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for browser title updates based on active entry state.
 */
class TimeLogsBrowserTitleTest : TimeLogsPageTestBase() {
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
}
