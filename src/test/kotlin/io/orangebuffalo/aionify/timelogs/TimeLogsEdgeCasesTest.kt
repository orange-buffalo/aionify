package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Tests for edge cases and special scenarios.
 */
class TimeLogsEdgeCasesTest : TimeLogsPageTestBase() {
    @Test
    fun `should show Sunday entry in current week when viewing on Sunday`() {
        // FIXED_TEST_TIME is Friday, March 15, 2024 at 14:30:00 UTC = Saturday, March 16, 2024 at 03:30:00 NZDT
        // Sunday, March 17, 2024 at 14:30:00 UTC = Monday, March 18, 2024 at 03:30:00 NZDT
        // To test Sunday in Auckland, we need Sunday at 03:30 NZDT = Saturday at 14:30 UTC
        val sundayTime = FIXED_TEST_TIME.plusDays(1) // Saturday 14:30 UTC = Sunday 03:30 NZDT

        // Override the test time to Sunday
        page.clock().pauseAt(sundayTime.toEpochMilli())

        // Create an entry for Sunday in Auckland timezone
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = sundayTime.minusMinutes(30), // 30 minutes ago (Saturday 14:00 UTC = Sunday 03:00 NZDT)
                endTime = sundayTime.minusMinutes(15), // 15 minutes ago (Saturday 14:15 UTC = Sunday 03:15 NZDT)
                title = "Sunday Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Expected: The Sunday entry should appear in the "Today" section
        // Week should be Monday Mar 11 - Sunday Mar 17 (in Auckland timezone)
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:15:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:15:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Sunday Task",
                                        timeRange = "03:00 - 03:15",
                                        duration = "00:15:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }
}
