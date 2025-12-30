package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for continuing (restarting) from existing entries.
 */
class TimeLogsContinueTest : TimeLogsPageTestBase() {
    @Test
    fun `should continue with an existing entry`() {
        // Create a completed entry with a specific tag to avoid grouping with the new entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Previous Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("completed"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state with the existing entry
        val initialState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Previous Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                        tags = listOf("completed"),
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click continue button
        timeLogsPage.clickContinueForEntry("Previous Task")

        // Verify the entry is started immediately with the same title and tags
        // Note: With grouping enabled, the new active entry and the completed entry
        // will be grouped together since they have the same title and tags
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()

        // Verify the count badge shows 2 entries
        assertThat(groupedEntry.locator("[data-testid='entry-count-badge']")).containsText("2")

        // Verify title and tag
        assertThat(groupedEntry.locator("[data-testid='entry-title']")).containsText("Previous Task")
        assertThat(groupedEntry.locator("[data-testid^='entry-tag-']")).containsText(arrayOf("completed"))

        // Verify time range shows earliest start to "in progress"
        assertThat(groupedEntry.locator("[data-testid='entry-time-range']")).containsText("02:30 - in progress")

        // Verify total duration only includes the completed entry
        assertThat(groupedEntry.locator("[data-testid='entry-duration']")).containsText("00:30:00")

        // Verify continue button is visible
        assertThat(groupedEntry.locator("[data-testid='continue-button']")).isVisible()

        // Verify active entry panel shows the active entry
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()
        assertThat(page.locator("[data-testid='current-entry-panel']")).containsText("Previous Task")

        // Verify database state - new entry created with backend timestamp
        val newActiveEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(newActiveEntry, "New active entry should exist in database")
        assertEquals("Previous Task", newActiveEntry!!.title)
        // Verify startTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, newActiveEntry.startTime, "Start time should be set by backend")
        assertNull(newActiveEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should start from existing entry when active entry exists by stopping active entry first`() {
        // Create a completed entry with tags to prevent grouping with the new active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200), // 2 hours ago (12:30)
                endTime = FIXED_TEST_TIME.minusSeconds(5400), // 1.5 hours ago (13:00)
                title = "Completed Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("completed"),
            ),
        )

        // Create an active entry with different tags to avoid grouping
        val previouslyActiveEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago (03:00)
                    endTime = null,
                    title = "Currently Active Task",
                    ownerId = requireNotNull(testUser.id),
                    tags = arrayOf("active"),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state with both active and completed entries
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Currently Active Task",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:00:00", // 30 min active + 30 min completed
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Currently Active Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                        tags = listOf("active"),
                                    ),
                                    EntryState(
                                        title = "Completed Task",
                                        timeRange = "01:30 - 02:00",
                                        duration = "00:30:00",
                                        tags = listOf("completed"),
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click continue button on the completed entry
        // This should:
        // 1. Stop the currently active entry
        // 2. Start a new entry with the completed entry's title and tags
        timeLogsPage.clickContinueForEntry("Completed Task")

        // Verify the new state:
        // - The previously active entry should now be stopped
        // - A new active entry with "Completed Task" title and tags should be started
        // - The new active entry and original completed entry will be grouped (same title and tags)

        // Verify active entry panel shows the new active entry
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()
        assertThat(page.locator("[data-testid='current-entry-panel']")).containsText("Completed Task")

        // Verify the grouped entry for "Completed Task" entries
        val completedTaskGroup =
            page
                .locator(
                    "[data-testid='grouped-time-entry']",
                ).filter(
                    com.microsoft.playwright.Locator
                        .FilterOptions()
                        .setHasText("Completed Task"),
                )
        assertThat(completedTaskGroup).isVisible()
        assertThat(completedTaskGroup.locator("[data-testid='entry-count-badge']")).containsText("2")

        // Verify the "Currently Active Task" is now stopped and shown as a regular entry
        val activeTaskEntry =
            page
                .locator(
                    "[data-testid='time-entry']",
                ).filter(
                    com.microsoft.playwright.Locator
                        .FilterOptions()
                        .setHasText("Currently Active Task"),
                )
        assertThat(activeTaskEntry).isVisible()
        assertThat(activeTaskEntry.locator("[data-testid='entry-time-range']")).containsText("03:00 - 03:30")

        // Verify database state:
        // 1. The previously active entry should be stopped with endTime from backend
        val stoppedPreviousEntry = timeLogEntryRepository.findById(previouslyActiveEntry.id!!).orElse(null)
        assertNotNull(stoppedPreviousEntry, "Previously active entry should exist")
        assertEquals(FIXED_TEST_TIME.minusSeconds(1800), stoppedPreviousEntry!!.startTime)
        // Verify endTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, stoppedPreviousEntry.endTime, "End time should be set by backend when stopping")

        // 2. A new active entry should be created with startTime from backend
        val newActiveEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(newActiveEntry, "New active entry should exist in database")
        assertEquals("Completed Task", newActiveEntry!!.title)
        // Verify startTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, newActiveEntry.startTime, "Start time should be set by backend")
        assertNull(newActiveEntry.endTime, "New active entry should not have end time")
    }
}
