package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Tests for deleting time log entries.
 */
class TimeLogsDeletionTest : TimeLogsPageTestBase() {
    @Test
    fun `should delete a time entry with confirmation`() {
        // Create an entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify entry exists
        val initialState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Task to Delete",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Delete the entry
        timeLogsPage.deleteEntry("Task to Delete")

        // Wait for entry to be removed from the list
        page.waitForCondition {
            page.locator("[data-testid='time-entry']:has-text('Task to Delete')").count() == 0
        }

        // Verify entry is deleted and page shows no entries
        val deletedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups = emptyList(),
                errorMessageVisible = false,
            )
        timeLogsPage.assertPageState(deletedState)
    }

    @Test
    fun `should delete midnight-split entry correctly`() {
        // Create an entry that spans midnight
        // Friday 22:00 NZDT to Saturday 02:00 NZDT (4 hours, spans midnight)
        val fridayEvening = FIXED_TEST_TIME.minusSeconds(19800) // Friday 22:00 NZDT (5.5 hours = 19800 seconds before Saturday 03:30)
        val saturdayMorning = FIXED_TEST_TIME.minusSeconds(5400) // Saturday 02:00 NZDT (1.5 hours = 5400 seconds before Saturday 03:30)

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = fridayEvening,
                endTime = saturdayMorning,
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state with split entry across two days
        // Entry runs from Friday 22:00 to Saturday 02:00 NZDT
        // Split at midnight: Friday 22:00 - 23:59:59.999 and Saturday 00:00 - 02:00
        val initialState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups =
                    listOf(
                        // Saturday (today) portion: 00:00 - 02:00 = 2 hours
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "02:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Task to Delete",
                                        timeRange = "00:00 - 02:00",
                                        duration = "02:00:00",
                                    ),
                                ),
                        ),
                        // Friday (yesterday) portion: 22:00 to 23:59:59.999 (displays as 01:59:59)
                        DayGroupState(
                            displayTitle = "Yesterday",
                            totalDuration = "01:59:59", // Due to midnight split at 23:59:59.999
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Task to Delete",
                                        timeRange = "22:00 - 23:59",
                                        duration = "01:59:59",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Delete the entry (deleting from first occurrence)
        timeLogsPage.deleteEntry("Task to Delete")

        // Wait for both parts to be removed
        page.waitForCondition {
            page.locator("[data-testid='time-entry']:has-text('Task to Delete')").count() == 0
        }

        // Verify both parts of the entry are deleted
        val deletedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups = emptyList(),
                errorMessageVisible = false,
            )
        timeLogsPage.assertPageState(deletedState)
    }
}
