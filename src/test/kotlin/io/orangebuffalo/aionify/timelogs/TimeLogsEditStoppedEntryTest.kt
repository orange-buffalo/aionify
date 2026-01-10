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
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Task",
                    ownerId = requireNotNull(testUser.id),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
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
        assertEquals(baseTime.withLocalTime("02:30"), updatedEntry.startTime, "Start time should be unchanged")
        assertEquals(baseTime.withLocalTime("03:00"), updatedEntry.endTime, "End time should be unchanged")
        assertEquals(testUser.id, updatedEntry.ownerId, "Owner ID should be unchanged")
    }

    @Test
    fun `should edit stopped entry start and end times`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
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
                weekNavigation = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "01:00:00"),
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
        assertEquals(baseTime.withLocalTime("01:30").minusMinutes(30), updatedEntry.startTime, "Start time should be updated to 01:00")
        assertEquals(baseTime.withLocalTime("02:30").minusMinutes(30), updatedEntry.endTime, "End time should be updated to 02:00")
        assertEquals(testUser.id, updatedEntry.ownerId, "Owner ID should be unchanged")
    }

    @Test
    fun `should cancel editing stopped entry`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
                    title = "Original Task",
                    ownerId = requireNotNull(testUser.id),
                ),
            )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify initial state
        val initialState =
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
        assertEquals(baseTime.withLocalTime("02:30"), unchangedEntry.startTime, "Start time should be unchanged")
        assertEquals(baseTime.withLocalTime("03:00"), unchangedEntry.endTime, "End time should be unchanged")
        assertEquals(testUser.id, unchangedEntry.ownerId, "Owner ID should be unchanged")
    }

    @Test
    fun `should allow editing multiple stopped entries simultaneously`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create two stopped entries
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30").minusMinutes(30),
                endTime = baseTime.withLocalTime("02:30"),
                title = "First Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("02:30"),
                endTime = baseTime.withLocalTime("03:00"),
                title = "Second Task",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify both entries are visible
        assertThat(page.locator("[data-testid='entry-title']")).hasCount(2)

        // Start editing the first entry
        timeLogsPage.clickEditForEntry("First Task")

        // Verify first entry edit form is visible
        timeLogsPage.assertStoppedEntryEditVisible()
        assertThat(page.locator("[data-testid='time-entry-edit']")).hasCount(1)

        // Start editing the second entry - should open a second edit form
        timeLogsPage.clickEditForEntry("Second Task")

        // Verify both edit forms are visible now - this is the key change
        assertThat(page.locator("[data-testid='time-entry-edit']")).hasCount(2)

        // Cancel both edits independently
        page
            .locator("[data-testid='time-entry-edit']")
            .nth(0)
            .locator("[data-testid='cancel-stopped-entry-edit-button']")
            .click()
        assertThat(page.locator("[data-testid='time-entry-edit']")).hasCount(1)

        page.locator("[data-testid='cancel-stopped-entry-edit-button']").click()
        assertThat(page.locator("[data-testid='time-entry-edit']")).hasCount(0)

        // Verify original titles are preserved
        assertThat(
            page.locator("[data-testid='entry-title']").filter(
                com.microsoft.playwright.Locator
                    .FilterOptions()
                    .setHasText("First Task"),
            ),
        ).isVisible()
        assertThat(
            page.locator("[data-testid='entry-title']").filter(
                com.microsoft.playwright.Locator
                    .FilterOptions()
                    .setHasText("Second Task"),
            ),
        ).isVisible()
    }

    @Test
    fun `should show error when end time is before start time`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create a stopped entry
        val createdEntry =
            testDatabaseSupport.insert(
                TimeLogEntry(
                    startTime = baseTime.withLocalTime("02:30"),
                    endTime = baseTime.withLocalTime("03:00"),
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
        assertThat(page.locator("[data-testid='edit-stopped-entry-error']")).isVisible()
        assertThat(page.locator("[data-testid='edit-stopped-entry-error']")).containsText("End time must be after start time")

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
        assertEquals(baseTime.withLocalTime("02:30"), unchangedEntry.startTime, "Start time should be unchanged")
        assertEquals(baseTime.withLocalTime("03:00"), unchangedEntry.endTime, "End time should be unchanged")
        assertEquals(testUser.id, unchangedEntry.ownerId, "Owner ID should be unchanged")
    }
}
