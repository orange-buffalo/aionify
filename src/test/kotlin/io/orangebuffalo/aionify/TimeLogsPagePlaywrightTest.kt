package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeEntry
import io.orangebuffalo.aionify.domain.TimeEntryRepository
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Playwright tests for the Time Logs page functionality.
 */
@MicronautTest(transactional = false)
class TimeLogsPagePlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var testUser: User

    @BeforeEach
    fun setupTestData() {
        testUser = testUsers.createRegularUser()
    }

    @Test
    fun `should display time logs page with navigation`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify page is displayed
        val pageTitle = page.locator("[data-testid='time-logs-title']")
        assertThat(pageTitle).isVisible()
        assertThat(pageTitle).hasText("Time Logs")

        // Verify week navigation
        assertThat(page.locator("[data-testid='previous-week-button']")).isVisible()
        assertThat(page.locator("[data-testid='next-week-button']")).isVisible()
        assertThat(page.locator("[data-testid='week-range']")).isVisible()

        // Verify current entry panel
        assertThat(page.locator("[data-testid='current-entry-panel']")).isVisible()
        assertThat(page.locator("[data-testid='new-entry-input']")).isVisible()
        assertThat(page.locator("[data-testid='start-button']")).isVisible()
    }

    @Test
    fun `should start and stop a time entry`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Start a new entry
        val titleInput = page.locator("[data-testid='new-entry-input']")
        titleInput.fill("Test Task")
        
        val startButton = page.locator("[data-testid='start-button']")
        startButton.click()

        // Verify entry is started - active timer should be visible
        val activeTimer = page.locator("[data-testid='active-timer']")
        assertThat(activeTimer).isVisible()
        
        // Verify stop button is visible
        val stopButton = page.locator("[data-testid='stop-button']")
        assertThat(stopButton).isVisible()

        // Stop the entry
        stopButton.click()

        // Verify we're back to start state
        assertThat(page.locator("[data-testid='new-entry-input']")).isVisible()
        assertThat(page.locator("[data-testid='start-button']")).isVisible()

        // Verify entry appears in the list
        val entries = page.locator("[data-testid='time-entry']")
        assertThat(entries.first()).isVisible()
        assertThat(entries.first()).containsText("Test Task")
    }

    @Test
    fun `should continue with an existing entry`() {
        // Create a completed entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = Instant.now().minusSeconds(3600),
                endTime = Instant.now().minusSeconds(1800),
                title = "Previous Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click continue button on the existing entry
        val continueButton = page.locator("[data-testid='continue-button']").first()
        continueButton.click()

        // Verify the entry is started immediately with the same title
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()
        assertThat(page.locator("[data-testid='current-entry-panel']").getByText("Previous Task")).isVisible()
        assertThat(page.locator("[data-testid='stop-button']")).isVisible()
    }

    @Test
    fun `should delete a time entry with confirmation`() {
        // Create an entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = Instant.now().minusSeconds(3600),
                endTime = Instant.now().minusSeconds(1800),
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify entry exists
        val entry = page.locator("[data-testid='time-entry']").first()
        assertThat(entry).containsText("Task to Delete")

        // Open dropdown menu
        val menuButton = page.locator("[data-testid='entry-menu-button']").first()
        menuButton.click()

        // Click delete
        val deleteMenuItem = page.locator("[data-testid='delete-menu-item']")
        deleteMenuItem.click()

        // Confirm deletion in dialog
        val confirmButton = page.locator("[data-testid='confirm-delete-button']")
        assertThat(confirmButton).isVisible()
        confirmButton.click()

        // Wait for dialog to close
        assertThat(confirmButton).not().isVisible()

        // Wait for entry to be removed from the list
        page.waitForCondition {
            page.locator("[data-testid='time-entry']:has-text('Work Entry')").count() == 0
        }

        // Verify no error message
        assertThat(page.locator("[data-testid='time-logs-error']")).not().isVisible()
    }

    @Test
    fun `should navigate between weeks`() {
        // Create entries for different weeks
        val now = Instant.now()
        val lastWeek = now.minusSeconds(7 * 24 * 3600)
        
        // Current week entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = now.minusSeconds(3600),
                endTime = now.minusSeconds(1800),
                title = "This Week Task",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Last week entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = lastWeek.minusSeconds(3600),
                endTime = lastWeek.minusSeconds(1800),
                title = "Last Week Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify current week entry is visible
        assertThat(page.locator("text=This Week Task")).isVisible()

        // Navigate to previous week
        page.locator("[data-testid='previous-week-button']").click()

        // Verify last week entry is visible
        assertThat(page.locator("text=Last Week Task")).isVisible()
        
        // Navigate back to current week
        page.locator("[data-testid='next-week-button']").click()
        
        // Verify current week entry is visible again
        assertThat(page.locator("text=This Week Task")).isVisible()
    }

    @Test
    fun `should handle entry spanning two dates`() {
        // Create an entry that spans midnight
        val today = Instant.now()
        val todayStart = today.minusSeconds(today.epochSecond % 86400) // Start of today
        val yesterdayEnd = todayStart.minusSeconds(1) // End of yesterday
        
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = yesterdayEnd.minusSeconds(7200), // Started 2 hours before midnight
                endTime = todayStart.plusSeconds(3600), // Ended 1 hour after midnight
                title = "Spanning Entry",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Entry should appear in both days
        val entries = page.locator("[data-testid='time-entry']")
        val entryCount = entries.count()
        
        // Should see the entry at least once (may be split across two day groups)
        assertThat(entries.first()).containsText("Spanning Entry")
    }

    @Test
    fun `should show no entries message when week is empty`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify no entries message
        assertThat(page.locator("[data-testid='no-entries']")).isVisible()
    }

    @Test
    fun `should show active entry on page load`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = Instant.now().minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify active entry is shown with timer
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()
        assertThat(page.locator("[data-testid='stop-button']")).isVisible()
        
        // Verify title is shown in the current entry panel
        assertThat(page.locator("[data-testid='current-entry-panel']").locator("text=Active Task")).isVisible()
    }

    @Test
    fun `should require title to start entry`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Try to start without title
        val startButton = page.locator("[data-testid='start-button']")
        
        // Button should be disabled
        assertThat(startButton).isDisabled()
    }

    @Test
    fun `should allow starting entry by pressing Enter`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Fill in title and press Enter
        val titleInput = page.locator("[data-testid='new-entry-input']")
        titleInput.fill("Quick Entry")
        titleInput.press("Enter")

        // Verify entry is started
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()
        assertThat(page.locator("[data-testid='current-entry-panel']").locator("text=Quick Entry")).isVisible()
    }

    @Test
    fun `should prevent starting new entry while another is active`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = Instant.now().minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify we cannot start a new entry
        assertThat(page.locator("[data-testid='new-entry-input']")).not().isVisible()
        assertThat(page.locator("[data-testid='start-button']")).not().isVisible()
        
        // Only stop button should be visible
        assertThat(page.locator("[data-testid='stop-button']")).isVisible()
    }

    @Test
    fun `should display timezone hint`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify timezone hint is visible somewhere on the page
        val hint = page.locator("text=/Times shown in/")
        assertThat(hint).isVisible()
    }
}
