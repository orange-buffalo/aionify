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
        // Set base time to ensure consistent test behavior
        setBaseTime("2024-03-16", "03:30")

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Assert initial empty page state - using default state
        timeLogsPage.assertPageState(TimeLogsPageState())
    }

    @Test
    fun `should show no entries message when week is empty`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        setBaseTime("2024-03-16", "03:30")

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
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT
        // Let's create entries for different days in the current week (Mon Mar 11 - Sun Mar 17)

        // Monday (Mar 11) - 2 entries
        val monday = timeInTestTz("2024-03-11", "03:30")
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = monday.withLocalTime("01:30"),
                endTime = monday.withLocalTime("02:00"),
                title = "Monday Task 1",
                ownerId = requireNotNull(testUser.id),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = monday.withLocalTime("02:30"),
                endTime = monday.withLocalTime("03:00"),
                title = "Monday Task 2",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Tuesday (Mar 12) - 1 entry
        val tuesday = timeInTestTz("2024-03-12", "03:30")
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = tuesday.withLocalTime("02:30"),
                endTime = tuesday.withLocalTime("03:00"),
                title = "Tuesday Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Wednesday (Mar 13) - 3 entries
        val wednesday = timeInTestTz("2024-03-13", "03:30")
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.withLocalTime("00:30"),
                endTime = wednesday.withLocalTime("01:00"),
                title = "Wednesday Task 1",
                ownerId = requireNotNull(testUser.id),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.withLocalTime("01:30"),
                endTime = wednesday.withLocalTime("02:00"),
                title = "Wednesday Task 2",
                ownerId = requireNotNull(testUser.id),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.withLocalTime("02:30"),
                endTime = wednesday.withLocalTime("03:00"),
                title = "Wednesday Task 3",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Saturday (Mar 16) - today - 1 entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
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
