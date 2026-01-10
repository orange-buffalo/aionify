package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for validation and error handling.
 */
class TimeLogsValidationTest : TimeLogsPageTestBase() {
    @Test
    fun `should hide edit button when not in active state`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify edit button is not visible when no active entry
        val noActiveEntryState = TimeLogsPageState()
        timeLogsPage.assertPageState(noActiveEntryState)
    }

    fun `should show error when end time is before start time`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setCurrentTimestamp(timeInTestTz("2024-03-16", "03:30"))

        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.minusHours(1),
                    endTime = baseTime.minusMinutes(30),
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
        assertEquals(baseTime.minusHours(1), unchangedEntry.startTime, "Start time should be unchanged")
        assertEquals(baseTime.minusMinutes(30), unchangedEntry.endTime, "End time should be unchanged")
        assertEquals(testUser.id, unchangedEntry.ownerId, "Owner ID should be unchanged")
    }
}
