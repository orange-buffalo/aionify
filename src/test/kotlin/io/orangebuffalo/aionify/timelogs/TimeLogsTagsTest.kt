package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for tag display and editing on time entries.
 */
class TimeLogsTagsTest : TimeLogsPageTestBase() {
    @Test
    fun `should display tags on time entries`() {
        // Create entries with various tag configurations
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200), // 2 hours ago
                endTime = FIXED_TEST_TIME.minusSeconds(5400), // 1.5 hours ago
                title = "Entry with Multiple Tags",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("work", "urgent", "frontend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(5400), // 1.5 hours ago
                endTime = FIXED_TEST_TIME.minusSeconds(3600), // 1 hour ago
                title = "Entry with Single Tag",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600), // 1 hour ago
                endTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago
                title = "Entry without Tags",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify entries are displayed with their tags
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Entry without Tags",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                        tags = emptyList(),
                                    ),
                                    EntryState(
                                        title = "Entry with Single Tag",
                                        timeRange = "02:00 - 02:30",
                                        duration = "00:30:00",
                                        tags = listOf("backend"),
                                    ),
                                    EntryState(
                                        title = "Entry with Multiple Tags",
                                        timeRange = "01:30 - 02:00",
                                        duration = "00:30:00",
                                        tags = listOf("frontend", "urgent", "work"),
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should support editing tags on active entry`() {
        // Create an active entry with initial tags
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME,
                    endTime = null,
                    title = "Active Task with Tags",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend", "urgent"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click edit button to enter edit mode
        timeLogsPage.clickEditEntry()

        // Wait for edit mode to be visible
        page.waitForSelector("[data-testid='edit-title-input']")

        // Verify tag selector button is visible
        assertThat(page.locator("[data-testid='edit-tags-button']")).isVisible()

        // Open tag selector
        timeLogsPage.clickEditTagsButton()

        // Verify existing tags are checked
        timeLogsPage.assertTagsSelected(listOf("backend", "urgent"), testIdPrefix = "edit-tags")

        // Add a new tag by unchecking one and adding another
        timeLogsPage.toggleTag("urgent", testIdPrefix = "edit-tags")

        // Close popover by clicking outside
        page.locator("[data-testid='edit-title-input']").click()

        // Save the changes
        timeLogsPage.clickSaveEdit()

        // Wait for save to complete
        page.waitForCondition {
            page.locator("[data-testid='edit-title-input']").isHidden
        }

        // Verify the entry was updated in the database
        val updatedEntry = timeLogEntryRepository.findById(entry.id!!).orElse(null)
        assertNotNull(updatedEntry)
        assertEquals("Active Task with Tags", updatedEntry!!.title)
        assertEquals(listOf("backend"), updatedEntry.tags.toList())
    }

    @Test
    fun `should load existing tags when editing active entry`() {
        // Create existing entries with tags to populate the tag selector
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200),
                endTime = FIXED_TEST_TIME.minusSeconds(3600),
                title = "Previous Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("frontend", "testing", "ui"),
            ),
        )

        // Create an active entry with some tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME,
                endTime = null,
                title = "Current Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("frontend", "ui"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Wait for edit mode
        page.waitForSelector("[data-testid='edit-title-input']")

        // Open tag selector
        timeLogsPage.clickEditTagsButton()

        // Wait for popover to be visible
        page.waitForSelector("[data-testid='edit-tags-popover']")

        // Verify existing tags from the entry are pre-selected
        timeLogsPage.assertTagsSelected(listOf("frontend", "ui"), testIdPrefix = "edit-tags")

        // Verify tags from other entries are available in the list
        assertThat(page.locator("[data-testid='edit-tags-checkbox-testing']")).isVisible()
        assertThat(page.locator("[data-testid='edit-tags-checkbox-testing']")).not().isChecked()
    }

    @Test
    fun `should support editing tags on stopped entry`() {
        // Create a stopped entry with initial tags
        val entry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Completed Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("backend"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click edit button for the entry
        timeLogsPage.clickEditForEntry("Completed Task")

        // Wait for edit mode
        page.waitForSelector("[data-testid='stopped-entry-edit-title-input']")

        // Verify tag selector button is visible
        assertThat(page.locator("[data-testid='stopped-entry-edit-tags-button']")).isVisible()

        // Open tag selector
        timeLogsPage.clickStoppedEntryEditTagsButton()

        // Wait for popover
        page.waitForSelector("[data-testid='stopped-entry-edit-tags-popover']")

        // Verify existing tags are checked
        timeLogsPage.assertTagsSelected(listOf("backend"), testIdPrefix = "stopped-entry-edit-tags")

        // Add a new tag
        page.locator("[data-testid='stopped-entry-edit-tags-new-tag-input']").fill("urgent")
        page.locator("[data-testid='stopped-entry-edit-tags-add-tag-button']").click()

        // Verify the tag was added by checking it's in the list and selected
        assertThat(page.locator("[data-testid='stopped-entry-edit-tags-item-urgent']")).isVisible()

        // Close popover by pressing Escape
        page.keyboard().press("Escape")

        // Verify popover is closed
        assertThat(page.locator("[data-testid='stopped-entry-edit-tags-popover']")).not().isVisible()

        // Save the changes
        timeLogsPage.clickSaveStoppedEntryEdit()

        // Wait for save to complete
        page.waitForCondition {
            page.locator("[data-testid='stopped-entry-edit-title-input']").isHidden
        }

        // Verify the entry was updated
        val updatedEntry = timeLogEntryRepository.findById(entry.id!!).orElse(null)
        assertNotNull(updatedEntry)
        assertEquals("Completed Task", updatedEntry!!.title)
        // Tags should be sorted alphabetically in the list
        val expectedTags = listOf("backend", "urgent").sorted()
        assertEquals(expectedTags, updatedEntry.tags.toList().sorted())
    }

    @Test
    fun `should load existing tags when editing stopped entry`() {
        // Create an entry with tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200),
                endTime = FIXED_TEST_TIME.minusSeconds(3600),
                title = "Previous Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("meeting", "planning"),
            ),
        )

        // Create the entry to edit
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Task to Edit",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("meeting"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Task to Edit")

        // Wait for edit mode
        page.waitForSelector("[data-testid='stopped-entry-edit-title-input']")

        // Open tag selector
        timeLogsPage.clickStoppedEntryEditTagsButton()

        // Wait for popover
        page.waitForSelector("[data-testid='stopped-entry-edit-tags-popover']")

        // Verify the entry's existing tag is pre-selected
        timeLogsPage.assertTagsSelected(listOf("meeting"), testIdPrefix = "stopped-entry-edit-tags")

        // Verify other tags from history are available but not selected
        assertThat(page.locator("[data-testid='stopped-entry-edit-tags-checkbox-planning']")).isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-tags-checkbox-planning']")).not().isChecked()
    }
}
