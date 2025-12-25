package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

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
    
    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

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
                duration = "00:00:00",
                startedAt = "16 Mar, 03:30",  // Started at FIXED_TEST_TIME (03:30)
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Test Task",
                            timeRange = "03:30 - in progress",
                            duration = "00:00:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(activeState)
        
        // Verify database state after starting
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(activeEntry, "Active entry should exist in database")
        assertEquals("Test Task", activeEntry!!.title)
        // Verify startTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, activeEntry.startTime, "Start time should be set by backend")
        assertNull(activeEntry.endTime, "Active entry should not have end time")

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
                            timeRange = "03:30 - 03:30",
                            duration = "00:00:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(stoppedState)
        
        // Verify database state after stopping
        val noActiveEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNull(noActiveEntry, "Should not have active entry after stopping")
        
        // Verify the stopped entry has correct timestamps
        val stoppedEntry = timeLogEntryRepository.findById(activeEntry.id!!).orElse(null)
        assertNotNull(stoppedEntry, "Stopped entry should still exist in database")
        assertEquals("Test Task", stoppedEntry!!.title)
        assertEquals(FIXED_TEST_TIME, stoppedEntry.startTime, "Start time should remain unchanged")
        // Verify endTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, stoppedEntry.endTime, "End time should be set by backend")
    }

    @Test
    fun `should continue with an existing entry`() {
        // Create a completed entry
        testDatabaseSupport.insert(
            TimeLogEntry(
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
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Previous Task",
                            timeRange = "02:30 - 03:00",
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
                startedAt = "16 Mar, 03:30",  // Started at FIXED_TEST_TIME (backend time)
            ),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",  // Total includes both entries
                    entries = listOf(
                        // New active entry appears first (most recent)
                        EntryState(
                            title = "Previous Task",
                            timeRange = "03:30 - in progress",  // Started at FIXED_TEST_TIME (backend time)
                            duration = "00:00:00"
                        ),
                        // Original completed entry
                        EntryState(
                            title = "Previous Task",
                            timeRange = "02:30 - 03:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(activeState)
        
        // Verify database state - new entry created with backend timestamp
        val newActiveEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(newActiveEntry, "New active entry should exist in database")
        assertEquals("Previous Task", newActiveEntry!!.title)
        // Verify startTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, newActiveEntry.startTime, "Start time should be set by backend")
        assertNull(newActiveEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should delete a time entry with confirmation`() {
        // Create an entry
        testDatabaseSupport.insert(
            TimeLogEntry(
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
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Task to Delete",
                            timeRange = "02:30 - 03:00",
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
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
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
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "This Week Task",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Last week entry
        testDatabaseSupport.insert(
            TimeLogEntry(
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
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "This Week Task",
                            timeRange = "02:30 - 03:00",
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
            weekNavigation = WeekNavigationState(weekRange = "4 Mar - 10 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Saturday, 9 Mar",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Last Week Task",
                            timeRange = "02:30 - 03:00",
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
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Entry should appear split across two day groups
        // Friday 20:00 NZDT to Saturday 02:30 NZDT (6.5 hours total)
        // Split at midnight: Friday 20:00 - 23:59:59.999 and Saturday 00:00 - 02:30
        val expectedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                // Saturday (today) - shows portion from midnight to 02:30 (2:30 duration)
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
                // Friday (yesterday) - shows portion from 20:00 to 23:59:59.999 (03:59:59 duration)
                DayGroupState(
                    displayTitle = "Yesterday",
                    totalDuration = "03:59:59",  // Due to midnight split at 23:59:59.999
                    entries = listOf(
                        EntryState(
                            title = "Spanning Entry",
                            timeRange = "20:00 - 23:59",
                            duration = "03:59:59"
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
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = emptyList(),
        )
        timeLogsPage.assertPageState(emptyState)
    }

    @Test
    fun `should show active entry on page load`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
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
                startedAt = "16 Mar, 03:00",
            ),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "03:00 - in progress",
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
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
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
                startedAt = "16 Mar, 03:30",  // Started at FIXED_TEST_TIME (backend time)
            ),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Quick Entry",
                            timeRange = "03:30 - in progress",  // Started at FIXED_TEST_TIME (backend time)
                            duration = "00:00:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(activeState)
        
        // Verify database state - entry should be created with backend timestamp
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(activeEntry, "Active entry should exist in database")
        assertEquals("Quick Entry", activeEntry!!.title)
        // Verify startTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, activeEntry.startTime, "Start time should be set by backend")
        assertNull(activeEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should prevent starting new entry while another is active`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
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
                startedAt = "16 Mar, 03:00",
            ),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "03:00 - in progress",
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
            TimeLogEntry(
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
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00",
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "03:00 - in progress",
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
                duration = "00:35:00",
                startedAt = "16 Mar, 03:00",
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:35:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "03:00 - in progress",
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
                duration = "01:00:00",
                startedAt = "16 Mar, 03:00",
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "01:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Active Task",
                            timeRange = "03:00 - in progress",
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
        // FIXED_TEST_TIME is Saturday, March 16, 2024 at 03:30 NZDT
        // Let's create entries for different days in the current week (Mon Mar 11 - Sun Mar 17)
        
        // Monday (Mar 11) - 2 entries (5 days before Saturday)
        val monday = FIXED_TEST_TIME.minusSeconds(5 * 24 * 3600) // 5 days before Saturday
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = monday.minusSeconds(7200),
                endTime = monday.minusSeconds(5400),
                title = "Monday Task 1",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = monday.minusSeconds(3600),
                endTime = monday.minusSeconds(1800),
                title = "Monday Task 2",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Tuesday (Mar 12) - 1 entry (4 days before Saturday)
        val tuesday = FIXED_TEST_TIME.minusSeconds(4 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = tuesday.minusSeconds(3600),
                endTime = tuesday.minusSeconds(1800),
                title = "Tuesday Task",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Wednesday (Mar 13) - 3 entries (3 days before Saturday)
        val wednesday = FIXED_TEST_TIME.minusSeconds(3 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusSeconds(10800),
                endTime = wednesday.minusSeconds(9000),
                title = "Wednesday Task 1",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusSeconds(7200),
                endTime = wednesday.minusSeconds(5400),
                title = "Wednesday Task 2",
                ownerId = requireNotNull(testUser.id)
            )
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusSeconds(3600),
                endTime = wednesday.minusSeconds(1800),
                title = "Wednesday Task 3",
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Saturday (Mar 16) - today - 1 entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Saturday Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify all days and entries are displayed correctly
        val expectedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                // Saturday (today) - 1 entry
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Saturday Task",
                            timeRange = "02:30 - 03:00",
                            duration = "00:30:00"
                        )
                    )
                ),
                // Wednesday - 3 entries (in reverse chronological order within the day)
                DayGroupState(
                    displayTitle = "Wednesday, 13 Mar",
                    totalDuration = "01:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Wednesday Task 3",
                            timeRange = "02:30 - 03:00",
                            duration = "00:30:00"
                        ),
                        EntryState(
                            title = "Wednesday Task 2",
                            timeRange = "01:30 - 02:00",
                            duration = "00:30:00"
                        ),
                        EntryState(
                            title = "Wednesday Task 1",
                            timeRange = "00:30 - 01:00",
                            duration = "00:30:00"
                        )
                    )
                ),
                // Tuesday - 1 entry
                DayGroupState(
                    displayTitle = "Tuesday, 12 Mar",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Tuesday Task",
                            timeRange = "02:30 - 03:00",
                            duration = "00:30:00"
                        )
                    )
                ),
                // Monday - 2 entries
                DayGroupState(
                    displayTitle = "Monday, 11 Mar",
                    totalDuration = "01:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Monday Task 2",
                            timeRange = "02:30 - 03:00",
                            duration = "00:30:00"
                        ),
                        EntryState(
                            title = "Monday Task 1",
                            timeRange = "01:30 - 02:00",
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
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify the entry appears split across 2 day groups
        // Friday 22:00 to Saturday 02:00 NZDT (4 hours total, spans midnight)
        // Split at midnight: Friday 22:00 - 23:59:59.999 and Saturday 00:00 - 02:00
        val expectedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                // Saturday (today) - portion from 00:00 to 02:00
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "02:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Midnight Spanning Task",
                            timeRange = "00:00 - 02:00",
                            duration = "02:00:00"
                        )
                    )
                ),
                // Friday (yesterday) - portion from 22:00 to 23:59:59.999 (displays as 01:59:59)
                DayGroupState(
                    displayTitle = "Yesterday",
                    totalDuration = "01:59:59",  // Due to midnight split at 23:59:59.999
                    entries = listOf(
                        EntryState(
                            title = "Midnight Spanning Task",
                            timeRange = "22:00 - 23:59",
                            duration = "01:59:59"
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
        // Friday 22:00 NZDT to Saturday 02:00 NZDT (4 hours, spans midnight)
        val fridayEvening = FIXED_TEST_TIME.minusSeconds(19800) // Friday 22:00 NZDT (5.5 hours = 19800 seconds before Saturday 03:30)
        val saturdayMorning = FIXED_TEST_TIME.minusSeconds(5400) // Saturday 02:00 NZDT (1.5 hours = 5400 seconds before Saturday 03:30)
        
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = fridayEvening,
                endTime = saturdayMorning,
                title = "Task to Delete",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state with split entry across two days
        // Entry runs from Friday 22:00 to Saturday 02:00 NZDT
        // Split at midnight: Friday 22:00 - 23:59:59.999 and Saturday 00:00 - 02:00
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                // Saturday (today) portion: 00:00 - 02:00 = 2 hours
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "02:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Task to Delete",
                            timeRange = "00:00 - 02:00",
                            duration = "02:00:00"
                        )
                    )
                ),
                // Friday (yesterday) portion: 22:00 to 23:59:59.999 (displays as 01:59:59)
                DayGroupState(
                    displayTitle = "Yesterday",
                    totalDuration = "01:59:59",  // Due to midnight split at 23:59:59.999
                    entries = listOf(
                        EntryState(
                            title = "Task to Delete",
                            timeRange = "22:00 - 23:59",
                            duration = "01:59:59"
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
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = emptyList(),
            errorMessageVisible = false
        )
        timeLogsPage.assertPageState(deletedState)
    }

    @Test
    fun `should edit active entry title`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Original Title",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial active state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Original Title",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00"
            ),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Original Title",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit button - transition to edit mode
        timeLogsPage.clickEditEntry()
        
        val editModeState = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Original Title",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00",
                editMode = EditModeState.Editing(
                    titleValue = "Original Title",
                    dateValue = "16 Mar 2024",
                    timeValue = "03:00"
                )
            )
        )
        timeLogsPage.assertPageState(editModeState)

        // Change the title
        timeLogsPage.fillEditTitle("Updated Title")

        // Save changes
        timeLogsPage.clickSaveEdit()

        // Verify entry is updated - mutation from initial state
        val updatedState = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Updated Title",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Updated Title",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(updatedState)
        
        // Verify database state - title should be updated
        val editedEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(editedEntry, "Active entry should still exist in database")
        assertEquals("Updated Title", editedEntry!!.title, "Title should be updated in database")
        assertEquals(FIXED_TEST_TIME.minusSeconds(1800), editedEntry.startTime, "Start time should remain unchanged")
        assertNull(editedEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should edit active entry start time`() {
        // Create an active entry that started 30 minutes ago
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800), // 14:00
                endTime = null,
                title = "Test Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Test Task",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Test Task",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Change start time to 1 hour ago (02:30) - need to set date explicitly to today
        timeLogsPage.fillEditDate("2024-03-16")  // Saturday (today)
        timeLogsPage.fillEditTime("02:30")

        // Save changes
        timeLogsPage.clickSaveEdit()

        // Verify the start time and duration are updated - mutation from initial state
        // Duration should now be 1 hour (from 02:30 to 03:30)
        val updatedState = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Test Task",
                duration = "01:00:00",
                startedAt = "16 Mar, 02:30"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "01:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Test Task",
                            timeRange = "02:30 - in progress",
                            duration = "01:00:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(updatedState)
        
        // Verify database state - startTime should be updated to user-provided time
        val editedEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(editedEntry, "Active entry should still exist in database")
        assertEquals("Test Task", editedEntry!!.title)
        // When user edits start time, they provide the value, so it should match what they entered
        // The user entered "02:30" in NZDT timezone, which is FIXED_TEST_TIME.minusSeconds(3600)
        assertEquals(FIXED_TEST_TIME.minusSeconds(3600), editedEntry.startTime, "Start time should be updated to user's value")
        assertNull(editedEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should edit active entry title and start time together`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800), // 14:00
                endTime = null,
                title = "Original Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Original Task",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Original Task",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Change both title and start time - need to set date explicitly to today
        timeLogsPage.fillEditTitle("Modified Task")
        timeLogsPage.fillEditDate("2024-03-16")  // Saturday (today)
        timeLogsPage.fillEditTime("01:00")

        // Save changes
        timeLogsPage.clickSaveEdit()

        // Verify both fields are updated - mutation from initial state
        // Duration should be 2.5 hours (from 01:00 to 03:30)
        val updatedState = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Modified Task",
                duration = "02:30:00",
                startedAt = "16 Mar, 01:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "02:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Modified Task",
                            timeRange = "01:00 - in progress",
                            duration = "02:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(updatedState)
        
        // Verify database state - both title and startTime should be updated
        val editedEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(editedEntry, "Active entry should still exist in database")
        assertEquals("Modified Task", editedEntry!!.title, "Title should be updated in database")
        // User entered "01:00" in NZDT timezone, which is FIXED_TEST_TIME.minusSeconds(9000)
        assertEquals(FIXED_TEST_TIME.minusSeconds(9000), editedEntry.startTime, "Start time should be updated to user's value")
        assertNull(editedEntry.endTime, "Active entry should not have end time")
    }

    @Test
    fun `should cancel editing and revert changes`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Original Title",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Original Title",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Original Title",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Make some changes
        timeLogsPage.fillEditTitle("Changed Title")
        timeLogsPage.fillEditTime("13:00")

        // Cancel editing
        timeLogsPage.clickCancelEdit()

        // Verify original state is preserved - revert to initial state
        timeLogsPage.assertPageState(initialState)
    }

    @Test
    fun `should change start time to different day`() {
        // Create an active entry that started today
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800), // Friday 14:00
                endTime = null,
                title = "Cross-Day Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Cross-Day Task",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Cross-Day Task",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Change start time to yesterday (Friday)
        timeLogsPage.fillEditDate("2024-03-15")
        timeLogsPage.fillEditTime("05:00")

        // Save changes
        timeLogsPage.clickSaveEdit()

        // Verify the entry now appears in yesterday's group
        // Duration should be from Friday 05:00 to Saturday 03:30 NZDT = 22:30:00
        // This spans midnight, so it's split: Yesterday (05:00-23:59:59.999) + Today (00:00-03:30)
        // The UI rounds the split to whole seconds: Yesterday: 18:59:59, Today: 03:30:00
        val updatedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Cross-Day Task",
                duration = "22:30:00",  // Total duration from Friday 05:00 to Saturday 03:30
                startedAt = "15 Mar, 05:00"  // Start time from yesterday
            ),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                // Entry should span across two days after the update
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "03:30:00", // From midnight to 03:30
                    entries = listOf(
                        EntryState(
                            title = "Cross-Day Task",
                            timeRange = "00:00 - in progress",
                            duration = "03:30:00"
                        )
                    )
                ),
                DayGroupState(
                    displayTitle = "Yesterday",
                    totalDuration = "18:59:59", // 05:00 to midnight (23:59:59.999 rounds to 18:59:59)
                    entries = listOf(
                        EntryState(
                            title = "Cross-Day Task",
                            timeRange = "05:00 - 23:59",
                            duration = "18:59:59"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(updatedState)
    }

    @Test
    fun `should prevent saving edit with empty title`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Valid Title",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Valid Title",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Valid Title",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Verify edit mode state
        val editModeState = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Valid Title",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00",
                editMode = EditModeState.Editing(
                    titleValue = "Valid Title",
                    dateValue = "16 Mar 2024",
                    timeValue = "03:00"
                )
            )
        )
        timeLogsPage.assertPageState(editModeState)

        // Clear the title
        timeLogsPage.fillEditTitle("")

        // Verify save button is disabled with empty title
        val editWithEmptyTitleState = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Valid Title",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00",
                editMode = EditModeState.Editing(
                    titleValue = "",
                    dateValue = "16 Mar 2024",
                    timeValue = "03:00",
                    saveButtonEnabled = false
                )
            )
        )
        timeLogsPage.assertPageState(editWithEmptyTitleState)
    }

    @Test
    fun `should show error when setting start time in future`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Test Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Test Task",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00"
            ),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Test Task",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit button
        timeLogsPage.clickEditEntry()

        // Set start time to future (tomorrow)
        timeLogsPage.fillEditDate("2024-03-16")
        timeLogsPage.fillEditTime("10:00")

        // Try to save
        timeLogsPage.clickSaveEdit()

        // Verify error is shown and we remain in edit mode
        val errorState = initialState.copy(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Test Task",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00",
                editMode = EditModeState.Editing(
                    titleValue = "Test Task",
                    dateValue = "16 Mar 2024",
                    timeValue = "10:00",
                    saveButtonEnabled = true
                )
            ),
            errorMessageVisible = true,
            errorMessage = "Start time cannot be in the future"
        )
        timeLogsPage.assertPageState(errorState)
    }

    @Test
    fun `should hide edit button when not in active state`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify edit button is not visible when no active entry
        val noActiveEntryState = TimeLogsPageState()
        timeLogsPage.assertPageState(noActiveEntryState)
    }

    @ParameterizedTest
    @MethodSource("localeTestCases")
    fun `should display dates and times according to user locale`(testCase: LocaleTestCase) {
        // Create a user with the specified locale
        val testUser = testUsers.createUserWithLocale(
            username = testCase.username,
            greeting = testCase.greeting,
            locale = java.util.Locale.forLanguageTag(testCase.localeTag)
        )
        
        // Create an entry that started at 14:30 (2:30 PM) - afternoon time to clearly show 12/24-hour format difference
        // FIXED_TEST_TIME is Saturday, March 16, 2024 at 03:30:00 NZDT
        // We want 14:30 NZDT, which is 11 hours later
        val afternoonTime = FIXED_TEST_TIME.plusSeconds(11 * 3600) // 14:30 NZDT
        
        // Set the browser clock to the afternoon time for this test
        page.clock().pauseAt(afternoonTime.toEpochMilli())
        
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = afternoonTime.minusSeconds(1800), // Started 30 minutes ago at 14:00 (2:00 PM)
                endTime = null,
                title = testCase.taskTitle,
                ownerId = requireNotNull(testUser.id)
            )
        )
        
        // Also create a completed entry to test time ranges in the day groups
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = afternoonTime.minusSeconds(7200), // Started 2 hours ago at 12:30 (12:30 PM)
                endTime = afternoonTime.minusSeconds(5400), // Ended 1.5 hours ago at 13:00 (1:00 PM)
                title = testCase.completedTaskTitle,
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify the active entry "started at" label uses locale format
        val startedAtLocator = page.locator("[data-testid='active-entry-started-at']")
        assertThat(startedAtLocator).isVisible()
        assertThat(startedAtLocator).containsText(testCase.expectedStartedAtText)
        
        // Verify the active entry time range in day groups uses locale format
        val activeEntryTimeRange = page.locator("[data-testid='time-entry']:has-text('${testCase.taskTitle}')")
            .locator("[data-testid='entry-time-range']")
        assertThat(activeEntryTimeRange).isVisible()
        assertThat(activeEntryTimeRange).hasText(testCase.expectedActiveTimeRangeText)
        
        // Verify the completed entry time range in day groups uses locale format
        val completedEntryTimeRange = page.locator("[data-testid='time-entry']:has-text('${testCase.completedTaskTitle}')")
            .locator("[data-testid='entry-time-range']")
        assertThat(completedEntryTimeRange).isVisible()
        assertThat(completedEntryTimeRange).hasText(testCase.expectedCompletedTimeRangeText)
        
        // Verify week range uses locale format
        val weekRangeLocator = page.locator("[data-testid='week-range']")
        assertThat(weekRangeLocator).isVisible()
        assertThat(weekRangeLocator).hasText(testCase.expectedWeekRangeText)
        
        // Verify day title uses locale format
        val dayTitleLocator = page.locator("[data-testid='day-title']").first()
        assertThat(dayTitleLocator).isVisible()
        assertThat(dayTitleLocator).hasText(testCase.expectedDayTitle)
        
        // Click edit button to test datetime picker with the locale
        timeLogsPage.clickEditEntry()

        // Verify edit mode date input uses locale format
        val dateInput = page.locator("[data-testid='edit-date-input']")
        assertThat(dateInput).isVisible()
        assertThat(dateInput).hasValue(testCase.expectedEditDateValue)
        
        // Verify edit mode time input uses locale format
        val timeInput = page.locator("[data-testid='edit-time-input']")
        assertThat(timeInput).isVisible()
        assertThat(timeInput).hasValue(testCase.expectedEditTimeValue)
    }
    
    companion object {
        @JvmStatic
        fun localeTestCases() = listOf(
            // US locale - 12-hour format with AM/PM
            LocaleTestCase(
                localeTag = "en-US",
                username = "us_user",
                greeting = "US User",
                taskTitle = "Active Task",
                completedTaskTitle = "Completed Task",
                expectedStartedAtText = "Mar 16, 02:00 PM", // 14:00 in 12-hour format
                expectedActiveTimeRangeText = "02:00 PM - in progress", // 14:00 in 12-hour format
                expectedCompletedTimeRangeText = "12:30 PM - 01:00 PM", // 12:30 - 13:00 in 12-hour format
                expectedWeekRangeText = "Mar 11 - Mar 17",
                expectedDayTitle = "Today",
                expectedEditDateValue = "Mar 16, 2024",
                expectedEditTimeValue = "02:00 PM"
            ),
            // UK locale - 24-hour format
            LocaleTestCase(
                localeTag = "en-GB",
                username = "uk_user",
                greeting = "UK User",
                taskTitle = "Active Task",
                completedTaskTitle = "Completed Task",
                expectedStartedAtText = "16 Mar, 14:00", // 14:00 in 24-hour format
                expectedActiveTimeRangeText = "14:00 - in progress", // 14:00 in 24-hour format
                expectedCompletedTimeRangeText = "12:30 - 13:00", // 12:30 - 13:00 in 24-hour format
                expectedWeekRangeText = "11 Mar - 17 Mar",
                expectedDayTitle = "Today",
                expectedEditDateValue = "16 Mar 2024",
                expectedEditTimeValue = "14:00"
            ),
            // Ukrainian locale - 24-hour format
            LocaleTestCase(
                localeTag = "uk",
                username = "ua_user",
                greeting = "Українець",
                taskTitle = "Українське завдання",
                completedTaskTitle = "Завершене завдання",
                expectedStartedAtText = "16 бер., 14:00", // 14:00 in 24-hour format with Ukrainian month
                expectedActiveTimeRangeText = "14:00 - виконується", // 14:00 in 24-hour format
                expectedCompletedTimeRangeText = "12:30 - 13:00", // 12:30 - 13:00 in 24-hour format
                expectedWeekRangeText = "11 бер. - 17 бер.",
                expectedDayTitle = "Сьогодні",
                expectedEditDateValue = "16 бер. 2024 р.",
                expectedEditTimeValue = "14:00"
            )
        )
    }
    
    data class LocaleTestCase(
        val localeTag: String,
        val username: String,
        val greeting: String,
        val taskTitle: String,
        val completedTaskTitle: String,
        val expectedStartedAtText: String,
        val expectedActiveTimeRangeText: String,
        val expectedCompletedTimeRangeText: String,
        val expectedWeekRangeText: String,
        val expectedDayTitle: String,
        val expectedEditDateValue: String,
        val expectedEditTimeValue: String
    )

    @Test
    fun `should start from existing entry when active entry exists by stopping active entry first`() {
        // Create a completed entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200), // 2 hours ago (12:30)
                endTime = FIXED_TEST_TIME.minusSeconds(5400), // 1.5 hours ago (13:00)
                title = "Completed Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        // Create an active entry
        val previouslyActiveEntry = testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800), // 30 minutes ago (03:00)
                endTime = null,
                title = "Currently Active Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state with both active and completed entries
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Currently Active Task",
                duration = "00:30:00",
                startedAt = "16 Mar, 03:00",
            ),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "01:00:00", // 30 min active + 30 min completed
                    entries = listOf(
                        EntryState(
                            title = "Currently Active Task",
                            timeRange = "03:00 - in progress",
                            duration = "00:30:00"
                        ),
                        EntryState(
                            title = "Completed Task",
                            timeRange = "01:30 - 02:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(initialState)

        // Click start button on the completed entry
        // This should:
        // 1. Stop the currently active entry
        // 2. Start a new entry with the completed entry's title
        timeLogsPage.clickContinueForEntry("Completed Task")

        // Verify the new state:
        // - The previously active entry should now be stopped
        // - A new active entry with "Completed Task" title should be started
        val newState = TimeLogsPageState(
            currentEntry = CurrentEntryState.ActiveEntry(
                title = "Completed Task",
                duration = "00:00:00",
                startedAt = "16 Mar, 03:30",  // Started at FIXED_TEST_TIME (backend time)
            ),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "01:00:00", // All entries combined
                    entries = listOf(
                        // New active entry (most recent)
                        EntryState(
                            title = "Completed Task",
                            timeRange = "03:30 - in progress",  // Started at FIXED_TEST_TIME (backend time)
                            duration = "00:00:00"
                        ),
                        // Previously active entry, now stopped
                        EntryState(
                            title = "Currently Active Task",
                            timeRange = "03:00 - 03:30",  // Stopped at FIXED_TEST_TIME (backend time)
                            duration = "00:30:00"
                        ),
                        // Original completed entry
                        EntryState(
                            title = "Completed Task",
                            timeRange = "01:30 - 02:00",
                            duration = "00:30:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(newState)
        
        // Verify database state:
        // 1. The previously active entry should be stopped with endTime from backend
        val stoppedPreviousEntry = timeLogEntryRepository.findById(previouslyActiveEntry.id!!).orElse(null)
        assertNotNull(stoppedPreviousEntry, "Previously active entry should exist")
        assertEquals(FIXED_TEST_TIME.minusSeconds(1800), stoppedPreviousEntry!!.startTime)
        // Verify endTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, stoppedPreviousEntry.endTime, "End time should be set by backend when stopping")
        
        // 2. A new active entry should be created with startTime from backend
        val newActiveEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertNotNull(newActiveEntry, "New active entry should exist in database")
        assertEquals("Completed Task", newActiveEntry!!.title)
        // Verify startTime is set by backend to FIXED_TEST_TIME
        assertEquals(FIXED_TEST_TIME, newActiveEntry.startTime, "Start time should be set by backend")
        assertNull(newActiveEntry.endTime, "New active entry should not have end time")
    }

    @Test
    fun `should show Sunday entry in current week when viewing on Sunday`() {
        // FIXED_TEST_TIME is Friday, March 15, 2024 at 14:30:00 UTC = Saturday, March 16, 2024 at 03:30:00 NZDT
        // Sunday, March 17, 2024 at 14:30:00 UTC = Monday, March 18, 2024 at 03:30:00 NZDT
        // To test Sunday in Auckland, we need Sunday at 03:30 NZDT = Saturday at 14:30 UTC
        val sundayTime = FIXED_TEST_TIME.plusSeconds(24 * 3600) // Saturday 14:30 UTC = Sunday 03:30 NZDT
        
        // Override the test time to Sunday
        page.clock().pauseAt(sundayTime.toEpochMilli())
        
        // Create an entry for Sunday in Auckland timezone
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = sundayTime.minusSeconds(1800), // 30 minutes ago (Saturday 14:00 UTC = Sunday 03:00 NZDT)
                endTime = sundayTime.minusSeconds(900), // 15 minutes ago (Saturday 14:15 UTC = Sunday 03:15 NZDT)
                title = "Sunday Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Expected: The Sunday entry should appear in the "Today" section
        // Week should be Monday Mar 11 - Sunday Mar 17 (in Auckland timezone)
        val expectedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:15:00",
                    entries = listOf(
                        EntryState(
                            title = "Sunday Task",
                            timeRange = "03:00 - 03:15",
                            duration = "00:15:00"
                        )
                    )
                )
            ),
        )
        timeLogsPage.assertPageState(expectedState)
    }

    @Test
    fun `should edit stopped entry title`() {
        // Create a stopped entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Original Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Original Task",
                            timeRange = "02:30 - 03:00",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Original Task")

        // Wait for edit form to appear
        assertThat(page.locator("[data-testid='time-entry-edit']")).isVisible()

        // Change the title
        timeLogsPage.fillStoppedEntryEditTitle("Updated Task")

        // Save changes
        timeLogsPage.clickSaveStoppedEntryEdit()

        // Wait for edit form to disappear
        assertThat(page.locator("[data-testid='time-entry-edit']")).not().isVisible()

        // Verify the entry is updated
        val updatedState = initialState.copy(
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Updated Task",
                            timeRange = "02:30 - 03:00",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(updatedState)
    }

    @Test
    fun `should edit stopped entry start and end times`() {
        // Create a stopped entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600), // 02:30
                endTime = FIXED_TEST_TIME.minusSeconds(1800),   // 03:00
                title = "Task to Edit",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Task to Edit")

        // Wait for edit form to appear
        assertThat(page.locator("[data-testid='time-entry-edit']")).isVisible()

        // Change start time to 01:00 and end time to 02:00
        timeLogsPage.fillStoppedEntryEditStartDate("2024-03-16")
        timeLogsPage.fillStoppedEntryEditStartTime("01:00")
        timeLogsPage.fillStoppedEntryEditEndDate("2024-03-16")
        timeLogsPage.fillStoppedEntryEditEndTime("02:00")

        // Save changes
        timeLogsPage.clickSaveStoppedEntryEdit()

        // Wait for edit form to disappear
        assertThat(page.locator("[data-testid='time-entry-edit']")).not().isVisible()

        // Verify the entry is updated with new times and duration (1 hour)
        val updatedState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "01:00:00",
                    entries = listOf(
                        EntryState(
                            title = "Task to Edit",
                            timeRange = "01:00 - 02:00",
                            duration = "01:00:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(updatedState)
    }

    @Test
    fun `should cancel editing stopped entry`() {
        // Create a stopped entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Original Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState = TimeLogsPageState(
            currentEntry = CurrentEntryState.NoActiveEntry(),
            weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
            dayGroups = listOf(
                DayGroupState(
                    displayTitle = "Today",
                    totalDuration = "00:30:00",
                    entries = listOf(
                        EntryState(
                            title = "Original Task",
                            timeRange = "02:30 - 03:00",
                            duration = "00:30:00"
                        )
                    )
                )
            )
        )
        timeLogsPage.assertPageState(initialState)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Original Task")

        // Wait for edit form to appear
        assertThat(page.locator("[data-testid='time-entry-edit']")).isVisible()

        // Make some changes
        timeLogsPage.fillStoppedEntryEditTitle("Changed Title")
        timeLogsPage.fillStoppedEntryEditStartTime("01:00")

        // Cancel editing
        timeLogsPage.clickCancelStoppedEntryEdit()

        // Wait for edit form to disappear
        assertThat(page.locator("[data-testid='time-entry-edit']")).not().isVisible()

        // Verify original state is preserved
        timeLogsPage.assertPageState(initialState)
    }

    @Test
    fun `should enforce mutual exclusion between active and stopped entry editing`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        // Create a stopped entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(2700),
                title = "Stopped Task",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Start editing the active entry
        timeLogsPage.clickEditEntry()

        // Verify active entry edit form is visible
        assertThat(page.locator("[data-testid='edit-title-input']")).isVisible()

        // Start editing a stopped entry
        timeLogsPage.clickEditForEntry("Stopped Task")

        // Verify active entry edit form is no longer visible (cancelled)
        assertThat(page.locator("[data-testid='edit-title-input']")).not().isVisible()

        // Verify stopped entry edit form is visible
        assertThat(page.locator("[data-testid='time-entry-edit']")).isVisible()

        // Cancel stopped entry edit
        timeLogsPage.clickCancelStoppedEntryEdit()

        // Start editing the active entry again
        timeLogsPage.clickEditEntry()

        // Verify active entry edit form is visible
        assertThat(page.locator("[data-testid='edit-title-input']")).isVisible()
    }

    @Test
    fun `should show error when end time is before start time`() {
        // Create a stopped entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Task to Edit",
                ownerId = requireNotNull(testUser.id)
            )
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Task to Edit")

        // Wait for edit form to appear
        assertThat(page.locator("[data-testid='time-entry-edit']")).isVisible()

        // Set end time before start time
        timeLogsPage.fillStoppedEntryEditStartDate("2024-03-16")
        timeLogsPage.fillStoppedEntryEditStartTime("03:00")
        timeLogsPage.fillStoppedEntryEditEndDate("2024-03-16")
        timeLogsPage.fillStoppedEntryEditEndTime("02:00")

        // Try to save
        timeLogsPage.clickSaveStoppedEntryEdit()

        // Verify error message is shown
        assertThat(page.locator("[data-testid='time-logs-error']")).isVisible()
        assertThat(page.locator("[data-testid='time-logs-error']")).containsText("End time must be after start time")

        // Verify we're still in edit mode
        assertThat(page.locator("[data-testid='time-entry-edit']")).isVisible()
    }
}
