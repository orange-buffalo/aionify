package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

/**
 * Tests for editing grouped time log entries.
 * Verifies that users can edit title and tags of all entries in a group at once.
 */
class TimeLogsEditGroupedEntryTest : TimeLogsPageTestBase() {
    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

    @Test
    fun `should allow editing title and tags of grouped entries`() {
        // Create three entries with same title and tags
        val entry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(10800), // 3 hours ago
                    endTime = FIXED_TEST_TIME.minusSeconds(9000), // 2.5 hours ago
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry2 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(7200), // 2 hours ago
                    endTime = FIXED_TEST_TIME.minusSeconds(5400), // 1.5 hours ago
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry3 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600), // 1 hour ago
                    endTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago
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

        // Verify tags are shown
        assertThat(page.locator("[data-testid='edit-grouped-entry-tags-selected-tag-backend']")).isVisible()
        assertThat(page.locator("[data-testid='edit-grouped-entry-tags-selected-tag-urgent']")).isVisible()

        // Edit the title
        page.locator("[data-testid='edit-grouped-entry-title-input']").fill("Updated Title")

        // Remove "urgent" tag
        page.locator("[data-testid='edit-grouped-entry-tags-selected-tag-urgent']").click()

        // Add "frontend" tag
        page.locator("[data-testid='edit-grouped-entry-tags-button']").click()
        page.locator("[data-testid='edit-grouped-entry-tags-input']").fill("frontend")
        page.locator("[data-testid='edit-grouped-entry-tags-input']").press("Enter")

        // Save the changes
        page.locator("[data-testid='save-edit-grouped-entry-button']").click()

        // Verify form is closed
        assertThat(page.locator("[data-testid='edit-grouped-entry-title-input']")).not().isVisible()

        // Verify the grouped entry shows updated values
        assertThat(groupedEntry.locator("[data-testid='entry-title']")).containsText("Updated Title")

        // Verify tags are updated
        val tags = groupedEntry.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("backend", "frontend"))

        // Verify all entries in the database were updated
        testDatabaseSupport.inTransaction {
            val updatedEntry1 = timeLogEntryRepository.findById(requireNotNull(entry1.id)).orElseThrow()
            val updatedEntry2 = timeLogEntryRepository.findById(requireNotNull(entry2.id)).orElseThrow()
            val updatedEntry3 = timeLogEntryRepository.findById(requireNotNull(entry3.id)).orElseThrow()

            // Verify title was updated for all entries
            assertThat(updatedEntry1.title).isEqualTo("Updated Title")
            assertThat(updatedEntry2.title).isEqualTo("Updated Title")
            assertThat(updatedEntry3.title).isEqualTo("Updated Title")

            // Verify tags were updated for all entries
            assertThat(updatedEntry1.tags.toSet()).isEqualTo(setOf("backend", "frontend"))
            assertThat(updatedEntry2.tags.toSet()).isEqualTo(setOf("backend", "frontend"))
            assertThat(updatedEntry3.tags.toSet()).isEqualTo(setOf("backend", "frontend"))

            // CRITICAL: Verify start and end times were NOT changed
            assertThat(updatedEntry1.startTime).isEqualTo(entry1.startTime)
            assertThat(updatedEntry1.endTime).isEqualTo(entry1.endTime)
            assertThat(updatedEntry2.startTime).isEqualTo(entry2.startTime)
            assertThat(updatedEntry2.endTime).isEqualTo(entry2.endTime)
            assertThat(updatedEntry3.startTime).isEqualTo(entry3.startTime)
            assertThat(updatedEntry3.endTime).isEqualTo(entry3.endTime)
        }
    }

    @Test
    fun `should only update entries in the edited group`() {
        // Create a group of 2 entries with same title and tags
        val groupEntry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(7200),
                    endTime = FIXED_TEST_TIME.minusSeconds(5400),
                    title = "Group A",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        val groupEntry2 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Group A",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        // Create a separate entry that should NOT be updated
        val separateEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(900),
                    endTime = FIXED_TEST_TIME.minusSeconds(300),
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

        // Verify only the grouped entries were updated
        testDatabaseSupport.inTransaction {
            val updated1 = timeLogEntryRepository.findById(requireNotNull(groupEntry1.id)).orElseThrow()
            val updated2 = timeLogEntryRepository.findById(requireNotNull(groupEntry2.id)).orElseThrow()
            val notUpdated = timeLogEntryRepository.findById(requireNotNull(separateEntry.id)).orElseThrow()

            // Grouped entries should be updated
            assertThat(updated1.title).isEqualTo("Group A Updated")
            assertThat(updated2.title).isEqualTo("Group A Updated")

            // Separate entry should NOT be changed
            assertThat(notUpdated.title).isEqualTo("Different Entry")
        }
    }

    @Test
    fun `should cancel editing grouped entry without saving changes`() {
        // Create grouped entries
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200),
                endTime = FIXED_TEST_TIME.minusSeconds(5400),
                title = "Testing",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("qa"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
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
        // Create a single entry (not grouped)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
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
        // Create grouped entries
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200),
                endTime = FIXED_TEST_TIME.minusSeconds(5400),
                title = "Valid Title",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
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
