package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

        // Change the time to 02:45 (in Pacific/Auckland local time, between start 02:30 and end 03:00)
        val timeInput = page.locator("[data-testid='time-entry-inline-start-time-time-input']")
        timeInput.fill("02:45")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-start-time-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-popover']")).not().isVisible()

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            // The browser runs in Pacific/Auckland timezone (UTC+13)
            // Original entry: 2024-03-15T13:30:00Z = 2024-03-16 02:30 local
            // User enters "02:45" local time = 2024-03-16 02:45 Pacific/Auckland
            // Expected UTC: 2024-03-15T13:45:00Z (02:45 - 13 hours)
            val expectedStartTime = Instant.parse("2024-03-15T13:45:00Z")
            assertEquals(expectedStartTime, updatedEntry.startTime, "Start time should be updated to 02:45 local (13:45 UTC)")

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

        // Change the time to 03:15 (in Pacific/Auckland, after the original 03:00 end time)
        val timeInput = page.locator("[data-testid='time-entry-inline-end-time-time-input']")
        timeInput.fill("03:15")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-end-time-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-end-time-popover']")).not().isVisible()

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            // The browser runs in Pacific/Auckland timezone (UTC+13)
            // Original entry end: 2024-03-15T14:00:00Z = 2024-03-16 03:00 local
            // User enters "03:15" local time = 2024-03-16 03:15 Pacific/Auckland
            // Expected UTC: 2024-03-15T14:15:00Z (03:15 - 13 hours)
            val expectedEndTime = Instant.parse("2024-03-15T14:15:00Z")
            assertEquals(expectedEndTime, updatedEntry.endTime, "End time should be updated to 03:15 local (14:15 UTC)")

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

        // Verify database was updated to the 5th of March
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            // The browser runs in Pacific/Auckland timezone (UTC+13)
            // Original: 2024-03-16 02:30 local = 2024-03-15T13:30:00Z
            // User clicks day "5" in calendar = March 5, same time 02:30 local
            // Expected UTC: 2024-03-04T13:30:00Z (March 5, 02:30 local - 13 hours)
            val expectedStartTime = Instant.parse("2024-03-04T13:30:00Z")
            assertEquals(expectedStartTime, updatedEntry.startTime, "Start time should be 5th March local at 02:30 (4th March 13:30 UTC)")
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
        timeInput.fill("01:45")

        // Save
        page.locator("[data-testid='time-entry-inline-start-time-save-button']").click()

        // Verify popover closed
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-popover']")).not().isVisible()

        // Verify database was updated for the first entry in the list
        testDatabaseSupport.inTransaction {
            // The first visible entry should be the most recent one (entry2)
            // Find which entry was updated by checking for the new time
            // User fills "01:45" local = March 16, 01:45 Pacific/Auckland
            // Expected UTC: 2024-03-15T12:45:00Z (01:45 - 13 hours)
            val entries = timeLogEntryRepository.findAll()
            val updatedEntry =
                entries.find { it.startTime == Instant.parse("2024-03-15T12:45:00Z") }
            assertNotNull(updatedEntry, "One entry should have start time updated to 01:45 local (12:45 UTC)")
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
        page.locator("[data-testid='time-entry-inline-start-time-time-input']").fill("02:40")
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
            // Verify time was updated (02:40 local = 13:40 UTC)
            assertEquals(Instant.parse("2024-03-15T13:40:00Z"), updatedEntry.startTime)
            // Verify title and tags were not changed
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
        timeInput.fill("04:00")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-end-time-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-end-time-popover']")).not().isVisible()

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            // The browser runs in Pacific/Auckland timezone (UTC+13)
            // Original: 2024-03-16T14:30:00Z = 2024-03-17 03:30 local (cross-day)
            // User enters "04:00" local time = 2024-03-17 04:00 Pacific/Auckland
            // Expected UTC: 2024-03-16T15:00:00Z (04:00 - 13 hours)
            val expectedEndTime = Instant.parse("2024-03-16T15:00:00Z")
            assertEquals(expectedEndTime, updatedEntry.endTime, "End time should be updated to 04:00 local (15:00 UTC)")

            // Ensure other fields were not changed
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.title, updatedEntry.title)
        }
    }
}
