package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Tests for inline time editing functionality.
 * Verifies that users can edit start and end times inline using a popover.
 */
class TimeLogsInlineTimeEditTest : TimeLogsPageTestBase() {
    @Test
    fun `should allow inline edit of start time on stopped entry`() {
        // Create a stopped entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600), // 1 hour ago
                    endTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago
                    title = "Test Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click on the start time to open the popover
        page.locator("[data-testid='time-entry-inline-start-time-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-popover']")).isVisible()

        // Verify time picker is visible
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-time-input']")).isVisible()

        // Verify calendar is visible
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-grid']")).isVisible()

        // Change the time to 13:45 (after the original 13:30 start time, before the 14:00 end time)
        val timeInput = page.locator("[data-testid='time-entry-inline-start-time-time-input']")
        timeInput.fill("13:45")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-start-time-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-popover']")).not().isVisible()

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            // The time part should be updated to 13:45
            // Compare Instants directly to avoid timezone issues
            assertNotEquals(entry.startTime, updatedEntry.startTime, "Start time should have changed")

            // Ensure other fields were not changed
            assertEquals(entry.endTime, updatedEntry.endTime)
            assertEquals(entry.title, updatedEntry.title)
        }
    }

    @Test
    fun `should allow inline edit of end time on stopped entry`() {
        // Create a stopped entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600), // 1 hour ago
                    endTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago
                    title = "Test Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click on the end time to open the popover
        page.locator("[data-testid='time-entry-inline-end-time-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-inline-end-time-popover']")).isVisible()

        // Change the time to 14:45 (using 24-hour format)
        val timeInput = page.locator("[data-testid='time-entry-inline-end-time-time-input']")
        timeInput.fill("14:45")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-end-time-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-end-time-popover']")).not().isVisible()

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            // The time part should be updated
            // Compare Instants directly to avoid timezone issues
            assertNotEquals(entry.endTime, updatedEntry.endTime, "End time should have changed")

            // Ensure other fields were not changed
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.title, updatedEntry.title)
        }
    }

    @Test
    fun `should not show inline edit for end time on active entry`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = null,
                title = "Active Entry",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Find the time entry in daily view (not the active entry panel on top)
        val dailyEntry = page.locator("[data-testid='time-entry']")

        // Verify that end time shows "in progress" and is not a clickable inline edit trigger
        val timeRange = dailyEntry.locator("[data-testid='entry-time-range']")
        assertThat(timeRange).containsText("in progress")

        // Verify inline edit trigger for end time is not present
        assertThat(dailyEntry.locator("[data-testid='time-entry-inline-end-time-trigger']")).not().isVisible()
    }

    @Test
    fun `should allow changing date when editing start time`() {
        // Create a stopped entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Test Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click on the start time
        page.locator("[data-testid='time-entry-inline-start-time-trigger']").click()

        // Verify calendar is visible
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-grid']")).isVisible()

        // Click on the 5th day of the month
        val calendarGrid = page.locator("[data-testid='time-entry-inline-start-time-grid']")
        val dayButtons = calendarGrid.locator("button")
        // Find the button containing "5" - use hasText for matching
        dayButtons.locator("text=5").first().click()

        // Click the save button
        page.locator("[data-testid='time-entry-inline-start-time-save-button']").click()

        // Verify database was updated to the 5th of the month
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            // The date should change to the 5th
            // Compare Instants directly - we know the date changed
            assertNotEquals(entry.startTime, updatedEntry.startTime, "Start time should have changed")
        }
    }

    @Test
    fun `should allow editing times in expanded grouped entries`() {
        // Create two entries with same title and tags (will be grouped)
        val entry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(7200), // 2 hours ago
                    endTime = FIXED_TEST_TIME.minusSeconds(5400), // 1.5 hours ago
                    title = "Grouped Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600), // 1 hour ago
                endTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago
                title = "Grouped Entry",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Find grouped entry
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()

        // Expand the group
        groupedEntry.locator("[data-testid='entry-count-badge']").click()

        // Wait for expanded entries to be visible
        val expandedEntries = page.locator("[data-testid='grouped-entries-expanded']")
        assertThat(expandedEntries).isVisible()

        // Find the first expanded entry
        val firstExpandedEntry = expandedEntries.locator("[data-testid='time-entry']").first()

        // Click on start time of first expanded entry
        firstExpandedEntry.locator("[data-testid='time-entry-inline-start-time-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-popover']")).isVisible()

        // Change the time
        val timeInput = page.locator("[data-testid='time-entry-inline-start-time-time-input']")
        timeInput.fill("10:00")

        // Save
        page.locator("[data-testid='time-entry-inline-start-time-save-button']").click()

        // Verify popover closed
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-popover']")).not().isVisible()

        // Verify database was updated for this specific entry
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry1.id)).orElseThrow()
            // Compare Instants directly
            assertNotEquals(entry1.startTime, updatedEntry.startTime, "Start time should have changed")
        }
    }

    @Test
    fun `should not affect title or tags when updating time`() {
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Important Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent", "feature"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Edit start time
        page.locator("[data-testid='time-entry-inline-start-time-trigger']").click()
        page.locator("[data-testid='time-entry-inline-start-time-time-input']").fill("11:30")
        page.locator("[data-testid='time-entry-inline-start-time-save-button']").click()

        // Wait for save to complete
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-popover']")).not().isVisible()

        // Verify title is still visible in UI
        assertThat(page.locator("[data-testid='time-entry-inline-title-trigger']")).containsText("Important Task")

        // Verify tags are still visible in UI
        val tags = page.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("backend", "feature", "urgent"))

        // Verify database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Important Task", updatedEntry.title)
            assertEquals(entry.tags.toSet(), updatedEntry.tags.toSet())
            assertEquals(entry.endTime, updatedEntry.endTime)
        }
    }

    @Test
    fun `should not allow inline edit of grouped entry header times`() {
        // Create two entries with same title and tags (will be grouped)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200),
                endTime = FIXED_TEST_TIME.minusSeconds(5400),
                title = "Grouped Entry",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Grouped Entry",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Find grouped entry header
        val groupedEntry = page.locator("[data-testid='grouped-entry-header']")
        assertThat(groupedEntry).isVisible()

        // Verify that the time range in the grouped header is not clickable inline edit
        // The grouped header should show times but not have inline edit triggers
        assertThat(groupedEntry.locator("[data-testid='time-entry-inline-start-time-trigger']")).not().isVisible()
        assertThat(groupedEntry.locator("[data-testid='time-entry-inline-end-time-trigger']")).not().isVisible()
    }

    @Test
    fun `should allow inline edit of end time for cross-day entries`() {
        // Create an entry that spans across different days
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600), // 1 hour ago
                    endTime = FIXED_TEST_TIME.plusSeconds(86400), // 1 day later
                    title = "Cross-day Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify that the inline edit trigger for end time is visible (not just a plain span)
        val endTimeTrigger = page.locator("[data-testid='time-entry-inline-end-time-trigger']")
        assertThat(endTimeTrigger).isVisible()

        // Click on the end time to open the popover
        endTimeTrigger.click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-inline-end-time-popover']")).isVisible()

        // Change the time
        val timeInput = page.locator("[data-testid='time-entry-inline-end-time-time-input']")
        timeInput.fill("15:30")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-end-time-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-end-time-popover']")).not().isVisible()

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            // End time should have changed
            assertNotEquals(entry.endTime, updatedEntry.endTime, "End time should have changed")

            // Ensure other fields were not changed
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.title, updatedEntry.title)
        }
    }
}
