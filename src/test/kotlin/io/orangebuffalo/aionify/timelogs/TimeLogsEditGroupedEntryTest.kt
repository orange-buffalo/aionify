package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for editing grouped time log entries.
 * Verifies that users can edit title and tags of all entries in a group at once.
 */
class TimeLogsEditGroupedEntryTest : TimeLogsPageTestBase() {
    @Test
    fun `should allow editing title and tags of grouped entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create three entries with same title and tags
        val entry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("00:30"), // 3 hours ago
                    endTime = baseTime.withLocalTime("01:30").minusMinutes(30), // 2.5 hours ago
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry2 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("01:30"), // 2 hours ago
                    endTime = baseTime.withLocalTime("02:30").minusMinutes(30), // 1.5 hours ago
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry3 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"), // 1 hour ago
                    endTime = baseTime.withLocalTime("03:00"), // 30 minutes ago
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify grouped entry is visible
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()
        assertThat(groupedEntry.locator("[data-testid='entry-title']")).containsText("Original Title")

        // Click edit button on the grouped entry
        groupedEntry.locator("[data-testid='edit-grouped-entry-button']").click()

        // Verify edit form is visible with current values
        assertThat(page.locator("[data-testid='edit-grouped-entry-title-input']")).isVisible()
        assertThat(page.locator("[data-testid='edit-grouped-entry-title-input']")).hasValue("Original Title")

        // Edit the title
        page.locator("[data-testid='edit-grouped-entry-title-input']").fill("Updated Title")

        // Remove "urgent" tag - open the tag selector
        page.locator("[data-testid='edit-grouped-entry-tags-button']").click()

        // Wait for popover to appear
        assertThat(page.locator("[data-testid='edit-grouped-entry-tags-popover']")).isVisible()

        // Uncheck "urgent" tag
        page.locator("[data-testid='edit-grouped-entry-tags-checkbox-urgent']").click()

        // Add "frontend" tag by typing in the input
        page.locator("[data-testid='edit-grouped-entry-tags-new-tag-input']").fill("frontend")
        page.locator("[data-testid='edit-grouped-entry-tags-new-tag-input']").press("Enter")

        // Close popover
        page.locator("[data-testid='edit-grouped-entry-tags-button']").click()

        // Save the changes
        page.locator("[data-testid='save-edit-grouped-entry-button']").click()

        // Verify form is closed
        assertThat(page.locator("[data-testid='edit-grouped-entry-title-input']")).not().isVisible()

        // Verify the grouped entry shows updated values
        assertThat(groupedEntry.locator("[data-testid='entry-title']")).containsText("Updated Title")

        // Verify tags are updated
        val tags = groupedEntry.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("backend", "frontend"))

        // Ensure request is processed before checking the database
        assertThat(page.locator("[data-testid='edit-grouped-entry-title-input']")).not().isVisible()

        // Verify all entries in the database were updated
        testDatabaseSupport.inTransaction {
            val updatedEntry1 = timeLogEntryRepository.findById(requireNotNull(entry1.id)).orElseThrow()
            val updatedEntry2 = timeLogEntryRepository.findById(requireNotNull(entry2.id)).orElseThrow()
            val updatedEntry3 = timeLogEntryRepository.findById(requireNotNull(entry3.id)).orElseThrow()

            // Verify title was updated for all entries
            assertEquals("Updated Title", updatedEntry1.title)
            assertEquals("Updated Title", updatedEntry2.title)
            assertEquals("Updated Title", updatedEntry3.title)

            // Verify tags were updated for all entries
            assertEquals(setOf("backend", "frontend"), updatedEntry1.tags.toSet())
            assertEquals(setOf("backend", "frontend"), updatedEntry2.tags.toSet())
            assertEquals(setOf("backend", "frontend"), updatedEntry3.tags.toSet())

            // CRITICAL: Verify start and end times were NOT changed
            assertEquals(entry1.startTime, updatedEntry1.startTime)
            assertEquals(entry1.endTime, updatedEntry1.endTime)
            assertEquals(entry2.startTime, updatedEntry2.startTime)
            assertEquals(entry2.endTime, updatedEntry2.endTime)
            assertEquals(entry3.startTime, updatedEntry3.startTime)
            assertEquals(entry3.endTime, updatedEntry3.endTime)
        }
    }

    @Test
    fun `should only update entries in the edited group`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a group of 2 entries with same title and tags
        val groupEntry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("01:30"),
                    endTime = baseTime.withLocalTime("02:30").minusMinutes(30),
                    title = "Group A",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        val groupEntry2 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Group A",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        // Create a separate entry that should NOT be updated
        val separateEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("03:15"),
                    endTime = baseTime.withLocalTime("03:25"),
                    title = "Different Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("frontend"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Find and edit the grouped entry
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        groupedEntry.locator("[data-testid='edit-grouped-entry-button']").click()

        // Change title
        page.locator("[data-testid='edit-grouped-entry-title-input']").fill("Group A Updated")

        // Save
        page.locator("[data-testid='save-edit-grouped-entry-button']").click()

        // Wait for edit form to close (indicating save completed)
        assertThat(page.locator("[data-testid='edit-grouped-entry-form']")).not().isVisible()

        // Verify only the grouped entries were updated
        testDatabaseSupport.inTransaction {
            val updated1 = timeLogEntryRepository.findById(requireNotNull(groupEntry1.id)).orElseThrow()
            val updated2 = timeLogEntryRepository.findById(requireNotNull(groupEntry2.id)).orElseThrow()
            val notUpdated = timeLogEntryRepository.findById(requireNotNull(separateEntry.id)).orElseThrow()

            // Grouped entries should be updated
            assertEquals("Group A Updated", updated1.title)
            assertEquals("Group A Updated", updated2.title)

            // Separate entry should NOT be changed
            assertEquals("Different Entry", notUpdated.title)
        }
    }

    @Test
    fun `should cancel editing grouped entry without saving changes`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create grouped entries
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30"),
                endTime = baseTime.withLocalTime("02:30").minusMinutes(30),
                title = "Testing",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("qa"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Testing",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("qa"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")

        // Start editing
        groupedEntry.locator("[data-testid='edit-grouped-entry-button']").click()

        // Make changes
        page.locator("[data-testid='edit-grouped-entry-title-input']").fill("Changed Title")

        // Cancel
        page.locator("[data-testid='cancel-edit-grouped-entry-button']").click()

        // Verify edit form is hidden
        assertThat(page.locator("[data-testid='edit-grouped-entry-title-input']")).not().isVisible()

        // Verify original title is still shown
        assertThat(groupedEntry.locator("[data-testid='entry-title']")).containsText("Testing")
    }

    @Test
    fun `should not show edit button on ungrouped entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a single entry (not grouped)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Single Entry",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify regular entry does not have grouped edit button
        val regularEntry = page.locator("[data-testid='time-entry']")
        assertThat(regularEntry).isVisible()
        assertThat(regularEntry.locator("[data-testid='edit-grouped-entry-button']")).not().isVisible()

        // Verify regular entry still has its own edit functionality via menu
        regularEntry.locator("[data-testid='entry-menu-button']").click()
        assertThat(page.locator("[data-testid='edit-menu-item']")).isVisible()
    }

    @Test
    fun `should not allow editing grouped entry with blank title`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create grouped entries
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30"),
                endTime = baseTime.withLocalTime("02:30").minusMinutes(30),
                title = "Valid Title",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Valid Title",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        groupedEntry.locator("[data-testid='edit-grouped-entry-button']").click()

        // Try to set blank title
        page.locator("[data-testid='edit-grouped-entry-title-input']").fill("")

        // Save button should be disabled
        assertThat(page.locator("[data-testid='save-edit-grouped-entry-button']")).isDisabled()
    }
}
