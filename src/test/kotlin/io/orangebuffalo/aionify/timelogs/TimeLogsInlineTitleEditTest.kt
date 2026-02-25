package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.timeInTestTz
import io.orangebuffalo.aionify.withLocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for inline title editing functionality.
 * Verifies that users can edit titles inline using a popover.
 */
class TimeLogsInlineTitleEditTest : TimeLogsPageTestBase() {
    @Test
    fun `should allow inline edit of title on stopped entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click on the title to open the popover
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).isVisible()

        // Verify input has the current title
        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        assertThat(input).hasValue("Original Title")

        // Change the title
        input.fill("Updated Title")

        // Click the save button
        page.locator("[data-testid='time-entry-inline-title-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify title is updated in the UI
        assertThat(page.locator("[data-testid='time-entry-inline-title-trigger']")).containsText("Updated Title")

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Updated Title", updatedEntry.title)
            // Ensure other fields were not changed
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.endTime, updatedEntry.endTime)
            assertEquals(entry.tags.toSet(), updatedEntry.tags.toSet())
        }
    }

    @Test
    fun `should allow quick edit of title on active entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create an active entry
        val entry =
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

        // Click on the title
        dailyEntry.locator("[data-testid='time-entry-inline-title-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).isVisible()

        // Update title
        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("Updated Active Entry")

        // Save
        page.locator("[data-testid='time-entry-inline-title-save-button']").click()

        // Verify update in UI
        assertThat(dailyEntry.locator("[data-testid='time-entry-inline-title-trigger']")).containsText(
            "Updated Active Entry",
        )

        // Verify database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Updated Active Entry", updatedEntry.title)
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.endTime, updatedEntry.endTime)
        }
    }

    @Test
    fun `should allow inline edit of grouped entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create three entries with same title and tags
        val entry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("00:30"), // 3 hours ago
                    endTime = baseTime.withLocalTime("01:00"), // 2.5 hours ago
                    title = "Grouped Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry2 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("01:30"), // 2 hours ago
                    endTime = baseTime.withLocalTime("02:00"), // 1.5 hours ago
                    title = "Grouped Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry3 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"), // 1 hour ago
                    endTime = baseTime.withLocalTime("03:00"), // 30 minutes ago
                    title = "Grouped Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Find grouped entry
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()

        // Click on the title
        groupedEntry.locator("[data-testid='grouped-entry-inline-title-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='grouped-entry-inline-title-popover']")).isVisible()

        // Update title
        val input = page.locator("[data-testid='grouped-entry-inline-title-input']")
        input.fill("Updated Group Title")

        // Save
        page.locator("[data-testid='grouped-entry-inline-title-save-button']").click()

        // Verify popover closed
        assertThat(page.locator("[data-testid='grouped-entry-inline-title-popover']")).not().isVisible()

        // Verify UI updated
        assertThat(groupedEntry.locator("[data-testid='grouped-entry-inline-title-trigger']")).containsText(
            "Updated Group Title",
        )

        // Verify all entries in database were updated
        testDatabaseSupport.inTransaction {
            val updated1 = timeLogEntryRepository.findById(requireNotNull(entry1.id)).orElseThrow()
            val updated2 = timeLogEntryRepository.findById(requireNotNull(entry2.id)).orElseThrow()
            val updated3 = timeLogEntryRepository.findById(requireNotNull(entry3.id)).orElseThrow()

            assertEquals("Updated Group Title", updated1.title)
            assertEquals("Updated Group Title", updated2.title)
            assertEquals("Updated Group Title", updated3.title)

            // Ensure other fields were not changed
            assertEquals(entry1.startTime, updated1.startTime)
            assertEquals(entry1.endTime, updated1.endTime)
            assertEquals(entry1.tags.toSet(), updated1.tags.toSet())

            assertEquals(entry2.startTime, updated2.startTime)
            assertEquals(entry2.endTime, updated2.endTime)
            assertEquals(entry2.tags.toSet(), updated2.tags.toSet())

            assertEquals(entry3.startTime, updated3.startTime)
            assertEquals(entry3.endTime, updated3.endTime)
            assertEquals(entry3.tags.toSet(), updated3.tags.toSet())
        }
    }

    @Test
    fun `should close popover when pressing Escape key`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

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

        // Open popover
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).isVisible()

        // Press Escape
        page.keyboard().press("Escape")

        // Verify popover closed
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()
    }

    @Test
    fun `should save title when pressing Enter key`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).isVisible()

        // Update title and press Enter
        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("Title via Enter")
        input.press("Enter")

        // Verify popover closed
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify title updated
        assertThat(page.locator("[data-testid='time-entry-inline-title-trigger']")).containsText("Title via Enter")

        // Verify database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Title via Enter", updatedEntry.title)
        }
    }

    @Test
    fun `should disable save button when title is empty`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Original Title",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()

        // Clear the title
        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        input.fill("")

        // Verify save button is disabled
        assertThat(page.locator("[data-testid='time-entry-inline-title-save-button']")).isDisabled()
    }

    @Test
    fun `should disable save button when title exceeds max length`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Original Title",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()

        // Enter a title that's too long (over 1000 characters)
        val input = page.locator("[data-testid='time-entry-inline-title-input']")
        val longTitle = "a".repeat(1001)
        input.fill(longTitle)

        // Verify save button is disabled
        assertThat(page.locator("[data-testid='time-entry-inline-title-save-button']")).isDisabled()
    }

    @Test
    fun `should not affect tags when updating title`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent", "feature"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Inline edit title
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()
        page.locator("[data-testid='time-entry-inline-title-input']").fill("Updated Title")
        page.locator("[data-testid='time-entry-inline-title-save-button']").click()

        // Wait for save to complete
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify tags are still indicated in UI (button shows Tags icon)
        val hasTagsAttr = page.locator("[data-testid='time-entry-inline-tags-button']").getAttribute("data-has-tags") ?: "false"
        assertTrue(hasTagsAttr == "true", "Tag button should show tags icon when tags are selected")

        // Verify tags in database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Updated Title", updatedEntry.title)
            assertEquals(entry.tags.toSet(), updatedEntry.tags.toSet())
        }
    }

    @Test
    fun `should trim whitespace from title when saving`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Inline edit with whitespace
        page.locator("[data-testid='time-entry-inline-title-trigger']").click()
        page.locator("[data-testid='time-entry-inline-title-input']").fill("  Trimmed Title  ")
        page.locator("[data-testid='time-entry-inline-title-save-button']").click()

        // Wait for save
        assertThat(page.locator("[data-testid='time-entry-inline-title-popover']")).not().isVisible()

        // Verify title was trimmed in database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Trimmed Title", updatedEntry.title)
        }
    }
}
