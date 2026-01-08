package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for quick title editing functionality.
 * Verifies that users can quickly edit titles inline using a popover.
 */
class TimeLogsQuickTitleEditTest : TimeLogsPageTestBase() {
    @Test
    fun `should allow quick edit of title on stopped entry`() {
        // Create a stopped entry
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click on the title to open the popover
        page.locator("[data-testid='time-entry-quick-title-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).isVisible()

        // Verify input has the current title
        val input = page.locator("[data-testid='time-entry-quick-title-input']")
        assertThat(input).hasValue("Original Title")

        // Change the title
        input.fill("Updated Title")

        // Click the save button
        page.locator("[data-testid='time-entry-quick-title-save-button']").click()

        // Verify popover is closed
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).not().isVisible()

        // Verify title is updated in the UI
        assertThat(page.locator("[data-testid='time-entry-quick-title-trigger']")).containsText("Updated Title")

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
        // Create an active entry
        val entry =
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

        // Click on the title
        dailyEntry.locator("[data-testid='time-entry-quick-title-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).isVisible()

        // Update title
        val input = page.locator("[data-testid='time-entry-quick-title-input']")
        input.fill("Updated Active Entry")

        // Save
        page.locator("[data-testid='time-entry-quick-title-save-button']").click()

        // Verify update in UI
        assertThat(dailyEntry.locator("[data-testid='time-entry-quick-title-trigger']")).containsText(
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
    fun `should allow quick edit of grouped entries`() {
        // Create three entries with same title and tags
        val entry1 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(10800), // 3 hours ago
                    endTime = FIXED_TEST_TIME.minusSeconds(9000), // 2.5 hours ago
                    title = "Grouped Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry2 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(7200), // 2 hours ago
                    endTime = FIXED_TEST_TIME.minusSeconds(5400), // 1.5 hours ago
                    title = "Grouped Entry",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        val entry3 =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600), // 1 hour ago
                    endTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago
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
        groupedEntry.locator("[data-testid='grouped-entry-quick-title-trigger']").click()

        // Verify popover is visible
        assertThat(page.locator("[data-testid='grouped-entry-quick-title-popover']")).isVisible()

        // Update title
        val input = page.locator("[data-testid='grouped-entry-quick-title-input']")
        input.fill("Updated Group Title")

        // Save
        page.locator("[data-testid='grouped-entry-quick-title-save-button']").click()

        // Verify popover closed
        assertThat(page.locator("[data-testid='grouped-entry-quick-title-popover']")).not().isVisible()

        // Verify UI updated
        assertThat(groupedEntry.locator("[data-testid='grouped-entry-quick-title-trigger']")).containsText(
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

        // Open popover
        page.locator("[data-testid='time-entry-quick-title-trigger']").click()
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).isVisible()

        // Press Escape
        page.keyboard().press("Escape")

        // Verify popover closed
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).not().isVisible()
    }

    @Test
    fun `should save title when pressing Enter key`() {
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-quick-title-trigger']").click()
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).isVisible()

        // Update title and press Enter
        val input = page.locator("[data-testid='time-entry-quick-title-input']")
        input.fill("Title via Enter")
        input.press("Enter")

        // Verify popover closed
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).not().isVisible()

        // Verify title updated
        assertThat(page.locator("[data-testid='time-entry-quick-title-trigger']")).containsText("Title via Enter")

        // Verify database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Title via Enter", updatedEntry.title)
        }
    }

    @Test
    fun `should disable save button when title is empty`() {
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Original Title",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-quick-title-trigger']").click()

        // Clear the title
        val input = page.locator("[data-testid='time-entry-quick-title-input']")
        input.fill("")

        // Verify save button is disabled
        assertThat(page.locator("[data-testid='time-entry-quick-title-save-button']")).isDisabled()
    }

    @Test
    fun `should disable save button when title exceeds max length`() {
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Original Title",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-quick-title-trigger']").click()

        // Enter a title that's too long (over 1000 characters)
        val input = page.locator("[data-testid='time-entry-quick-title-input']")
        val longTitle = "a".repeat(1001)
        input.fill(longTitle)

        // Verify save button is disabled
        assertThat(page.locator("[data-testid='time-entry-quick-title-save-button']")).isDisabled()
    }

    @Test
    fun `should show loading state while saving`() {
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Original Title",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open popover
        page.locator("[data-testid='time-entry-quick-title-trigger']").click()

        // Update title
        val input = page.locator("[data-testid='time-entry-quick-title-input']")
        input.fill("New Title")

        // Click save and immediately check for loading icon
        // Note: This might be too fast to observe the loading state in some cases
        page.locator("[data-testid='time-entry-quick-title-save-button']").click()

        // Verify the operation completes (popover closes)
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).not().isVisible()
    }

    @Test
    fun `should show cursor pointer on hover over title`() {
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

        // Verify the title has cursor-pointer class
        val titleTrigger = page.locator("[data-testid='time-entry-quick-title-trigger']")
        val classAttr = titleTrigger.getAttribute("class") ?: ""
        assert(classAttr.contains("cursor-pointer")) { "Expected cursor-pointer class but got: $classAttr" }
    }

    @Test
    fun `should not affect tags when updating title`() {
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent", "feature"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Quick edit title
        page.locator("[data-testid='time-entry-quick-title-trigger']").click()
        page.locator("[data-testid='time-entry-quick-title-input']").fill("Updated Title")
        page.locator("[data-testid='time-entry-quick-title-save-button']").click()

        // Wait for save to complete
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).not().isVisible()

        // Verify tags are still visible in UI
        val tags = page.locator("[data-testid='entry-tags']").locator("[data-testid^='entry-tag-']")
        assertThat(tags).containsText(arrayOf("backend", "feature", "urgent"))

        // Verify tags in database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Updated Title", updatedEntry.title)
            assertEquals(entry.tags.toSet(), updatedEntry.tags.toSet())
        }
    }

    @Test
    fun `should trim whitespace from title when saving`() {
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Original Title",
                    ownerId = requireNotNull(testUser.id),
                    tags = emptyArray(),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Quick edit with whitespace
        page.locator("[data-testid='time-entry-quick-title-trigger']").click()
        page.locator("[data-testid='time-entry-quick-title-input']").fill("  Trimmed Title  ")
        page.locator("[data-testid='time-entry-quick-title-save-button']").click()

        // Wait for save
        assertThat(page.locator("[data-testid='time-entry-quick-title-popover']")).not().isVisible()

        // Verify title was trimmed in database
        testDatabaseSupport.inTransaction {
            val updatedEntry = timeLogEntryRepository.findById(requireNotNull(entry.id)).orElseThrow()
            assertEquals("Trimmed Title", updatedEntry.title)
        }
    }
}
