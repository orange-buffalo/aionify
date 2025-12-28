package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for editing stopped (completed) time entries.
 */
class TimeLogsEditStoppedEntryTest : TimeLogsPageTestBase() {
    @Test
    fun `should edit stopped entry title`() {
        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Original Task",
                    ownerId = requireNotNull(testUser.id),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Original Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Original Task")

        // Verify edit form is visible with all controls and proper initial values
        timeLogsPage.assertStoppedEntryEditVisible()
        timeLogsPage.assertStoppedEntryEditValues(
            expectedTitle = "Original Task",
            expectedStartDate = "16 Mar 2024",
            expectedStartTime = "02:30",
            expectedEndDate = "16 Mar 2024",
            expectedEndTime = "03:00",
        )

        // Change the title
        timeLogsPage.fillStoppedEntryEditTitle("Updated Task")

        // Save changes
        timeLogsPage.clickSaveStoppedEntryEdit()

        // Verify edit form is hidden with all controls
        timeLogsPage.assertStoppedEntryEditHidden()

        // Verify the entry is updated in UI
        val updatedState =
            initialState.copy(
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Updated Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(updatedState)

        // Verify database state - only title changed, rest unchanged
        val updatedEntry =
            timeLogEntryRepository
                .findByIdAndOwnerId(
                    requireNotNull(createdEntry.id),
                    requireNotNull(testUser.id),
                ).orElse(null)
        assertNotNull(updatedEntry, "Entry should exist in database")
        assertEquals("Updated Task", updatedEntry!!.title, "Title should be updated")
        assertEquals(FIXED_TEST_TIME.minusSeconds(3600), updatedEntry.startTime, "Start time should be unchanged")
        assertEquals(FIXED_TEST_TIME.minusSeconds(1800), updatedEntry.endTime, "End time should be unchanged")
        assertEquals(testUser.id, updatedEntry.ownerId, "Owner ID should be unchanged")
    }

    @Test
    fun `should edit stopped entry start and end times`() {
        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600), // 02:30
                    endTime = FIXED_TEST_TIME.minusSeconds(1800), // 03:00
                    title = "Task to Edit",
                    ownerId = requireNotNull(testUser.id),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Task to Edit")

        // Verify edit form is visible with all controls and proper initial values
        timeLogsPage.assertStoppedEntryEditVisible()
        timeLogsPage.assertStoppedEntryEditValues(
            expectedTitle = "Task to Edit",
            expectedStartDate = "16 Mar 2024",
            expectedStartTime = "02:30",
            expectedEndDate = "16 Mar 2024",
            expectedEndTime = "03:00",
        )

        // Change start time to 01:00 and end time to 02:00
        timeLogsPage.fillStoppedEntryEditStartDate("2024-03-16")
        timeLogsPage.fillStoppedEntryEditStartTime("01:00")
        timeLogsPage.fillStoppedEntryEditEndDate("2024-03-16")
        timeLogsPage.fillStoppedEntryEditEndTime("02:00")

        // Save changes
        timeLogsPage.clickSaveStoppedEntryEdit()

        // Verify edit form is hidden with all controls
        timeLogsPage.assertStoppedEntryEditHidden()

        // Verify the entry is updated with new times and duration (1 hour)
        val updatedState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "01:00:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Task to Edit",
                                        timeRange = "01:00 - 02:00",
                                        duration = "01:00:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(updatedState)

        // Verify database state - times updated, title and owner unchanged
        val updatedEntry =
            timeLogEntryRepository
                .findByIdAndOwnerId(
                    requireNotNull(createdEntry.id),
                    requireNotNull(testUser.id),
                ).orElse(null)
        assertNotNull(updatedEntry, "Entry should exist in database")
        assertEquals("Task to Edit", updatedEntry!!.title, "Title should be unchanged")
        assertEquals(FIXED_TEST_TIME.minusSeconds(9000), updatedEntry.startTime, "Start time should be updated to 01:00")
        assertEquals(FIXED_TEST_TIME.minusSeconds(5400), updatedEntry.endTime, "End time should be updated to 02:00")
        assertEquals(testUser.id, updatedEntry.ownerId, "Owner ID should be unchanged")
    }

    @Test
    fun `should cancel editing stopped entry`() {
        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Original Task",
                    ownerId = requireNotNull(testUser.id),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry = CurrentEntryState.NoActiveEntry(),
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar"),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Original Task",
                                        timeRange = "02:30 - 03:00",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
            )
        timeLogsPage.assertPageState(initialState)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Original Task")

        // Verify edit form is visible with all controls and proper initial values
        timeLogsPage.assertStoppedEntryEditVisible()
        timeLogsPage.assertStoppedEntryEditValues(
            expectedTitle = "Original Task",
            expectedStartDate = "16 Mar 2024",
            expectedStartTime = "02:30",
            expectedEndDate = "16 Mar 2024",
            expectedEndTime = "03:00",
        )

        // Make some changes
        timeLogsPage.fillStoppedEntryEditTitle("Changed Title")
        timeLogsPage.fillStoppedEntryEditStartTime("01:00")

        // Cancel editing
        timeLogsPage.clickCancelStoppedEntryEdit()

        // Verify edit form is hidden with all controls
        timeLogsPage.assertStoppedEntryEditHidden()

        // Verify original state is preserved
        timeLogsPage.assertPageState(initialState)

        // Verify database state is unchanged
        val unchangedEntry =
            timeLogEntryRepository
                .findByIdAndOwnerId(
                    requireNotNull(createdEntry.id),
                    requireNotNull(testUser.id),
                ).orElse(null)
        assertNotNull(unchangedEntry, "Entry should exist in database")
        assertEquals("Original Task", unchangedEntry!!.title, "Title should be unchanged")
        assertEquals(FIXED_TEST_TIME.minusSeconds(3600), unchangedEntry.startTime, "Start time should be unchanged")
        assertEquals(FIXED_TEST_TIME.minusSeconds(1800), unchangedEntry.endTime, "End time should be unchanged")
        assertEquals(testUser.id, unchangedEntry.ownerId, "Owner ID should be unchanged")
    }

    @Test
    fun `should enforce mutual exclusion between active and stopped entry editing`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Active Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Create a stopped entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(2700),
                title = "Stopped Task",
                ownerId = requireNotNull(testUser.id),
            ),
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

        // Verify stopped entry edit form is visible with all controls
        timeLogsPage.assertStoppedEntryEditVisible()

        // Cancel stopped entry edit
        timeLogsPage.clickCancelStoppedEntryEdit()

        // Verify stopped entry edit form is hidden with all controls
        timeLogsPage.assertStoppedEntryEditHidden()

        // Start editing the active entry again
        timeLogsPage.clickEditEntry()

        // Verify active entry edit form is visible
        assertThat(page.locator("[data-testid='edit-title-input']")).isVisible()
    }

    @Test
    fun `should show error when end time is before start time`() {
        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = FIXED_TEST_TIME.minusSeconds(3600),
                    endTime = FIXED_TEST_TIME.minusSeconds(1800),
                    title = "Task to Edit",
                    ownerId = requireNotNull(testUser.id),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click edit for the entry
        timeLogsPage.clickEditForEntry("Task to Edit")

        // Verify edit form is visible with all controls and proper initial values
        timeLogsPage.assertStoppedEntryEditVisible()
        timeLogsPage.assertStoppedEntryEditValues(
            expectedTitle = "Task to Edit",
            expectedStartDate = "16 Mar 2024",
            expectedStartTime = "02:30",
            expectedEndDate = "16 Mar 2024",
            expectedEndTime = "03:00",
        )

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

        // Verify we're still in edit mode with all controls visible
        timeLogsPage.assertStoppedEntryEditVisible()

        // Verify database state is unchanged (validation prevented the update)
        val unchangedEntry =
            timeLogEntryRepository
                .findByIdAndOwnerId(
                    requireNotNull(createdEntry.id),
                    requireNotNull(testUser.id),
                ).orElse(null)
        assertNotNull(unchangedEntry, "Entry should exist in database")
        assertEquals("Task to Edit", unchangedEntry!!.title, "Title should be unchanged")
        assertEquals(FIXED_TEST_TIME.minusSeconds(3600), unchangedEntry.startTime, "Start time should be unchanged")
        assertEquals(FIXED_TEST_TIME.minusSeconds(1800), unchangedEntry.endTime, "End time should be unchanged")
        assertEquals(testUser.id, unchangedEntry.ownerId, "Owner ID should be unchanged")
    }
}
