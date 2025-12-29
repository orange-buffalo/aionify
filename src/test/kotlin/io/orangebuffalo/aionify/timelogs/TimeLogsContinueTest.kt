package io.orangebuffalo.aionify.timelogs

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
        // Create a completed entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Previous Task",
                ownerId = requireNotNull(testUser.id),
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
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click continue button
        timeLogsPage.clickContinueForEntry("Previous Task")

        // Verify the entry is started immediately with the same title
        // Note: The new active entry appears in the day groups, plus the previous completed entry
        val activeState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Previous Task",
                        duration = "00:00:00",
                        startedAt = "16 Mar, 03:30", // Started at FIXED_TEST_TIME (backend time)
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00", // Total includes both entries
                            entries =
                                listOf(
                                    // New active entry appears first (most recent)
                                    EntryState(
                                        title = "Previous Task",
                                        timeRange = "03:30 - in progress", // Started at FIXED_TEST_TIME (backend time)
                                        duration = "00:00:00",
                                    ),
                                    // Original completed entry
                                    EntryState(
                                        title = "Previous Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(activeState)

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
        // Create a completed entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200), // 2 hours ago (12:30)
                endTime = FIXED_TEST_TIME.minusSeconds(5400), // 1.5 hours ago (13:00)
                title = "Completed Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Create an active entry
        val previouslyActiveEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago (03:00)
                    endTime = null,
                    title = "Currently Active Task",
                    ownerId = requireNotNull(testUser.id),
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
                                    ),
                                    EntryState(
                                        title = "Completed Task",
                                        timeRange = "01:30 - 02:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click start button on the completed entry
        // This should:
        // 1. Stop the currently active entry
        // 2. Start a new entry with the completed entry's title
        timeLogsPage.clickContinueForEntry("Completed Task")

        // Verify the new state:
        // - The previously active entry should now be stopped
        // - A new active entry with "Completed Task" title should be started
        val newState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Completed Task",
                        duration = "00:00:00",
                        startedAt = "16 Mar, 03:30", // Started at FIXED_TEST_TIME (backend time)
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:00:00", // All entries combined
                            entries =
                                listOf(
                                    // New active entry (most recent)
                                    EntryState(
                                        title = "Completed Task",
                                        timeRange = "03:30 - in progress", // Started at FIXED_TEST_TIME (backend time)
                                        duration = "00:00:00",
                                    ),
                                    // Previously active entry, now stopped
                                    EntryState(
                                        title = "Currently Active Task",
                                        timeRange = "03:00 - 03:30", // Stopped at FIXED_TEST_TIME (backend time)
                                        duration = "00:30:00",
                                    ),
                                    // Original completed entry
                                    EntryState(
                                        title = "Completed Task",
                                        timeRange = "01:30 - 02:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(newState)

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
