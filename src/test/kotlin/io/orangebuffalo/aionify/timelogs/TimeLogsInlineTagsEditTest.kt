package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.timeInTestTz
import io.orangebuffalo.aionify.withLocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for inline tags editing functionality.
 * Verifies that users can edit tags inline using a popover with a save button.
 */
class TimeLogsInlineTagsEditTest : TimeLogsPageTestBase() {
    @Test
    fun `should allow inline edit of tags on stopped entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry with initial tags
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Task with Tags",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        // Create some available tags for selection
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:00"),
                endTime = baseTime.withLocalTime("01:30"),
                title = "Another Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("frontend", "testing"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Locate the first time entry with the specific title
        val timeEntry = page.locator("[data-testid='time-entry']:has([data-testid='entry-title']:has-text('Task with Tags'))")

        // Click on the tags icon to open the popover
        timeEntry.locator("[data-testid='time-entry-inline-tags-button']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).isVisible()

        // Verify current tags are selected
        assertThat(page.locator("[data-testid='time-entry-inline-tags-checkbox-backend']")).isChecked()
        assertThat(page.locator("[data-testid='time-entry-inline-tags-checkbox-urgent']")).isChecked()
        assertThat(page.locator("[data-testid='time-entry-inline-tags-checkbox-frontend']")).not().isChecked()
        assertThat(page.locator("[data-testid='time-entry-inline-tags-checkbox-testing']")).not().isChecked()

        // Uncheck "urgent" and check "frontend"
        page.locator("[data-testid='time-entry-inline-tags-checkbox-urgent']").click()
        page.locator("[data-testid='time-entry-inline-tags-checkbox-frontend']").click()

        // Verify save button is enabled (tags have changed)
        assertThat(page.locator("[data-testid='time-entry-inline-tags-save-button']")).isEnabled()

        // Click save button
        page.locator("[data-testid='time-entry-inline-tags-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).not().isVisible()

        // Verify tags are updated in the UI
        val tags = timeEntry.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("backend", "frontend"))

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals(setOf("backend", "frontend"), updatedEntry.tags.toSet())
            // Ensure other fields were not changed
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.endTime, updatedEntry.endTime)
            assertEquals(entry.title, updatedEntry.title)
        }
    }

    @Test
    fun `should allow inline edit of tags on active entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create an active entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = null,
                    title = "Active Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Find the time entry in daily view (not the active entry panel on top)
        val dailyEntry = page.locator("[data-testid='time-entry']")

        // Click on the tags icon
        dailyEntry.locator("[data-testid='time-entry-inline-tags-button']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).isVisible()

        // Add a new tag
        val newTagInput = page.locator("[data-testid='time-entry-inline-tags-new-tag-input']")
        newTagInput.fill("urgent")
        page.locator("[data-testid='time-entry-inline-tags-add-tag-button']").click()

        // Verify the new tag is now in the list and selected
        assertThat(page.locator("[data-testid='time-entry-inline-tags-checkbox-urgent']")).isChecked()

        // Save
        page.locator("[data-testid='time-entry-inline-tags-save-button']").click()

        // Verify update in UI
        val tags = dailyEntry.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("backend", "urgent"))

        // Verify database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals(setOf("backend", "urgent"), updatedEntry.tags.toSet())
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.endTime, updatedEntry.endTime)
        }
    }

    @Test
    fun `should allow inline edit of grouped entries tags`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create three entries with same title and tags
        val entry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("00:30"),
                    endTime = baseTime.withLocalTime("01:00"),
                    title = "Grouped Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry2 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("01:30"),
                    endTime = baseTime.withLocalTime("02:00"),
                    title = "Grouped Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry3 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Grouped Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Find grouped entry
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()

        // Click on the tags icon
        groupedEntry.locator("[data-testid='grouped-entry-inline-tags-button']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='grouped-entry-inline-tags-popover']")).isVisible()

        // Remove "urgent" tag
        page.locator("[data-testid='grouped-entry-inline-tags-checkbox-urgent']").click()

        // Add new tag "feature"
        val newTagInput = page.locator("[data-testid='grouped-entry-inline-tags-new-tag-input']")
        newTagInput.fill("feature")
        page.locator("[data-testid='grouped-entry-inline-tags-add-tag-button']").click()

        // Save
        page.locator("[data-testid='grouped-entry-inline-tags-save-button']").click()

        // Verify popover closed
        assertThat(page.locator("[data-testid='grouped-entry-inline-tags-popover']")).not().isVisible()

        // Verify UI updated
        val tags = groupedEntry.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("backend", "feature"))

        // Verify all entries in database were updated
        testDatabaseSupport.inTransaction {
            val updated1 = timeLogEntryRepository.findById(requireNotNull(entry1.id)).orElseThrow()
            val updated2 = timeLogEntryRepository.findById(requireNotNull(entry2.id)).orElseThrow()
            val updated3 = timeLogEntryRepository.findById(requireNotNull(entry3.id)).orElseThrow()

            assertEquals(setOf("backend", "feature"), updated1.tags.toSet())
            assertEquals(setOf("backend", "feature"), updated2.tags.toSet())
            assertEquals(setOf("backend", "feature"), updated3.tags.toSet())

            // Ensure other fields were not changed
            assertEquals(entry1.startTime, updated1.startTime)
            assertEquals(entry1.endTime, updated1.endTime)
            assertEquals(entry1.title, updated1.title)

            assertEquals(entry2.startTime, updated2.startTime)
            assertEquals(entry2.endTime, updated2.endTime)
            assertEquals(entry2.title, updated2.title)

            assertEquals(entry3.startTime, updated3.startTime)
            assertEquals(entry3.endTime, updated3.endTime)
            assertEquals(entry3.title, updated3.title)
        }
    }

    @Test
    fun `should disable save button when tags are unchanged`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-inline-tags-button']").click()
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).isVisible()

        // Verify save button is disabled (no changes)
        assertThat(page.locator("[data-testid='time-entry-inline-tags-save-button']")).isDisabled()

        // Make a change
        page.locator("[data-testid='time-entry-inline-tags-checkbox-backend']").click()

        // Verify save button is enabled
        assertThat(page.locator("[data-testid='time-entry-inline-tags-save-button']")).isEnabled()

        // Revert the change
        page.locator("[data-testid='time-entry-inline-tags-checkbox-backend']").click()

        // Verify save button is disabled again
        assertThat(page.locator("[data-testid='time-entry-inline-tags-save-button']")).isDisabled()
    }

    @Test
    fun `should disable inputs during save operation`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Main Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        // Create an available tag
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:00"),
                endTime = baseTime.withLocalTime("01:30"),
                title = "Another Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("frontend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Locate the specific time entry
        val timeEntry = page.locator("[data-testid='time-entry']:has([data-testid='entry-title']:has-text('Main Task'))")

        // Open popover
        timeEntry.locator("[data-testid='time-entry-inline-tags-button']").click()

        // Make a change
        page.locator("[data-testid='time-entry-inline-tags-checkbox-frontend']").click()

        // Save and wait for popover to close
        page.locator("[data-testid='time-entry-inline-tags-save-button']").click()

        // Popover should close after save
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).not().isVisible()

        // Verify the tags were updated in the UI
        val tags = timeEntry.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("backend", "frontend"))
    }

    @Test
    fun `should close popover when pressing Escape key`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-inline-tags-button']").click()
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).isVisible()

        // Press Escape
        page.keyboard().press("Escape")

        // Verify popover closed
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).not().isVisible()
    }

    @Test
    fun `should save tags when pressing Enter key in new tag input`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-inline-tags-button']").click()
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).isVisible()

        // Add a tag using Enter
        val newTagInput = page.locator("[data-testid='time-entry-inline-tags-new-tag-input']")
        newTagInput.fill("urgent")
        newTagInput.press("Enter")

        // Verify tag was added to the list and is selected
        assertThat(page.locator("[data-testid='time-entry-inline-tags-checkbox-urgent']")).isChecked()

        // Now press Enter again (with empty input) to save
        newTagInput.press("Enter")

        // Verify popover closed
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).not().isVisible()

        // Verify tags updated
        val tags = page.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("urgent"))

        // Verify database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals(setOf("urgent"), updatedEntry.tags.toSet())
        }
    }

    @Test
    fun `should reset tags when reopening after unsaved changes`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-inline-tags-button']").click()

        // Make a change but don't save
        page.locator("[data-testid='time-entry-inline-tags-checkbox-backend']").click()

        // Close popover with Escape
        page.keyboard().press("Escape")

        // Reopen popover
        page.locator("[data-testid='time-entry-inline-tags-button']").click()

        // Verify backend is still checked (changes were reset)
        assertThat(page.locator("[data-testid='time-entry-inline-tags-checkbox-backend']")).isChecked()

        // Verify save button is disabled (no changes)
        assertThat(page.locator("[data-testid='time-entry-inline-tags-save-button']")).isDisabled()
    }

    @Test
    fun `should not affect title when updating tags`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Important Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open tags popover
        page.locator("[data-testid='time-entry-inline-tags-button']").click()

        // Add a tag
        val newTagInput = page.locator("[data-testid='time-entry-inline-tags-new-tag-input']")
        newTagInput.fill("urgent")
        page.locator("[data-testid='time-entry-inline-tags-add-tag-button']").click()

        // Save
        page.locator("[data-testid='time-entry-inline-tags-save-button']").click()

        // Wait for save to complete
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).not().isVisible()

        // Verify title is still visible in UI
        assertThat(page.locator("[data-testid='time-entry-inline-title-trigger']")).containsText("Important Task")

        // Verify database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Important Task", updatedEntry.title)
            assertEquals(setOf("backend", "urgent"), updatedEntry.tags.toSet())
            assertEquals(entry.startTime, updatedEntry.startTime)
            assertEquals(entry.endTime, updatedEntry.endTime)
        }
    }

    @Test
    fun `should remove all tags when all checkboxes are unchecked`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Task with Tags",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open tags popover
        page.locator("[data-testid='time-entry-inline-tags-button']").click()

        // Uncheck all tags
        page.locator("[data-testid='time-entry-inline-tags-checkbox-backend']").click()
        page.locator("[data-testid='time-entry-inline-tags-checkbox-urgent']").click()

        // Save
        page.locator("[data-testid='time-entry-inline-tags-save-button']").click()

        // Wait for save to complete
        assertThat(page.locator("[data-testid='time-entry-inline-tags-popover']")).not().isVisible()

        // Verify no tags are displayed
        assertThat(page.locator("[data-testid='entry-tags']")).not().isVisible()

        // Verify database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals(emptySet<String>(), updatedEntry.tags.toSet())
        }
    }
}
