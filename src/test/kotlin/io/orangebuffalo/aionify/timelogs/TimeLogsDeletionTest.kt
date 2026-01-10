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
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create an entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify entry exists
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
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:00:00"),
                dayGroups = emptyList(),
                errorMessageVisible = false,
            )
        timeLogsPage.assertPageState(deletedState)
    }

    @Test
    fun `should delete midnight-split entry correctly`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create an entry that spans midnight
        // Friday 22:00 NZDT to Saturday 02:00 NZDT (4 hours, spans midnight)
        val fridayEvening = baseTime.withLocalDate("2024-03-15").withLocalTime("22:00") // Friday 22:00 NZDT
        val saturdayMorning = baseTime.withLocalTime("02:00") // Saturday 02:00 NZDT

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = fridayEvening,
                endTime = saturdayMorning,
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state - entry shown only on start day (Friday) with full duration
        val initialState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "04:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Yesterday",
                            totalDuration = "04:00:00", // Full duration from Friday 22:00 to Saturday 02:00
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Task to Delete",
                                        timeRange = "22:00 - Sat, 02:00", // Shows weekday for end time
                                        duration = "04:00:00",
                                        hasDifferentDayWarning = true,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Delete the entry
        timeLogsPage.deleteEntry("Task to Delete")

        // Wait for entry to be removed
        page.waitForCondition {
            page.locator("[data-testid='time-entry']:has-text('Task to Delete')").count() == 0
        }

        // Verify entry is deleted
        val deletedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:00:00"),
                dayGroups = emptyList(),
                errorMessageVisible = false,
            )
        timeLogsPage.assertPageState(deletedState)
    }
}
