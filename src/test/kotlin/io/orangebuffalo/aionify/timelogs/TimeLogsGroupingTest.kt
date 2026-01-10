package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Tests for grouping time log entries by title and tags within a day.
 */
class TimeLogsGroupingTest : TimeLogsPageTestBase() {
    @Test
    fun `should group completed entries with same title and tags`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create three entries with the same title and tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(3), // 3 hours ago (12:30)
                endTime = baseTime.minusHours(2).minusMinutes(30), // 2.5 hours ago (13:00)
                title = "Development Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend", "urgent"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2), // 2 hours ago (13:30)
                endTime = baseTime.minusHours(1).minusMinutes(30), // 1.5 hours ago (14:00)
                title = "Development Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("urgent", "backend"), // Same tags, different order
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(1), // 1 hour ago (14:30)
                endTime = baseTime.minusMinutes(30), // 30 minutes ago (15:00)
                title = "Development Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend", "urgent"),
            ),
        )

        // Create one entry with different tags that should not be grouped
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusMinutes(15), // 15 minutes ago (15:15)
                endTime = baseTime.minusMinutes(5), // 5 minutes ago (15:25)
                title = "Development Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("frontend"), // Different tags
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify the page state with grouped and ungrouped entries
        val expectedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:40:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:40:00",
                            entries =
                                listOf(
                                    // Ungrouped entry with different tags (most recent)
                                    EntryState(
                                        title = "Development Task",
                                        timeRange = "03:15 - 03:25",
                                        duration = "00:10:00",
                                        tags = listOf("frontend"),
                                    ),
                                    // Grouped entries with backend+urgent tags
                                    EntryState(
                                        title = "Development Task",
                                        timeRange = "00:30 - 03:00",
                                        duration = "01:30:00",
                                        tags = listOf("backend", "urgent"),
                                        isGrouped = true,
                                        groupCount = 3,
                                        groupedEntries =
                                            listOf(
                                                // Entry 3 (most recent): 14:30-15:00
                                                EntryState(
                                                    title = "Development Task",
                                                    timeRange = "02:30 - 03:00",
                                                    duration = "00:30:00",
                                                    tags = listOf("backend", "urgent"),
                                                ),
                                                // Entry 2: 13:30-14:00
                                                EntryState(
                                                    title = "Development Task",
                                                    timeRange = "01:30 - 02:00",
                                                    duration = "00:30:00",
                                                    tags = listOf("backend", "urgent"),
                                                ),
                                                // Entry 1 (earliest): 12:30-13:00
                                                EntryState(
                                                    title = "Development Task",
                                                    timeRange = "00:30 - 01:00",
                                                    duration = "00:30:00",
                                                    tags = listOf("backend", "urgent"),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should expand grouped entry to show individual entries`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create two entries with the same title and tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2), // 2 hours ago (13:30)
                endTime = baseTime.minusHours(1).minusMinutes(30), // 1.5 hours ago (14:00)
                title = "Code Review",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("review"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(1), // 1 hour ago (14:30)
                endTime = baseTime.minusMinutes(30), // 30 minutes ago (15:00)
                title = "Code Review",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("review"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify grouped entry is present
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()

        // Verify expanded entries are not visible initially
        assertThat(page.locator("[data-testid='grouped-entries-expanded']")).not().isVisible()

        // Click the count badge to expand
        groupedEntry.locator("[data-testid='entry-count-badge']").click()

        // Verify expanded entries are now visible
        val expandedContainer = page.locator("[data-testid='grouped-entries-expanded']")
        assertThat(expandedContainer).isVisible()

        // Verify there are 2 individual entries in the expanded view
        val expandedEntries = expandedContainer.locator("[data-testid='time-entry']")
        assertThat(expandedEntries).hasCount(2)

        // Verify entries are in reverse chronological order (most recent first)
        val timeRanges = expandedEntries.locator("[data-testid='entry-time-range']")
        assertThat(timeRanges).containsText(arrayOf("02:30 - 03:00", "01:30 - 02:00"))

        // Verify durations for each entry
        val durations = expandedEntries.locator("[data-testid='entry-duration']")
        assertThat(durations).containsText(arrayOf("00:30:00", "00:30:00"))

        // Verify title IS shown for detailed entries (changed from hideTitle=true to false)
        assertThat(expandedEntries.first().locator("[data-testid='entry-title']")).isVisible()
        assertThat(expandedEntries.first().locator("[data-testid='entry-title']")).containsText("Code Review")

        // Verify tags are NOT shown for detailed entries
        assertThat(expandedEntries.first().locator("[data-testid='entry-tags']")).not().isVisible()

        // Verify continue button is NOT visible on detailed entries
        assertThat(expandedEntries.first().locator("[data-testid='continue-button']")).not().isVisible()

        // Verify burger menu IS visible on detailed entries
        assertThat(expandedEntries.first().locator("[data-testid='entry-menu-button']")).isVisible()
        assertThat(expandedEntries.nth(1).locator("[data-testid='entry-menu-button']")).isVisible()

        // Click the count badge again to collapse
        groupedEntry.locator("[data-testid='entry-count-badge']").click()

        // Verify expanded entries are hidden again
        assertThat(page.locator("[data-testid='grouped-entries-expanded']")).not().isVisible()
    }

    @Test
    fun `should group entries including active entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create one completed entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2), // 2 hours ago (13:30)
                endTime = baseTime.minusHours(1).minusMinutes(30), // 1.5 hours ago (14:00)
                title = "Meeting Notes",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("meeting"),
            ),
        )

        // Create an active entry with same title and tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusMinutes(30), // 30 minutes ago (15:00)
                endTime = null, // Active entry
                title = "Meeting Notes",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("meeting"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify active entry panel shows the active entry
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()

        // Verify grouped entry is shown in the day entries
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()

        // Verify count badge shows 2 entries
        assertThat(groupedEntry.locator("[data-testid='entry-count-badge']")).containsText("2")

        // Verify time range shows earliest start to "in progress"
        assertThat(groupedEntry.locator("[data-testid='entry-time-range']")).containsText("01:30 - in progress")

        // Verify total duration includes both completed and active entry
        // The duration should be 30:00 (completed) + 30:00 (active) = 01:00:00
        // Note: This updates continuously as the active entry duration increases
        assertThat(groupedEntry.locator("[data-testid='entry-duration']")).containsText("01:00:00")

        // Expand to verify both entries are shown
        groupedEntry.locator("[data-testid='entry-count-badge']").click()
        val expandedEntries = page.locator("[data-testid='grouped-entries-expanded']").locator("[data-testid='time-entry']")
        assertThat(expandedEntries).hasCount(2)

        // Verify the active entry shows "in progress"
        val timeRanges = expandedEntries.locator("[data-testid='entry-time-range']")
        assertThat(timeRanges.first()).containsText("in progress")
    }

    @Test
    fun `should update grouped entry duration automatically when it contains active entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create one completed entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2), // 2 hours ago (13:30)
                endTime = baseTime.minusHours(1).minusMinutes(30), // 1.5 hours ago (14:00)
                title = "Meeting Notes",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("meeting"),
            ),
        )

        // Create an active entry with same title and tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusMinutes(30), // 30 minutes ago (15:00)
                endTime = null, // Active entry
                title = "Meeting Notes",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("meeting"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state: grouped entry shows 1 hour total (30 min completed + 30 min active)
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Meeting Notes",
                        duration = "00:30:00",
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
                                        title = "Meeting Notes",
                                        timeRange = "01:30 - in progress",
                                        duration = "01:00:00",
                                        tags = listOf("meeting"),
                                        isGrouped = true,
                                        groupCount = 2,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Advance the clock by 10 minutes
        timeLogsPage.advanceClock(10 * 60 * 1000)

        // Verify grouped entry duration and day total both increased by 10 minutes
        val state10min =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Meeting Notes",
                        duration = "00:40:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:10:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:10:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Meeting Notes",
                                        timeRange = "01:30 - in progress",
                                        duration = "01:10:00",
                                        tags = listOf("meeting"),
                                        isGrouped = true,
                                        groupCount = 2,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(state10min)

        // Advance another 20 minutes to reach 1 hour active time
        timeLogsPage.advanceClock(20 * 60 * 1000)

        // Verify grouped entry duration and day total show 1:30 total (30 min completed + 1 hour active)
        val state1hour =
            state10min.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Meeting Notes",
                        duration = "01:00:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:30:00"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Meeting Notes",
                                        timeRange = "01:30 - in progress",
                                        duration = "01:30:00",
                                        tags = listOf("meeting"),
                                        isGrouped = true,
                                        groupCount = 2,
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(state1hour)
    }

    @Test
    fun `should allow clicking continue on grouped entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create two completed entries with same title and tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2), // 2 hours ago
                endTime = baseTime.minusHours(1).minusMinutes(30), // 1.5 hours ago
                title = "Documentation",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("docs"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(1), // 1 hour ago
                endTime = baseTime.minusMinutes(30), // 30 minutes ago
                title = "Documentation",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("docs"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify no active entry initially
        assertThat(page.locator("[data-testid='active-timer']")).not().isVisible()

        // Find and click continue button on the grouped entry
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        groupedEntry.locator("[data-testid='continue-button']").click()

        // Verify a new active entry is started with the same title
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()
        assertThat(page.locator("[data-testid='current-entry-panel']")).containsText("Documentation")

        // Verify the active entry appears in the day group
        // Now there should be a grouped entry with 3 entries (2 completed + 1 active)
        assertThat(groupedEntry.locator("[data-testid='entry-count-badge']")).containsText("3")
    }

    @Test
    fun `should not group entries with different titles`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create entries with same tags but different titles
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2),
                endTime = baseTime.minusHours(1).minusMinutes(30),
                title = "Task A",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(1),
                endTime = baseTime.minusMinutes(30),
                title = "Task B",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify no grouped entries
        assertThat(page.locator("[data-testid='grouped-time-entry']")).hasCount(0)

        // Verify two separate regular entries
        val regularEntries = page.locator("[data-testid='time-entry']")
        assertThat(regularEntries).hasCount(2)

        // Verify titles
        val titles = regularEntries.locator("[data-testid='entry-title']")
        assertThat(titles).containsText(arrayOf("Task B", "Task A"))
    }

    @Test
    fun `should not group entries with different tags`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create entries with same title but different tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2),
                endTime = baseTime.minusHours(1).minusMinutes(30),
                title = "Development",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend", "urgent"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(1),
                endTime = baseTime.minusMinutes(30),
                title = "Development",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("backend"), // Missing "urgent" tag
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify no grouped entries
        assertThat(page.locator("[data-testid='grouped-time-entry']")).hasCount(0)

        // Verify two separate regular entries
        assertThat(page.locator("[data-testid='time-entry']")).hasCount(2)
    }

    @Test
    fun `should group entries with no tags`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create entries with same title and no tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2),
                endTime = baseTime.minusHours(1).minusMinutes(30),
                title = "Planning",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(1),
                endTime = baseTime.minusMinutes(30),
                title = "Planning",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray(),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify grouped entry is created
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        assertThat(groupedEntry).isVisible()
        assertThat(groupedEntry.locator("[data-testid='entry-count-badge']")).containsText("2")

        // Verify no tags are displayed
        assertThat(groupedEntry.locator("[data-testid='entry-tags']")).not().isVisible()
    }

    @Test
    fun `should allow editing and deleting individual entries in expanded grouped view`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create two entries with same title and tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(2),
                endTime = baseTime.minusHours(1).minusMinutes(30),
                title = "Testing",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("qa"),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusHours(1),
                endTime = baseTime.minusMinutes(30),
                title = "Testing",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("qa"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Expand the grouped entry
        val groupedEntry = page.locator("[data-testid='grouped-time-entry']")
        groupedEntry.locator("[data-testid='entry-count-badge']").click()

        // Find the first expanded entry and open its menu
        val expandedEntries = page.locator("[data-testid='grouped-entries-expanded']").locator("[data-testid='time-entry']")
        expandedEntries.first().locator("[data-testid='entry-menu-button']").click()

        // Verify edit and delete options are available
        assertThat(page.locator("[data-testid='edit-menu-item']")).isVisible()
        assertThat(page.locator("[data-testid='delete-menu-item']")).isVisible()

        // Click delete
        page.locator("[data-testid='delete-menu-item']").click()

        // Confirm deletion
        assertThat(page.locator("[data-testid='confirm-delete-button']")).isVisible()
        page.locator("[data-testid='confirm-delete-button']").click()

        // Wait for dialog to close
        assertThat(page.locator("[data-testid='confirm-delete-button']")).not().isVisible()

        // Verify only one entry remains and it's now a regular entry (not grouped)
        assertThat(page.locator("[data-testid='grouped-time-entry']")).not().isVisible()
        assertThat(page.locator("[data-testid='time-entry']")).hasCount(1)
    }
}
