package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeEntry
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Playwright tests for the Time Logs page functionality.
 * 
 * These tests use a page object model with comprehensive state assertions
 * to ensure reliable and clear test feedback.
 */
@MicronautTest(transactional = false)
class TimeLogsPagePlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var testUser: User
    private lateinit var timeLogsPage: TimeLogsPageObject

    @BeforeEach
    fun setupTestData() {
        testUser = testUsers.createRegularUser()
        timeLogsPage = TimeLogsPageObject(page)
    }

    @Test
    fun `should display time logs page with navigation`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Assert initial empty page state - using default state
        timeLogsPage.assertPageState(TimeLogsPageState())
    }

    @Test
    fun `should start and stop a time entry`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState()
        timeLogsPage.assertPageState(initialState)

        // Start a new entry
        timeLogsPage.fillNewEntryTitle("Test Task")
        timeLogsPage.clickStart()

        // Verify entry is started with active timer
        // Note: Active entries appear in the day groups list immediately
        val activeState = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Test Task",
                duration = "00:00:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Test Task",
                            timeRange = "14:30 - in progress",
                            duration = "00:00:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(activeState)

        // Stop the entry
        timeLogsPage.clickStop()

        // Verify we're back to ready state with the completed entry visible
        val stoppedState = activeState.copy(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Test Task",
                            timeRange = "14:30 - 14:30",
                            duration = "00:00:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(stoppedState)
    }

    @Test
    fun `should continue with an existing entry`() {
        // Create a completed entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Previous Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state with the existing entry
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Previous Task",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(initialState)

        // Click continue button
        timeLogsPage.clickContinueForEntry("Previous Task")

        // Verify the entry is started immediately with the same title
        // Note: The new active entry appears in the day groups, plus the previous completed entry
        val activeState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Previous Task",
                duration = "00:00:00",
            ),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",  // Total includes both entries
                    entries = listOf(
                        // New active entry appears first (most recent)
                        EntryState(
                            title = "Previous Task",
                            timeRange = "14:30 - in progress",
                            duration = "00:00:00"
                        ),
                        // Original completed entry
                        EntryState(
                            title = "Previous Task",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(activeState)
    }

    @Test
    fun `should delete a time entry with confirmation`() {
        // Create an entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify entry exists
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Task to Delete",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(initialState)

        // Delete the entry
        timeLogsPage.deleteEntry("Task to Delete")

        // Wait for entry to be removed from the list
        page.waitForCondition {
            page.locator("[data-testid='time-entry']:has-text('Task to Delete')").count() == 0
        }

        // Verify entry is deleted and page shows no entries
        val deletedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = emptyList(),
            errorMessageVisible = false
        )
        timeLogsPage.assertPageState(deletedState)
    }

    @Test
    fun `should navigate between weeks`() {
        // Create entries for different weeks
        val lastWeek = FIXED_TEST_TIME.minusSeconds(7 * 24 * 3600)
        
        // Current week entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "This Week Task",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Last week entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = lastWeek.minusSeconds(3600),
                endTime = lastWeek.minusSeconds(1800),
                title = "Last Week Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify current week with its entry
        val currentWeekState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "This Week Task",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(currentWeekState)

        // Navigate to previous week
        timeLogsPage.goToPreviousWeek()

        // Verify last week with its entry
        val lastWeekState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 4 - Mar 10"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Friday, Mar 8",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Last Week Task",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(lastWeekState)
        
        // Navigate back to current week
        timeLogsPage.goToNextWeek()
        
        // Verify we're back to current week
        timeLogsPage.assertPageState(currentWeekState)
    }

    @Test
    fun `should handle entry spanning two dates`() {
        // Create an entry that spans midnight
        // FIXED_TEST_TIME is 2024-03-15T14:30:00Z (Friday afternoon)
        // We'll create an entry that starts on Thursday and ends on Friday
        val yesterdayEvening = FIXED_TEST_TIME.minusSeconds(24 * 3600 - 2 * 3600) // Thursday 16:30:00Z
        val todayMorning = FIXED_TEST_TIME.minusSeconds(12 * 3600) // Friday 02:30:00Z
        
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = yesterdayEvening, // Started on Thursday evening
                endTime = todayMorning, // Ended on Friday morning
                title = "Spanning Entry",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Entry should appear split across two day groups
        val expectedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                // Friday (today) - shows portion from midnight to 02:30
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "02:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Spanning Entry",
                            timeRange = "00:00 - 02:30",
                            duration = "02:30:00"
                        )
                    )
                ),
                // Thursday - shows portion from 16:30 to 23:59:59.999 (displays as 07:29:59)
                DayGroupState(
                    displayTitle = "Yesterday",
                    totalDuration = "07:29:59",  // Due to midnight split at 23:59:59.999
                    entries = listOf(
                        EntryState(
                            title = "Spanning Entry",
                            timeRange = "16:30 - 23:59",
                            duration = "07:29:59"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should show no entries message when week is empty`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify empty state
        val emptyState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = emptyList(),
        )
        timeLogsPage.assertPageState(emptyState)
    }

    @Test
    fun `should show active entry on page load`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify active entry is shown with timer
        // Note: Active entries appear in the day groups immediately
        val activeState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Active Task",
                duration = "00:30:00",
            ),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "14:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(activeState)
    }

    @Test
    fun `should require title to start entry`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify start button is disabled without title
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(
                inputVisible = true,
                startButtonVisible = true,
                startButtonEnabled = false
            ),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = emptyList(),
        )
        timeLogsPage.assertPageState(initialState)
    }

    @Test
    fun `should allow starting entry by pressing Enter`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Fill in title and press Enter
        timeLogsPage.fillNewEntryTitle("Quick Entry")
        timeLogsPage.pressEnterInNewEntryInput()

        // Verify entry is started
        // Active entry appears in day groups immediately
        val activeState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Quick Entry",
                duration = "00:00:00",
            ),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Quick Entry",
                            timeRange = "14:30 - in progress",
                            duration = "00:00:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(activeState)
    }

    @Test
    fun `should prevent starting new entry while another is active`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify we cannot start a new entry (input and start button are hidden)
        // Active entry appears in day groups
        val activeState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Active Task",
                duration = "00:30:00",
            ),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "14:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(activeState)
    }

    @Test
    fun `should verify active task duration using clock`() {
        // Create an active entry that started 30 minutes ago (1800 seconds)
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify active timer shows the correct duration (30 minutes = 00:30:00)
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Active Task",
                duration = "00:30:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "14:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)
        
        // Advance the clock by 5 minutes
        timeLogsPage.advanceClock(5 * 60 * 1000)
        
        // Timer and day group totals should now show 35 minutes
        val state35min = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Active Task",
                duration = "00:35:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:35:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "14:00 - in progress",
                            duration = "00:35:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(state35min)
        
        // Advance another 25 minutes to reach 1 hour
        timeLogsPage.advanceClock(25 * 60 * 1000)
        
        // Timer and day group totals should now show 1 hour
        val state1hour = state35min.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Active Task",
                duration = "01:00:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "01:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "14:00 - in progress",
                            duration = "01:00:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(state1hour)
    }
    
    @Test
    fun `should render entries from multiple days with varying entry counts`() {
        // FIXED_TEST_TIME is 2024-03-15T14:30:00Z (Friday, March 15)
        // Let's create entries for different days in the current week (Mon Mar 11 - Sun Mar 17)
        
        // Monday (Mar 11) - 2 entries
        val monday = FIXED_TEST_TIME.minusSeconds(4 * 24 * 3600) // 4 days before Friday
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = monday.minusSeconds(7200),
                endTime = monday.minusSeconds(5400),
                title = "Monday Task 1",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = monday.minusSeconds(3600),
                endTime = monday.minusSeconds(1800),
                title = "Monday Task 2",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Tuesday (Mar 12) - 1 entry
        val tuesday = FIXED_TEST_TIME.minusSeconds(3 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = tuesday.minusSeconds(3600),
                endTime = tuesday.minusSeconds(1800),
                title = "Tuesday Task",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Wednesday (Mar 13) - 3 entries
        val wednesday = FIXED_TEST_TIME.minusSeconds(2 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = wednesday.minusSeconds(10800),
                endTime = wednesday.minusSeconds(9000),
                title = "Wednesday Task 1",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = wednesday.minusSeconds(7200),
                endTime = wednesday.minusSeconds(5400),
                title = "Wednesday Task 2",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = wednesday.minusSeconds(3600),
                endTime = wednesday.minusSeconds(1800),
                title = "Wednesday Task 3",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Friday (Mar 15) - today - 1 entry
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Friday Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify all days and entries are displayed correctly
        val expectedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                // Friday (today) - 1 entry
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Friday Task",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        )
                    )
                ),
                // Wednesday - 3 entries (in reverse chronological order within the day)
                DayGroupState(
                    displayTitle = "Wednesday, Mar 13",
                    totalDuration = "01:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Wednesday Task 3",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        ),
                        EntryState(
                            title = "Wednesday Task 2",
                            timeRange = "12:30 - 13:00",
                            duration = "00:30:00"
                        ),
                        EntryState(
                            title = "Wednesday Task 1",
                            timeRange = "11:30 - 12:00",
                            duration = "00:30:00"
                        )
                    )
                ),
                // Tuesday - 1 entry
                DayGroupState(
                    displayTitle = "Tuesday, Mar 12",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Tuesday Task",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        )
                    )
                ),
                // Monday - 2 entries
                DayGroupState(
                    displayTitle = "Monday, Mar 11",
                    totalDuration = "01:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Monday Task 2",
                            timeRange = "13:30 - 14:00",
                            duration = "00:30:00"
                        ),
                        EntryState(
                            title = "Monday Task 1",
                            timeRange = "12:30 - 13:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(expectedState)
    }
    
    @Test
    fun `should handle midnight split scenario correctly`() {
        // FIXED_TEST_TIME is 2024-03-15T14:30:00Z (Friday afternoon)
        // Create an entry that spans from Thursday 23:00 UTC to Friday 01:00 UTC
        val thursdayEvening = FIXED_TEST_TIME.minusSeconds(55800) // Thursday 23:00 UTC (15.5 hours before)
        val fridayMorning = FIXED_TEST_TIME.minusSeconds(48600) // Friday 01:00 UTC (13.5 hours before)
        
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = thursdayEvening,
                endTime = fridayMorning,
                title = "Midnight Spanning Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify the entry appears split across 2 day groups
        val expectedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                // Friday - portion from 00:00 to 01:00
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "01:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Midnight Spanning Task",
                            timeRange = "00:00 - 01:00",
                            duration = "01:00:00"
                        )
                    )
                ),
                // Thursday - portion from 23:00 to 23:59:59.999 (displays as 00:59:59)
                DayGroupState(
                    displayTitle = "Yesterday",
                    totalDuration = "00:59:59",  // Due to midnight split at 23:59:59.999
                    entries = listOf(
                        EntryState(
                            title = "Midnight Spanning Task",
                            timeRange = "23:00 - 23:59",
                            duration = "00:59:59"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(expectedState)
    }
    
    @Test
    fun `should delete midnight-split entry correctly`() {
        // Create an entry that spans midnight
        val thursdayEvening = FIXED_TEST_TIME.minusSeconds(55800) // Thursday 23:00 UTC (15.5 hours before)
        val fridayMorning = FIXED_TEST_TIME.minusSeconds(48600) // Friday 01:00 UTC (13.5 hours before)
        
        testDatabaseSupport.insert(
            TimeEntry(
                startTime = thursdayEvening,
                endTime = fridayMorning,
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state with split entry across two days
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = listOf(
                // Friday portion
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "01:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Task to Delete",
                            timeRange = "00:00 - 01:00",
                            duration = "01:00:00"
                        )
                    )
                ),
                // Thursday portion (23:00 to 23:59:59.999 displays as 00:59:59)
                DayGroupState(
                    displayTitle = "Yesterday",
                    totalDuration = "00:59:59",  // Due to midnight split at 23:59:59.999
                    entries = listOf(
                        EntryState(
                            title = "Task to Delete",
                            timeRange = "23:00 - 23:59",
                            duration = "00:59:59"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(initialState)

        // Delete the entry (deleting from first occurrence)
        timeLogsPage.deleteEntry("Task to Delete")

        // Wait for both parts to be removed
        page.waitForCondition {
            page.locator("[data-testid='time-entry']:has-text('Task to Delete')").count() == 0
        }

        // Verify both parts of the entry are deleted
        val deletedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
            dayGroups = emptyList(),
            errorMessageVisible = false
        )
        timeLogsPage.assertPageState(deletedState)
    }
}
