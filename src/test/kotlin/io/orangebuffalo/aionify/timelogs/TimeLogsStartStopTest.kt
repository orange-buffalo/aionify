package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for starting and stopping time entries.
 */
class TimeLogsStartStopTest : TimeLogsPageTestBase() {
    @Test
    fun `should start and stop a time entry`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState()
        timeLogsPage.assertPageState(initialState)

        // Start a new entry
        timeLogsPage.fillNewEntryTitle("Test Task")
        timeLogsPage.clickStart()

        // Verify entry is started with active timer
        // Note: Active entries appear in the day groups list immediately
        val activeState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Test Task",
                        duration = "00:00:00",
                        startedAt = "16 Mar, 03:30", // Started at FIXED_TEST_TIME (03:30)
                    ),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Test Task",
                                        timeRange = "03:30 - in progress",
                                        duration = "00:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(activeState)

        // Verify database state after starting
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(activeEntry, "Active entry should exist in database")
        assertEquals("Test Task", activeEntry!!.title)
        // Verify startTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, activeEntry.startTime, "Start time should be set by backend")
        assertNull(activeEntry.endTime, "Active entry should not have end time")

        // Stop the entry
        timeLogsPage.clickStop()

        // Verify we're back to ready state with the completed entry visible
        val stoppedState =
            activeState.copy(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Test Task",
                                        timeRange = "03:30 - 03:30",
                                        duration = "00:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(stoppedState)

        // Verify database state after stopping
        val noActiveEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNull(noActiveEntry, "Should not have active entry after stopping")

        // Verify the stopped entry has correct timestamps
        val stoppedEntry = timeLogEntryRepository.findById(activeEntry.id!!).orElse(null)
        assertNotNull(stoppedEntry, "Stopped entry should still exist in database")
        assertEquals("Test Task", stoppedEntry!!.title)
        assertEquals(FIXED_TEST_TIME, stoppedEntry.startTime, "Start time should remain unchanged")
        // Verify endTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, stoppedEntry.endTime, "End time should be set by backend")
    }

    @Test
    fun `should show active entry on page load`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify active entry is shown with timer
        // Note: Active entries appear in the day groups immediately
        val activeState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Active Task",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Active Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(activeState)
    }

    @Test
    fun `should require title to start entry`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify start button is disabled without title
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.NoActiveEntry(
                        inputVisible = true,
                        startButtonVisible = true,
                        startButtonEnabled = false,
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:00:00"),
                dayGroups = emptyList(),
            )
        timeLogsPage.assertPageState(initialState)
    }

    @Test
    fun `should allow starting entry by pressing Enter`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Fill in title and press Enter
        timeLogsPage.fillNewEntryTitle("Quick Entry")
        timeLogsPage.pressEnterInNewEntryInput()

        // Verify entry is started
        // Active entry appears in day groups immediately
        val activeState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Quick Entry",
                        duration = "00:00:00",
                        startedAt = "16 Mar, 03:30", // Started at FIXED_TEST_TIME (backend time)
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Quick Entry",
                                        timeRange = "03:30 - in progress", // Started at FIXED_TEST_TIME (backend time)
                                        duration = "00:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(activeState)

        // Verify database state - entry should be created with backend timestamp
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(activeEntry, "Active entry should exist in database")
        assertEquals("Quick Entry", activeEntry!!.title)
        // Verify startTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, activeEntry.startTime, "Start time should be set by backend")
        assertNull(activeEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should prevent starting new entry while another is active`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify we cannot start a new entry (input and start button are hidden)
        // Active entry appears in day groups
        val activeState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Active Task",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Active Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(activeState)
    }
}
