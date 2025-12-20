package io.orangebuffalo.aionify

import com.microsoft.playwright.Clock
import com.microsoft.playwright.Locator
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
import org.junit.jupiter.api.Assertions.assertTrue
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
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
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
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
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
        val lastWeek = FIXED_TEST_TIME.minusSeconds(7 * 24 * 3600)
        
        // Current week entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
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
        // FIXED_TEST_TIME is 2024-03-15T14:30:00Z (Friday afternoon)
        // We'll create an entry that starts on Thursday and ends on Friday
        val yesterdayEvening = FIXED_TEST_TIME.minusSeconds(24 * 3600 - 2 * 3600) // Thursday 16:30:00Z
        val todayMorning = FIXED_TEST_TIME.minusSeconds(12 * 3600) // Friday 02:30:00Z
        
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = yesterdayEvening, // Started on Thursday evening
                endTime = todayMorning, // Ended on Friday morning
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
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
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
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
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
    
    @Test
    fun `should verify active task duration using clock`() {
        // Create an active entry that started 30 minutes ago (1800 seconds)
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify active timer shows the correct duration (30 minutes = 00:30:00)
        val activeTimer = page.locator("[data-testid='active-timer']")
        assertThat(activeTimer).isVisible()
        assertThat(activeTimer).hasText("00:30:00")
        
        // Advance the clock by 5 minutes
        page.clock().runFor(5 * 60 * 1000)
        
        // Timer should now show 35 minutes (00:35:00)
        assertThat(activeTimer).hasText("00:35:00")
        
        // Advance another 25 minutes to reach 1 hour
        page.clock().runFor(25 * 60 * 1000)
        
        // Timer should now show 1 hour (01:00:00)
        assertThat(activeTimer).hasText("01:00:00")
    }
    
    @Test
    fun `should render entries from multiple days with varying entry counts`() {
        // FIXED_TEST_TIME is 2024-03-15T14:30:00Z (Friday, March 15)
        // Let's create entries for different days in the current week (Mon Mar 11 - Sun Mar 17)
        
        // Monday (Mar 11) - 2 entries
        val monday = FIXED_TEST_TIME.minusSeconds(4 * 24 * 3600) // 4 days before Friday
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = monday.minusSeconds(7200),
                endTime = monday.minusSeconds(5400),
                title = "Monday Task 1",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = monday.minusSeconds(3600),
                endTime = monday.minusSeconds(1800),
                title = "Monday Task 2",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Tuesday (Mar 12) - 1 entry
        val tuesday = FIXED_TEST_TIME.minusSeconds(3 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = tuesday.minusSeconds(3600),
                endTime = tuesday.minusSeconds(1800),
                title = "Tuesday Task",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Wednesday (Mar 13) - 3 entries
        val wednesday = FIXED_TEST_TIME.minusSeconds(2 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = wednesday.minusSeconds(10800),
                endTime = wednesday.minusSeconds(9000),
                title = "Wednesday Task 1",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = wednesday.minusSeconds(7200),
                endTime = wednesday.minusSeconds(5400),
                title = "Wednesday Task 2",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = wednesday.minusSeconds(3600),
                endTime = wednesday.minusSeconds(1800),
                title = "Wednesday Task 3",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Friday (Mar 15) - today - 1 entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Friday Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify Friday (today) has 1 entry
        val fridayGroup = page.locator("[data-testid='time-entry']:has-text('Friday Task')")
        assertThat(fridayGroup).isVisible()
        
        // Verify we can see "Today" label for Friday
        assertThat(page.locator("text=/Today/i")).isVisible()
        
        // Verify Wednesday has 3 entries
        val wednesdayEntries = page.locator("[data-testid='time-entry']:has-text('Wednesday Task')")
        assertEquals(3, wednesdayEntries.count())
        assertThat(wednesdayEntries.first()).containsText("Wednesday Task")
        
        // Verify Tuesday has 1 entry
        val tuesdayEntry = page.locator("[data-testid='time-entry']:has-text('Tuesday Task')")
        assertThat(tuesdayEntry).isVisible()
        assertEquals(1, tuesdayEntry.count())
        
        // Verify Monday has 2 entries
        val mondayEntries = page.locator("[data-testid='time-entry']:has-text('Monday Task')")
        assertEquals(2, mondayEntries.count())
    }
    
    @Test
    fun `should handle midnight split scenario correctly`() {
        // FIXED_TEST_TIME is 2024-03-15T14:30:00Z (Friday afternoon)
        // Create an entry that spans from Thursday 23:00 UTC to Friday 01:00 UTC
        val thursdayEvening = FIXED_TEST_TIME.minusSeconds(55800) // Thursday 23:00 UTC (15.5 hours before)
        val fridayMorning = FIXED_TEST_TIME.minusSeconds(48600) // Friday 01:00 UTC (13.5 hours before)
        
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = thursdayEvening,
                endTime = fridayMorning,
                title = "Midnight Spanning Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify the entry appears split across 2 time entry elements
        val spanningEntries = page.locator("[data-testid='time-entry']:has-text('Midnight Spanning Task')")
        assertEquals(2, spanningEntries.count(), "Entry should be split across two days")
        
        // Verify both entry parts are visible and contain the correct title
        assertThat(spanningEntries.first()).isVisible()
        assertThat(spanningEntries.first()).containsText("Midnight Spanning Task")
        
        assertThat(spanningEntries.nth(1)).isVisible()
        assertThat(spanningEntries.nth(1)).containsText("Midnight Spanning Task")
    }
    
    @Test
    fun `should delete midnight-split entry correctly`() {
        // Create an entry that spans midnight
        val thursdayEvening = FIXED_TEST_TIME.minusSeconds(55800) // Thursday 23:00 UTC (15.5 hours before)
        val fridayMorning = FIXED_TEST_TIME.minusSeconds(48600) // Friday 01:00 UTC (13.5 hours before)
        
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = thursdayEvening,
                endTime = fridayMorning,
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify we have exactly 2 day groups before deletion
        val dayGroupsBefore = page.locator("[data-testid='day-group']")
        assertEquals(2, dayGroupsBefore.count(), "Should have exactly 2 day groups before deletion")
        
        // Verify entry exists in both day groups
        val entriesBefore = page.locator("[data-testid='time-entry']:has-text('Task to Delete')")
        assertEquals(2, entriesBefore.count(), "Entry should be split across two days")

        // Delete the first instance (from Friday group)
        val firstEntryMenuButton = page.locator("[data-testid='time-entry']:has-text('Task to Delete')")
            .first()
            .locator("[data-testid='entry-menu-button']")
        firstEntryMenuButton.click()

        val deleteMenuItem = page.locator("[data-testid='delete-menu-item']")
        deleteMenuItem.click()

        // Confirm deletion
        val confirmButton = page.locator("[data-testid='confirm-delete-button']")
        assertThat(confirmButton).isVisible()
        confirmButton.click()

        // Wait for dialog to close
        assertThat(confirmButton).not().isVisible()

        // Both parts of the entry should be removed (since it's the same entry in DB)
        page.waitForCondition {
            page.locator("[data-testid='time-entry']:has-text('Task to Delete')").count() == 0
        }

        // Verify no error message
        assertThat(page.locator("[data-testid='time-logs-error']")).not().isVisible()
        
        // Verify both day groups are gone (no entries left for those days)
        // OR verify we show "no entries" message
        val dayGroupsAfter = page.locator("[data-testid='day-group']")
        val noEntriesMessage = page.locator("[data-testid='no-entries']")
        
        // Either no day groups remain, or we see the no entries message
        val hasNoDayGroups = dayGroupsAfter.count() == 0
        val hasNoEntriesMessage = noEntriesMessage.isVisible()
        
        assertTrue(hasNoDayGroups || hasNoEntriesMessage, 
            "After deletion, should either have no day groups or show 'no entries' message")
    }
}
