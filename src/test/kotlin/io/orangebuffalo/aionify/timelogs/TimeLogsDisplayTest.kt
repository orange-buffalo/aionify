package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Tests for time logs page display, rendering, and locale-specific formatting.
 */
class TimeLogsDisplayTest : TimeLogsPageTestBase() {
    @Test
    fun `should display time logs page with navigation`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Assert initial empty page state - using default state
        timeLogsPage.assertPageState(TimeLogsPageState())
    }

    @Test
    fun `should show no entries message when week is empty`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify empty state
        val emptyState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:00:00"),
                dayGroups = emptyList(),
            )
        timeLogsPage.assertPageState(emptyState)
    }

    @Test
    fun `should render entries from multiple days with varying entry counts`() {
        // FIXED_TEST_TIME is Saturday, March 16, 2024 at 03:30 NZDT
        // Let's create entries for different days in the current week (Mon Mar 11 - Sun Mar 17)

        // Monday (Mar 11) - 2 entries (5 days before Saturday)
        val monday = FIXED_TEST_TIME.minusSeconds(5 * 24 * 3600) // 5 days before Saturday
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = monday.minusHours(2),
                endTime = monday.minusHours(1).minusMinutes(30),
                title = "Monday Task 1",
                ownerId = requireNotNull(testUser.id),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = monday.minusHours(1),
                endTime = monday.minusMinutes(30),
                title = "Monday Task 2",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Tuesday (Mar 12) - 1 entry (4 days before Saturday)
        val tuesday = FIXED_TEST_TIME.minusSeconds(4 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = tuesday.minusHours(1),
                endTime = tuesday.minusMinutes(30),
                title = "Tuesday Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Wednesday (Mar 13) - 3 entries (3 days before Saturday)
        val wednesday = FIXED_TEST_TIME.minusSeconds(3 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusHours(3),
                endTime = wednesday.minusHours(2).minusMinutes(30),
                title = "Wednesday Task 1",
                ownerId = requireNotNull(testUser.id),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusHours(2),
                endTime = wednesday.minusHours(1).minusMinutes(30),
                title = "Wednesday Task 2",
                ownerId = requireNotNull(testUser.id),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusHours(1),
                endTime = wednesday.minusMinutes(30),
                title = "Wednesday Task 3",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Saturday (Mar 16) - today - 1 entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusHours(1),
                endTime = FIXED_TEST_TIME.minusMinutes(30),
                title = "Saturday Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify all days and entries are displayed correctly
        // Weekly total: 1:00:00 (Monday) + 0:30:00 (Tuesday) + 1:30:00 (Wednesday) + 0:30:00 (Saturday) = 3:30:00
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "03:30:00"),
                dayGroups =
                    listOf(
                        // Saturday (today) - 1 entry
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Saturday Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                        // Wednesday - 3 entries (in reverse chronological order within the day)
                        DayGroupState(
                            displayTitle = "Wednesday, 13 Mar",
                            totalDuration = "01:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Wednesday Task 3",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                    EntryState(
                                        title = "Wednesday Task 2",
                                        timeRange = "01:30 - 02:00",
                                        duration = "00:30:00",
                                    ),
                                    EntryState(
                                        title = "Wednesday Task 1",
                                        timeRange = "00:30 - 01:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                        // Tuesday - 1 entry
                        DayGroupState(
                            displayTitle = "Tuesday, 12 Mar",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Tuesday Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                        // Monday - 2 entries
                        DayGroupState(
                            displayTitle = "Monday, 11 Mar",
                            totalDuration = "01:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Monday Task 2",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                    EntryState(
                                        title = "Monday Task 1",
                                        timeRange = "01:30 - 02:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }
}
