package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Tests for entries that span across midnight.
 */
class TimeLogsMidnightSpanTest : TimeLogsPageTestBase() {
    @Test
    fun `should handle entry spanning two dates`() {
        // Create an entry that spans midnight
        // FIXED_TEST_TIME is Saturday, March 16, 2024 at 03:30:00 NZDT
        // We'll create an entry that starts on Friday evening NZDT and ends on Saturday morning NZDT
        // Friday 20:00 NZDT to Saturday 02:30 NZDT (6.5 hours, spans midnight)
        val yesterdayEvening = FIXED_TEST_TIME.minusSeconds(27000) // Friday 20:00 NZDT (7.5 hours = 27000 seconds before Saturday 03:30)
        val todayMorning = FIXED_TEST_TIME.minusSeconds(3600) // Saturday 02:30 NZDT (1 hour before Saturday 03:30)

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = yesterdayEvening, // Started on Friday evening NZDT
                endTime = todayMorning, // Ended on Saturday morning NZDT
                title = "Spanning Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Entry should appear split across two day groups
        // Friday 20:00 NZDT to Saturday 02:30 NZDT (6.5 hours total)
        // Split at midnight: Friday 20:00 - 23:59:59.999 and Saturday 00:00 - 02:30
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups =
                    listOf(
                        // Saturday (today) - shows portion from midnight to 02:30 (2:30 duration)
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "02:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Spanning Entry",
                                        timeRange = "00:00 - 02:30",
                                        duration = "02:30:00",
                                    ),
                                ),
                        ),
                        // Friday (yesterday) - shows portion from 20:00 to 23:59:59.999 (03:59:59 duration)
                        DayGroupState(
                            displayTitle = "Yesterday",
                            totalDuration = "03:59:59", // Due to midnight split at 23:59:59.999
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Spanning Entry",
                                        timeRange = "20:00 - 23:59",
                                        duration = "03:59:59",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should handle midnight split scenario correctly`() {
        // FIXED_TEST_TIME is Saturday, March 16, 2024 at 03:30:00 NZDT
        // Create an entry that spans from Friday evening to Saturday morning
        // Friday 22:00 NZDT to Saturday 02:00 NZDT (4 hours, spans midnight)
        val fridayEvening = FIXED_TEST_TIME.minusSeconds(19800) // Friday 22:00 NZDT (5.5 hours = 19800 seconds before Saturday 03:30)
        val saturdayMorning = FIXED_TEST_TIME.minusSeconds(5400) // Saturday 02:00 NZDT (1.5 hours = 5400 seconds before Saturday 03:30)

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = fridayEvening,
                endTime = saturdayMorning,
                title = "Midnight Spanning Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify the entry appears split across 2 day groups
        // Friday 22:00 to Saturday 02:00 NZDT (4 hours total, spans midnight)
        // Split at midnight: Friday 22:00 - 23:59:59.999 and Saturday 00:00 - 02:00
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups =
                    listOf(
                        // Saturday (today) - portion from 00:00 to 02:00
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "02:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Midnight Spanning Task",
                                        timeRange = "00:00 - 02:00",
                                        duration = "02:00:00",
                                    ),
                                ),
                        ),
                        // Friday (yesterday) - portion from 22:00 to 23:59:59.999 (displays as 01:59:59)
                        DayGroupState(
                            displayTitle = "Yesterday",
                            totalDuration = "01:59:59", // Due to midnight split at 23:59:59.999
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Midnight Spanning Task",
                                        timeRange = "22:00 - 23:59",
                                        duration = "01:59:59",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }
}
