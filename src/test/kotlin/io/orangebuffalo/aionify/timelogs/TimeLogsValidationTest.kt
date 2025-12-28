package io.orangebuffalo.aionify.timelogs

import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Tests for validation and error handling.
 */
class TimeLogsValidationTest : TimeLogsPageTestBase() {
        @Test
    fun `should show error when setting start time in future`() {
        // Create an active entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = null,
                title = "Test Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
            TimeLogsPageState(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Test Task",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                    ),
                dayGroups =
                    listOf(
                        DayGroupState(
                            displayTitle = "Today",
                            totalDuration = "00:30:00",
                            entries =
                                listOf(
                                    EntryState(
                                        title = "Test Task",
                                        timeRange = "03:00 - in progress",
                                        duration = "00:30:00",
                                    ),
                                ),
                        ),
                    ),
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
        val errorState =
            initialState.copy(
                currentEntry =
                    CurrentEntryState.ActiveEntry(
                        title = "Test Task",
                        duration = "00:30:00",
                        startedAt = "16 Mar, 03:00",
                        editMode =
                            EditModeState.Editing(
                                titleValue = "Test Task",
                                dateValue = "16 Mar 2024",
                                timeValue = "10:00",
                                saveButtonEnabled = true,
                            ),
                    ),
                errorMessageVisible = true,
                errorMessage = "Start time cannot be in the future",
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
