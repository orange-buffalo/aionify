package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Tests for entries that span across days.
 * Entries should always be shown on their start day, with visual indicators for cross-day entries.
 */
class TimeLogsCrossDayTest : TimeLogsPageTestBase() {
    @Test
    fun `should show entry on start day when it spans midnight`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // baseTime is Saturday, March 16, 2024 at 03:30:00 NZDT
        // Create an entry that starts on Friday evening and ends on Saturday morning
        // Friday 20:00 NZDT to Saturday 02:30 NZDT (6.5 hours, spans midnight)
        val fridayEvening = baseTime.minusHours(7).minusMinutes(30) // Friday 20:00 NZDT
        val saturdayMorning = baseTime.minusHours(1) // Saturday 02:30 NZDT

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = fridayEvening,
                endTime = saturdayMorning,
                title = "Cross-Day Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Entry should appear only on Friday (start day) with the full time range
        // The UI should show a warning icon indicating it ends on a different day
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "06:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Yesterday",
                            totalDuration = "06:30:00", // Full duration from Friday 20:00 to Saturday 02:30
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Cross-Day Entry",
                                        timeRange = "20:00 - Sat, 02:30", // Shows weekday prefix for end time
                                        duration = "06:30:00",
                                        hasDifferentDayWarning = true,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should show entry on start day when it spans week boundary`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // baseTime is Saturday, March 16, 2024 at 03:30:00 NZDT
        // Create an entry that starts on Sunday (last day of week) and ends on Monday (first day of next week)
        // Sunday 23:30 NZDT to Monday 01:30 NZDT (2 hours, spans week boundary)
        val sundayEvening = baseTime.plusHours(44) // Sunday, March 17 at 23:30 NZDT
        val mondayMorning = baseTime.plusHours(46) // Monday, March 18 at 01:30 NZDT

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = sundayEvening,
                endTime = mondayMorning,
                title = "Week Boundary Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Entry should appear only on Sunday (start day) in the current week
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "02:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Sunday, 17 Mar",
                            totalDuration = "02:00:00", // Full duration from Sunday 23:30 to Monday 01:30
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Week Boundary Entry",
                                        timeRange = "23:30 - Mon, 01:30", // Shows weekday prefix for end time
                                        duration = "02:00:00",
                                        hasDifferentDayWarning = true,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should not show warning icon for same-day entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // baseTime is Saturday, March 16, 2024 at 03:30:00 NZDT
        // Create an entry that starts and ends on the same day
        val morning = baseTime.minusHours(1) // Saturday 02:30 NZDT
        val afternoon = baseTime.plusHours(1) // Saturday 04:30 NZDT

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = morning,
                endTime = afternoon,
                title = "Same-Day Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Entry should appear without warning icon
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "02:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "02:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Same-Day Entry",
                                        timeRange = "02:30 - 04:30", // No weekday prefix
                                        duration = "02:00:00",
                                        hasDifferentDayWarning = false,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }
}
