package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for tag display on time entries.
 */
class TimeLogsTagsTest : TimeLogsPageTestBase() {
    @Test
    fun `should display tags on time entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create entries with various tag configurations
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("01:30"), // 2 hours ago
                endTime = baseTime.withLocalTime("02:00"), // 1.5 hours ago
                title = "Entry with Multiple Tags",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("work", "urgent", "frontend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:00"), // 1.5 hours ago
                endTime = baseTime.withLocalTime("02:30"), // 1 hour ago
                title = "Entry with Single Tag",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"), // 1 hour ago
                endTime = baseTime.withLocalTime("03:00"), // 30 minutes ago
                title = "Entry without Tags",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify entries are displayed with their tags
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Entry without Tags",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                        tags = emptyList(),
                                    ),
                                    EntryState(
                                        title = "Entry with Single Tag",
                                        timeRange = "02:00 - 02:30",
                                        duration = "00:30:00",
                                        tags = listOf("backend"),
                                    ),
                                    EntryState(
                                        title = "Entry with Multiple Tags",
                                        timeRange = "01:30 - 02:00",
                                        duration = "00:30:00",
                                        tags = listOf("frontend", "urgent", "work"),
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should copy tags when starting from existing entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry with tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"), // 1 hour ago
                endTime = baseTime.withLocalTime("03:00"), // 30 minutes ago
                title = "Task with Tags",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend", "urgent", "database"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify the stopped entry is displayed with tags
        val stoppedEntryState =
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
                                        title = "Task with Tags",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                        tags = listOf("backend", "database", "urgent"),
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(stoppedEntryState)

        // Click continue button to start a new entry from this one
        timeLogsPage.clickContinueForEntry("Task with Tags")

        // Wait for the new entry to be created
        page.waitForSelector("[data-testid='active-timer']")

        // Verify the new active entry was created with the same title and tags in the database
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(activeEntry)
        assertEquals("Task with Tags", activeEntry!!.title)
        // Verify tags were copied
        assertEquals(setOf("backend", "urgent", "database"), activeEntry.tags.toSet())
    }
}
