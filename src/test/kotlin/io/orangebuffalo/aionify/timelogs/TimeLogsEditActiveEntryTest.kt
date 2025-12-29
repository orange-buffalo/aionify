package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for editing active (in-progress) time entries.
 */
class TimeLogsEditActiveEntryTest : TimeLogsPageTestBase() {
    @Test
    fun `should edit active entry title`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Original Title",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial active state
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Original Title",
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
                                        title = "Original Title",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click edit button - transition to edit mode
        timeLogsPage.clickEditEntry()

        val editModeState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Original Title",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                        editMode =
                            EditModeState.Editing(
                                titleValue = "Original Title",
                                dateValue = "16 Mar 2024",
                                timeValue = "03:00",
                            ),
                    ),
            )
        timeLogsPage.assertPageState(editModeState)

        // Change the title
        timeLogsPage.fillEditTitle("Updated Title")

        // Save changes
        timeLogsPage.clickSaveEdit()

        // Verify entry is updated - mutation from initial state
        val updatedState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Updated Title",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Updated Title",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(updatedState)

        // Verify database state - title should be updated
        val editedEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(editedEntry, "Active entry should still exist in database")
        assertEquals("Updated Title", editedEntry!!.title, "Title should be updated in database")
        assertEquals(FIXED_TEST_TIME.minusSeconds(1800), editedEntry.startTime, "Start time should remain unchanged")
        assertNull(editedEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should edit active entry start time`() {
        // Create an active entry that started 30 minutes ago
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800), // 14:00
                endTime = null,
                title = "Test Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Test Task",
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
                                        title = "Test Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Change start time to 1 hour ago (02:30) - need to set date explicitly to today
        timeLogsPage.fillEditDate("2024-03-16") // Saturday (today)
        timeLogsPage.fillEditTime("02:30")

        // Save changes
        timeLogsPage.clickSaveEdit()

        // Verify the start time and duration are updated - mutation from initial state
        // Duration should now be 1 hour (from 02:30 to 03:30)
        val updatedState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Test Task",
                        duration = "01:00:00",
                        startedAt = "16 Mar, 02:30",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Test Task",
                                        timeRange = "02:30 - in progress",
                                        duration = "01:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(updatedState)

        // Verify database state - startTime should be updated to user-provided time
        val editedEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(editedEntry, "Active entry should still exist in database")
        assertEquals("Test Task", editedEntry!!.title)
        // When user edits start time, they provide the value, so it should match what they entered
        // The user entered "02:30" in NZDT timezone, which is FIXED_TEST_TIME.minusSeconds(3600)
        assertEquals(FIXED_TEST_TIME.minusSeconds(3600), editedEntry.startTime, "Start time should be updated to user's value")
        assertNull(editedEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should edit active entry title and start time together`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800), // 14:00
                endTime = null,
                title = "Original Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Original Task",
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
                                        title = "Original Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Change both title and start time - need to set date explicitly to today
        timeLogsPage.fillEditTitle("Modified Task")
        timeLogsPage.fillEditDate("2024-03-16") // Saturday (today)
        timeLogsPage.fillEditTime("01:00")

        // Save changes
        timeLogsPage.clickSaveEdit()

        // Verify both fields are updated - mutation from initial state
        // Duration should be 2.5 hours (from 01:00 to 03:30)
        val updatedState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Modified Task",
                        duration = "02:30:00",
                        startedAt = "16 Mar, 01:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "02:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "02:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Modified Task",
                                        timeRange = "01:00 - in progress",
                                        duration = "02:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(updatedState)

        // Verify database state - both title and startTime should be updated
        val editedEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(editedEntry, "Active entry should still exist in database")
        assertEquals("Modified Task", editedEntry!!.title, "Title should be updated in database")
        // User entered "01:00" in NZDT timezone, which is FIXED_TEST_TIME.minusSeconds(9000)
        assertEquals(FIXED_TEST_TIME.minusSeconds(9000), editedEntry.startTime, "Start time should be updated to user's value")
        assertNull(editedEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should cancel editing and revert changes`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Original Title",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Original Title",
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
                                        title = "Original Title",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Make some changes
        timeLogsPage.fillEditTitle("Changed Title")
        timeLogsPage.fillEditTime("13:00")

        // Cancel editing
        timeLogsPage.clickCancelEdit()

        // Verify original state is preserved - revert to initial state
        timeLogsPage.assertPageState(initialState)
    }

    @Test
    fun `should change start time to different day`() {
        // Create an active entry that started today
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800), // Friday 14:00
                endTime = null,
                title = "Cross-Day Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Cross-Day Task",
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
                                        title = "Cross-Day Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Change start time to yesterday (Friday)
        timeLogsPage.fillEditDate("2024-03-15")
        timeLogsPage.fillEditTime("05:00")

        // Save changes
        timeLogsPage.clickSaveEdit()

        // Verify the entry now appears only in yesterday's group (start day)
        // Duration should be from Friday 05:00 to Saturday 03:30 NZDT = 22:30:00
        // The entry shows in progress with weekday indicator
        val updatedState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Cross-Day Task",
                        duration = "22:30:00", // Total duration from Friday 05:00 to Saturday 03:30
                        startedAt = "15 Mar, 05:00", // Start time from yesterday
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "22:30:00"),
                dayGroups =
                    listOf(
                        // Entry shown only on start day (Yesterday) with full duration
                        DayGroupState(
                            displayTitle = "Yesterday",
                            totalDuration = "22:30:00", // Full duration 05:00 to 03:30 next day
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Cross-Day Task",
                                        timeRange = "05:00 - in progress", // Active entry, no weekday shown
                                        duration = "22:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(updatedState)
    }

    @Test
    fun `should prevent saving edit with empty title`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Valid Title",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Valid Title",
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
                                        title = "Valid Title",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Verify edit mode state
        val editModeState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Valid Title",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                        editMode =
                            EditModeState.Editing(
                                titleValue = "Valid Title",
                                dateValue = "16 Mar 2024",
                                timeValue = "03:00",
                            ),
                    ),
            )
        timeLogsPage.assertPageState(editModeState)

        // Clear the title
        timeLogsPage.fillEditTitle("")

        // Verify save button is disabled with empty title
        val editWithEmptyTitleState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Valid Title",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                        editMode =
                            EditModeState.Editing(
                                titleValue = "",
                                dateValue = "16 Mar 2024",
                                timeValue = "03:00",
                                saveButtonEnabled = false,
                            ),
                    ),
            )
        timeLogsPage.assertPageState(editWithEmptyTitleState)
    }

    @Test
    fun `should hide edit button when not in active state`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify edit button is not visible when no active entry
        val noActiveEntryState = TimeLogsPageState()
        timeLogsPage.assertPageState(noActiveEntryState)
    }
}
