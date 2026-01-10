package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Tests for week navigation functionality.
 */
class TimeLogsNavigationTest : TimeLogsPageTestBase() {
    @Test
    fun `should navigate between weeks`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create entries for different weeks
        val lastWeek = baseTime.minusDays(7)

        // Current week entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(1),
                endTime = baseTime.minusMinutes(30),
                title = "This Week Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Last week entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = lastWeek.minusHours(1),
                endTime = lastWeek.minusMinutes(30),
                title = "Last Week Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify current week with its entry
        val currentWeekState =
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
                                        title = "This Week Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(currentWeekState)

        // Navigate to previous week
        timeLogsPage.goToPreviousWeek()

        // Verify last week with its entry
        val lastWeekState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "4 Mar - 10 Mar", weeklyTotal = "00:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Saturday, 9 Mar",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Last Week Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(lastWeekState)

        // Navigate back to current week
        timeLogsPage.goToNextWeek()

        // Verify we're back to current week
        timeLogsPage.assertPageState(currentWeekState)
    }
}
