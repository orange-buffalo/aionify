package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Tests for active timer duration display and clock advancement.
 */
class TimeLogsClockTest : TimeLogsPageTestBase() {
    @Test
    fun `should verify active task duration using clock`() {
        // Set the base time for this test: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create an active entry that started 30 minutes ago
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusMinutes(30),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify active timer shows the correct duration (30 minutes = 00:30:00)
        val initialState =
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
        timeLogsPage.assertPageState(initialState)

        // Advance the clock by 5 minutes
        timeLogsPage.advanceClock(5 * 60 * 1000)

        // Timer and day group totals should now show 35 minutes
        val state35min =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Active Task",
                        duration = "00:35:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:35:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:35:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Active Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:35:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(state35min)

        // Advance another 25 minutes to reach 1 hour
        timeLogsPage.advanceClock(25 * 60 * 1000)

        // Timer and day group totals should now show 1 hour
        val state1hour =
            state35min.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Active Task",
                        duration = "01:00:00",
                        startedAt = "16 Mar, 03:00",
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
                                        title = "Active Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "01:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(state1hour)
    }
}
