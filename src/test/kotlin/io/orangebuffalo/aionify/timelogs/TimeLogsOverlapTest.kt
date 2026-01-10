package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Tests for overlapping time log entries detection and display.
 */
class TimeLogsOverlapTest : TimeLogsPageTestBase() {
    @Test
    fun `should show overlap warning when entries overlap by more than 1 second`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT
        // Create two overlapping entries on the same day

        // Entry 1: 01:00 - 02:30 (1h 30min)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30").minusMinutes(30), // 03:30 - 2:30 = 01:00
                endTime = baseTime.withLocalTime("02:30"), // 03:30 - 1:00 = 02:30
                title = "Task A",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 2: 02:00 - 03:00 (1h) - overlaps with Entry 1 from 02:00 to 02:30 (30 minutes)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30").minusMinutes(30), // 03:30 - 1:30 = 02:00
                endTime = baseTime.withLocalTime("03:00"), // 03:30 - 0:30 = 03:00
                title = "Task B",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Both entries should show overlap warnings (displayed newest first)
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "02:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "02:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Task B",
                                        timeRange = "02:00 - 03:00",
                                        duration = "01:00:00",
                                        hasOverlapWarning = true,
                                        overlappingEntryTitle = "Task A",
                                    ),
                                    EntryState(
                                        title = "Task A",
                                        timeRange = "01:00 - 02:30",
                                        duration = "01:30:00",
                                        hasOverlapWarning = true,
                                        overlappingEntryTitle = "Task B",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should not show overlap warning when entries touch at boundary (0-1 second overlap)`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT

        // Entry 1: 01:00 - 02:00
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30").minusMinutes(30), // 01:00
                endTime = baseTime.withLocalTime("02:30").minusMinutes(30), // 02:00
                title = "Task A",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 2: 02:00 - 03:00 (exact boundary, 0 overlap)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30").minusMinutes(30), // 02:00 (same as Entry 1 end)
                endTime = baseTime.withLocalTime("03:00"), // 03:00
                title = "Task B",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Neither entry should show overlap warnings
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
                                        title = "Task B",
                                        timeRange = "02:00 - 03:00",
                                        duration = "01:00:00",
                                        hasOverlapWarning = false,
                                    ),
                                    EntryState(
                                        title = "Task A",
                                        timeRange = "01:00 - 02:00",
                                        duration = "01:00:00",
                                        hasOverlapWarning = false,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should show overlap warning when one entry is fully contained in another`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT

        // Entry 1: 01:00 - 04:00 (3 hours - large entry)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30").minusMinutes(30), // 01:00
                endTime = baseTime.minusSeconds(-1800), // 04:00
                title = "Long Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 2: 02:00 - 03:00 (1 hour - fully contained in Entry 1)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30").minusMinutes(30), // 02:00
                endTime = baseTime.withLocalTime("03:00"), // 03:00
                title = "Short Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Both entries should show overlap warnings (displayed newest first)
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "04:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "04:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Short Task",
                                        timeRange = "02:00 - 03:00",
                                        duration = "01:00:00",
                                        hasOverlapWarning = true,
                                        overlappingEntryTitle = "Long Task",
                                    ),
                                    EntryState(
                                        title = "Long Task",
                                        timeRange = "01:00 - 04:00",
                                        duration = "03:00:00",
                                        hasOverlapWarning = true,
                                        overlappingEntryTitle = "Short Task",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should show overlap warning for multiple overlapping entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT

        // Entry 1: 01:00 - 02:30
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30").minusMinutes(30), // 01:00
                endTime = baseTime.withLocalTime("02:30"), // 02:30
                title = "Task A",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 2: 02:00 - 03:30 (overlaps with Entry 1 from 02:00 to 02:30)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30").minusMinutes(30), // 02:00
                endTime = baseTime, // 03:30
                title = "Task B",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 3: 03:00 - 04:00 (overlaps with Entry 2 from 03:00 to 03:30)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("03:00"), // 03:00
                endTime = baseTime.minusSeconds(-1800), // 04:00
                title = "Task C",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Each overlapping entry shows warning with the first overlapping entry it finds
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "04:00:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "04:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Task C",
                                        timeRange = "03:00 - 04:00",
                                        duration = "01:00:00",
                                        hasOverlapWarning = true,
                                        overlappingEntryTitle = "Task B",
                                    ),
                                    EntryState(
                                        title = "Task B",
                                        timeRange = "02:00 - 03:30",
                                        duration = "01:30:00",
                                        hasOverlapWarning = true,
                                        overlappingEntryTitle = "Task A",
                                    ),
                                    EntryState(
                                        title = "Task A",
                                        timeRange = "01:00 - 02:30",
                                        duration = "01:30:00",
                                        hasOverlapWarning = true,
                                        overlappingEntryTitle = "Task B",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should not show overlap warning for non-overlapping entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT

        // Entry 1: 01:00 - 02:00
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30").minusMinutes(30), // 01:00
                endTime = baseTime.withLocalTime("02:30").minusMinutes(30), // 02:00
                title = "Task A",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 2: 02:30 - 03:30 (gap of 30 minutes, no overlap)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"), // 02:30
                endTime = baseTime, // 03:30
                title = "Task B",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Neither entry should show overlap warnings
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
                                        title = "Task B",
                                        timeRange = "02:30 - 03:30",
                                        duration = "01:00:00",
                                        hasOverlapWarning = false,
                                    ),
                                    EntryState(
                                        title = "Task A",
                                        timeRange = "01:00 - 02:00",
                                        duration = "01:00:00",
                                        hasOverlapWarning = false,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should not show overlap warning for active entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT

        // Entry 1: 01:00 - 03:00 (stopped)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30").minusMinutes(30), // 01:00
                endTime = baseTime.withLocalTime("03:00"), // 03:00
                title = "Stopped Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 2: 02:00 - still running (active) - would overlap if it were stopped
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30").minusMinutes(30), // 02:00
                endTime = null, // Active entry
                title = "Active Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Neither entry should show overlap warnings because one is active
        val expectedState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Active Task",
                        duration = "01:30:00",
                        startedAt = "02:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "03:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "03:30:00", // Includes both stopped (02:00) and active (01:30) entries
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Stopped Task",
                                        timeRange = "01:00 - 03:00",
                                        duration = "02:00:00",
                                        hasOverlapWarning = false,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }
}
