package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.timeInTestTz
import io.orangebuffalo.aionify.withLocalDate
import io.orangebuffalo.aionify.withLocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Tests for inline time editing functionality.
 * Verifies that users can edit start and end times inline using a popover.
 */
class TimeLogsInlineTimeEditTest : TimeLogsPageTestBase() {
    @Test
    fun `should allow inline edit of start time on stopped entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
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

        // Change the time to 02:45
        val timeInput = page.locator("[data-testid='time-entry-inline-start-time-time-input']")
        timeInput.fill("02:45")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-start-time-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-start-time-popover']")).not().isVisible()

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            val expectedStartTime = timeInTestTz("2024-03-16", "02:45")
            assertEquals(expectedStartTime, updatedEntry.startTime, "Start time should be updated to 02:45 local")

            // Ensure other fields were not changed
            assertEquals(entry.endTime, updatedEntry.endTime)
            assertEquals(entry.title, updatedEntry.title)
        }
    }

    @Test
    fun `should allow inline edit of end time on stopped entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
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

        // Change the time to 03:15
        val timeInput = page.locator("[data-testid='time-entry-inline-end-time-time-input']")
        timeInput.fill("03:15")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-end-time-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-end-time-popover']")).not().isVisible()

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            val expectedEndTime = timeInTestTz("2024-03-16", "03:15")
            assertEquals(expectedEndTime, updatedEntry.endTime, "End time should be updated to 03:15 local")

            // Ensure other fields were not changed
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.title, updatedEntry.title)
        }
    }

    @Test
    fun `should not show inline edit for end time on active entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
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
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
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
            // User clicks day "5" in calendar = March 5, same time 02:30 local
            val expectedStartTime = timeInTestTz("2024-03-05", "02:30")
            assertEquals(expectedStartTime, updatedEntry.startTime, "Start time should be 5th March at 02:30 local")
        }
    }

    @Test
    fun `should allow editing times in expanded grouped entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create two entries with same title and tags (will be grouped)
        val entry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("01:30"),
                    endTime = baseTime.withLocalTime("02:00"),
                    title = "Grouped Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
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
            val entries = timeLogEntryRepository.findAll()
            val updatedEntry =
                entries.find { it.startTime == timeInTestTz("2024-03-16", "01:45") }
            assertNotNull(updatedEntry, "One entry should have start time updated to 01:45 local")
        }
    }

    @Test
    fun `should not affect title or tags when updating time`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
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
            // Verify time was updated to 02:40 local
            assertEquals(timeInTestTz("2024-03-16", "02:40"), updatedEntry.startTime)
            // Verify title and tags were not changed
            assertEquals("Important Task", updatedEntry.title)
            assertEquals(entry.tags.toSet(), updatedEntry.tags.toSet())
            assertEquals(entry.endTime, updatedEntry.endTime)
        }
    }

    @Test
    fun `should not allow inline edit of grouped entry header times`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create two entries with same title and tags (will be grouped)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30"),
                endTime = baseTime.withLocalTime("02:00"),
                title = "Grouped Entry",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
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
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create an entry that spans across different days
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalDate("2024-03-17"),
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
            // User enters "04:00" local time = 2024-03-17 04:00 Pacific/Auckland
            val expectedEndTime = timeInTestTz("2024-03-17", "04:00")
            assertEquals(expectedEndTime, updatedEntry.endTime, "End time should be updated to 04:00 local")

            // Ensure other fields were not changed
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.title, updatedEntry.title)
        }
    }
}
