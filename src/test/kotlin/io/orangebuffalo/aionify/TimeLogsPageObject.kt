package io.orangebuffalo.aionify

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole

/**
 * Page state model representing the complete state of the Time Logs page.
 * This model is used for comprehensive page state assertions.
 * 
 * Business invariants:
 * - weekRange is always visible and has a value
 * - timezoneHint is always visible
 * - noEntriesMessageVisible is automatically determined from dayGroups
 * 
 * **CRITICAL TESTING PRINCIPLE:**
 * We **MUST** ensure that we **NEVER** show unexpected elements and that **ALL** expected
 * elements have the proper state at any given time during test execution. Every test
 * should verify the complete page state, not just fragments. Use mutations (copy with
 * specific field changes) to indicate delta state changes, rather than duplicating
 * the entire state structure.
 */
data class TimeLogsPageState(
    val currentEntry: CurrentEntryState = CurrentEntryState.NoActiveEntry(),
    val weekNavigation: WeekNavigationState = WeekNavigationState(weekRange = "Mar 11 - Mar 17"),
    val dayGroups: List<DayGroupState> = emptyList(),
    val errorMessageVisible: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Registers a new entry in the specified day, creating the day group if it doesn't exist.
     */
    fun withEntry(dayTitle: String, entry: EntryState): TimeLogsPageState {
        val existingDay = dayGroups.find { it.displayTitle == dayTitle }
        val updatedDayGroups = if (existingDay != null) {
            dayGroups.map { 
                if (it.displayTitle == dayTitle) {
                    it.copy(entries = listOf(entry) + it.entries)
                } else {
                    it
                }
            }
        } else {
            listOf(DayGroupState(
                displayTitle = dayTitle,
                totalDuration = entry.duration,
                entries = listOf(entry)
            )) + dayGroups
        }
        return copy(dayGroups = updatedDayGroups)
    }
}

/**
 * State of the current entry panel (either active or ready to start new entry).
 */
sealed class CurrentEntryState {
    /**
     * No active entry - shows input field and start button.
     */
    data class NoActiveEntry(
        val inputVisible: Boolean = true,
        val startButtonVisible: Boolean = true,
        val startButtonEnabled: Boolean = false
    ) : CurrentEntryState()

    /**
     * Active entry in progress - shows entry details and stop button.
     * Can be in view mode or edit mode.
     * 
     * Business invariants:
     * - startedAt is always present (formatted start time)
     * - stopButtonVisible is always true
     */
    data class ActiveEntry(
        val title: String,
        val duration: String,
        val startedAt: String,  // Always present - formatted start time display (e.g., "14:00")
        val stopButtonVisible: Boolean = true,
        val inputVisible: Boolean = false,
        val startButtonVisible: Boolean = false,
        val editMode: EditModeState = EditModeState.NotEditing
    ) : CurrentEntryState()
}

/**
 * Edit mode state for active entry.
 */
sealed class EditModeState {
    /**
     * Not in edit mode - showing view mode with edit button.
     * 
     * Business invariant: edit button is always visible in view mode.
     */
    object NotEditing : EditModeState()
    
    /**
     * In edit mode - showing edit form with save/cancel buttons.
     * 
     * Business invariants:
     * - Cancel button is always visible
     */
    data class Editing(
        val titleValue: String,
        val dateTimeValue: String,  // Locale-formatted datetime display value
        val saveButtonEnabled: Boolean = true
    ) : EditModeState()
}

/**
 * State of the week navigation section.
 * Business invariant: weekRange is always present and navigation buttons are always visible.
 */
data class WeekNavigationState(
    val weekRange: String
)

/**
 * State of a single day group in the time logs list.
 */
data class DayGroupState(
    val displayTitle: String,
    val totalDuration: String,
    val entries: List<EntryState>
)

/**
 * State of a single time entry.
 */
data class EntryState(
    val title: String,
    val timeRange: String,
    val duration: String,
    val continueButtonVisible: Boolean = true,
    val menuButtonVisible: Boolean = true
)

/**
 * Page Object for the Time Logs page, providing methods to interact with the page
 * and assert its state.
 */
class TimeLogsPageObject(private val page: Page) {

    /**
     * Asserts that the page is in the expected state.
     * Uses Playwright assertions to verify all elements of the page state.
     * 
     * Important: This method uses content-based array assertions rather than count assertions
     * for better failure feedback.
     * 
     * Business invariants enforced:
     * - Week navigation is always visible with a range
     * - Timezone hint is always visible
     * - No entries message visibility is derived from dayGroups state
     */
    fun assertPageState(expectedState: TimeLogsPageState) {
        // Assert current entry panel state
        assertCurrentEntryState(expectedState.currentEntry)

        // Assert week navigation state (always present)
        assertWeekNavigationState(expectedState.weekNavigation)

        // Assert error message visibility
        if (expectedState.errorMessageVisible) {
            assertThat(page.locator("[data-testid='time-logs-error']")).isVisible()
            if (expectedState.errorMessage != null) {
                assertThat(page.locator("[data-testid='time-logs-error']")).containsText(expectedState.errorMessage)
            }
        } else {
            assertThat(page.locator("[data-testid='time-logs-error']")).not().isVisible()
        }

        // Assert day groups and entries
        // Business invariant: no entries message is visible iff dayGroups is empty
        if (expectedState.dayGroups.isNotEmpty()) {
            assertDayGroups(expectedState.dayGroups)
            assertThat(page.locator("[data-testid='no-entries']")).not().isVisible()
        } else {
            assertThat(page.locator("[data-testid='no-entries']")).isVisible()
            assertThat(page.locator("[data-testid='day-group']")).hasCount(0)
        }

        // Business invariant: timezone hint is always visible
        // Get the timezone from the browser
        val expectedTimezone = page.evaluate("() => Intl.DateTimeFormat().resolvedOptions().timeZone") as String
        val expectedHintText = "Times shown in $expectedTimezone"
        assertThat(page.locator("text=/Times shown in/")).isVisible()
        assertThat(page.locator("text=/Times shown in/")).hasText(expectedHintText)
    }

    private fun assertCurrentEntryState(currentEntry: CurrentEntryState) {
        when (currentEntry) {
            is CurrentEntryState.NoActiveEntry -> {
                // Verify input and start button state
                if (currentEntry.inputVisible) {
                    assertThat(page.locator("[data-testid='new-entry-input']")).isVisible()
                } else {
                    assertThat(page.locator("[data-testid='new-entry-input']")).not().isVisible()
                }

                if (currentEntry.startButtonVisible) {
                    assertThat(page.locator("[data-testid='start-button']")).isVisible()
                    if (currentEntry.startButtonEnabled) {
                        assertThat(page.locator("[data-testid='start-button']")).isEnabled()
                    } else {
                        assertThat(page.locator("[data-testid='start-button']")).isDisabled()
                    }
                } else {
                    assertThat(page.locator("[data-testid='start-button']")).not().isVisible()
                }

                // Business invariant: Active timer and stop button must always be hidden in NoActiveEntry state
                assertThat(page.locator("[data-testid='active-timer']")).not().isVisible()
                assertThat(page.locator("[data-testid='stop-button']")).not().isVisible()
                // Edit mode elements must be hidden
                assertThat(page.locator("[data-testid='edit-entry-button']")).not().isVisible()
                assertThat(page.locator("[data-testid='edit-title-input']")).not().isVisible()
                assertThat(page.locator("[data-testid='edit-date-input']")).not().isVisible()
                assertThat(page.locator("[data-testid='edit-time-input']")).not().isVisible()
            }

            is CurrentEntryState.ActiveEntry -> {
                // Business invariant: Input and start button must always be hidden in ActiveEntry state
                assertThat(page.locator("[data-testid='new-entry-input']")).not().isVisible()
                assertThat(page.locator("[data-testid='start-button']")).not().isVisible()

                // Assert edit mode state
                when (currentEntry.editMode) {
                    is EditModeState.NotEditing -> {
                        // View mode: show title, startedAt, edit button, timer, and stop button
                        assertThat(page.locator("[data-testid='current-entry-panel']").locator("text=${currentEntry.title}")).isVisible()
                        
                        // startedAt is always present (business invariant)
                        assertThat(page.locator("[data-testid='active-entry-started-at']")).isVisible()
                        assertThat(page.locator("[data-testid='active-entry-started-at']")).containsText(currentEntry.startedAt)
                        
                        // Edit button is always visible in view mode (business invariant)
                        assertThat(page.locator("[data-testid='edit-entry-button']")).isVisible()

                        // Timer and stop button visible in view mode
                        assertThat(page.locator("[data-testid='active-timer']")).isVisible()
                        assertThat(page.locator("[data-testid='active-timer']")).hasText(currentEntry.duration)
                        
                        if (currentEntry.stopButtonVisible) {
                            assertThat(page.locator("[data-testid='stop-button']")).isVisible()
                        } else {
                            assertThat(page.locator("[data-testid='stop-button']")).not().isVisible()
                        }

                        // Edit inputs must be hidden in view mode
                        assertThat(page.locator("[data-testid='edit-title-input']")).not().isVisible()
                        assertThat(page.locator("[data-testid='edit-datetime-trigger']")).not().isVisible()
                        assertThat(page.locator("[data-testid='save-edit-button']")).not().isVisible()
                        assertThat(page.locator("[data-testid='cancel-edit-button']")).not().isVisible()
                    }
                    
                    is EditModeState.Editing -> {
                        // Edit mode: show edit form, timer/stop button NOT visible
                        val editMode = currentEntry.editMode
                        
                        // Title input
                        assertThat(page.locator("[data-testid='edit-title-input']")).isVisible()
                        assertThat(page.locator("[data-testid='edit-title-input']")).hasValue(editMode.titleValue)
                        
                        // DateTime picker trigger button
                        val dateTimeTrigger = page.locator("[data-testid='edit-datetime-trigger']")
                        assertThat(dateTimeTrigger).isVisible()
                        assertThat(dateTimeTrigger).containsText(editMode.dateTimeValue)
                        
                        // Save button
                        assertThat(page.locator("[data-testid='save-edit-button']")).isVisible()
                        if (editMode.saveButtonEnabled) {
                            assertThat(page.locator("[data-testid='save-edit-button']")).isEnabled()
                        } else {
                            assertThat(page.locator("[data-testid='save-edit-button']")).isDisabled()
                        }
                        
                        // Cancel button is always visible (business invariant)
                        assertThat(page.locator("[data-testid='cancel-edit-button']")).isVisible()

                        // View mode elements must be hidden in edit mode
                        assertThat(page.locator("[data-testid='edit-entry-button']")).not().isVisible()
                        assertThat(page.locator("[data-testid='active-timer']")).not().isVisible()
                        assertThat(page.locator("[data-testid='stop-button']")).not().isVisible()
                    }
                }
            }
        }
    }

    private fun assertWeekNavigationState(weekNav: WeekNavigationState) {
        // Business invariant: Week navigation is always visible
        assertThat(page.locator("[data-testid='previous-week-button']")).isVisible()
        assertThat(page.locator("[data-testid='next-week-button']")).isVisible()
        assertThat(page.locator("[data-testid='week-range']")).isVisible()
        assertThat(page.locator("[data-testid='week-range']")).hasText(weekNav.weekRange)
    }

    private fun assertDayGroups(expectedDayGroups: List<DayGroupState>) {
        val dayGroupLocator = page.locator("[data-testid='day-group']")
        
        // Assert day group titles using content-based assertion
        val expectedTitles = expectedDayGroups.map { it.displayTitle }
        val titleLocators = dayGroupLocator.locator("[data-testid='day-title']")
        assertThat(titleLocators).containsText(expectedTitles.toTypedArray())
        
        // Assert day group total durations with exact match including "Total: " prefix
        val expectedDurationsWithPrefix = expectedDayGroups.map { "Total: ${it.totalDuration}" }
        val durationLocators = dayGroupLocator.locator("[data-testid='day-total-duration']")
        assertThat(durationLocators).containsText(expectedDurationsWithPrefix.toTypedArray())

        // Assert entries for each day group
        for (i in expectedDayGroups.indices) {
            val dayGroup = expectedDayGroups[i]
            val dayGroupElement = dayGroupLocator.nth(i)
            
            if (dayGroup.entries.isNotEmpty()) {
                assertEntriesInDayGroup(dayGroupElement, dayGroup.entries)
            }
        }
    }

    private fun assertEntriesInDayGroup(dayGroupLocator: com.microsoft.playwright.Locator, expectedEntries: List<EntryState>) {
        val entryLocator = dayGroupLocator.locator("[data-testid='time-entry']")
        
        // Assert entry titles using content-based assertion
        val expectedTitles = expectedEntries.map { it.title }
        val titleLocators = entryLocator.locator("[data-testid='entry-title']")
        assertThat(titleLocators).containsText(expectedTitles.toTypedArray())
        
        // Assert entry time ranges using content-based assertion
        val expectedTimeRanges = expectedEntries.map { it.timeRange }
        val timeRangeLocators = entryLocator.locator("[data-testid='entry-time-range']")
        assertThat(timeRangeLocators).containsText(expectedTimeRanges.toTypedArray())
        
        // Assert entry durations using content-based assertion
        val expectedDurations = expectedEntries.map { it.duration }
        val durationLocators = entryLocator.locator("[data-testid='entry-duration']")
        assertThat(durationLocators).containsText(expectedDurations.toTypedArray())

        // Assert action buttons visibility for each entry
        for (i in expectedEntries.indices) {
            val entry = expectedEntries[i]
            val entryElement = entryLocator.nth(i)
            
            if (entry.continueButtonVisible) {
                assertThat(entryElement.locator("[data-testid='continue-button']")).isVisible()
            } else {
                assertThat(entryElement.locator("[data-testid='continue-button']")).not().isVisible()
            }
            
            if (entry.menuButtonVisible) {
                assertThat(entryElement.locator("[data-testid='entry-menu-button']")).isVisible()
            } else {
                assertThat(entryElement.locator("[data-testid='entry-menu-button']")).not().isVisible()
            }
        }
    }

    // Interaction methods

    /**
     * Fills the new entry input field with the given title.
     */
    fun fillNewEntryTitle(title: String) {
        page.locator("[data-testid='new-entry-input']").fill(title)
    }

    /**
     * Clicks the start button to start a new time entry.
     */
    fun clickStart() {
        page.locator("[data-testid='start-button']").click()
    }

    /**
     * Clicks the stop button to stop the active time entry.
     */
    fun clickStop() {
        page.locator("[data-testid='stop-button']").click()
    }

    /**
     * Presses Enter in the new entry input field.
     */
    fun pressEnterInNewEntryInput() {
        page.locator("[data-testid='new-entry-input']").press("Enter")
    }

    /**
     * Clicks the continue button for an entry with the given title.
     * The entry must be uniquely identifiable by title.
     */
    fun clickContinueForEntry(entryTitle: String) {
        page.locator("[data-testid='time-entry']:has-text('$entryTitle')")
            .locator("[data-testid='continue-button']")
            .click()
    }

    /**
     * Deletes an entry with the given title, confirming the deletion dialog.
     * For entries that span midnight (appearing in multiple day groups), deletes the first occurrence.
     */
    fun deleteEntry(entryTitle: String) {
        // Find all entries with this title
        val matchingEntries = page.locator("[data-testid='time-entry']:has-text('$entryTitle')")
        
        // For midnight-spanning entries, there will be multiple matches (same entry split across days)
        // Click the menu button for the first occurrence
        matchingEntries.first().locator("[data-testid='entry-menu-button']").click()

        // Click delete in the menu
        page.locator("[data-testid='delete-menu-item']").click()

        // Confirm deletion
        val confirmButton = page.locator("[data-testid='confirm-delete-button']")
        assertThat(confirmButton).isVisible()
        confirmButton.click()

        // Wait for dialog to close
        assertThat(confirmButton).not().isVisible()
    }

    /**
     * Navigates to the previous week.
     */
    fun goToPreviousWeek() {
        page.locator("[data-testid='previous-week-button']").click()
    }

    /**
     * Navigates to the next week.
     */
    fun goToNextWeek() {
        page.locator("[data-testid='next-week-button']").click()
    }

    /**
     * Advances the browser clock by the given number of milliseconds.
     */
    fun advanceClock(milliseconds: Long) {
        page.clock().runFor(milliseconds)
    }

    /**
     * Clicks the edit button for the active entry.
     */
    fun clickEditEntry() {
        page.locator("[data-testid='edit-entry-button']").click()
    }

    private var editDateValue: String = "2024-03-15"
    private var editTimeValue: String = "00:00"

    /**
     * Fills the edit title input with the given title.
     */
    fun fillEditTitle(title: String) {
        page.locator("[data-testid='edit-title-input']").fill(title)
    }

    /**
     * Sets the edit date/time using the custom DateTimePicker.
     * @param date in format "YYYY-MM-DD" (e.g., "2024-03-15")
     * @param time in format "HH:mm" (e.g., "14:30")
     */
    fun fillEditDateTime(date: String, time: String) {
        // Click the trigger to open the picker
        page.locator("[data-testid='edit-datetime-trigger']").click()
        
        // Wait for popover to appear
        page.locator("[role='dialog']").waitFor()
        
        // Parse the date to get the day we need to click
        val dateParts = date.split("-")
        val targetDay = dateParts[2].toInt()
        
        // Click on the day button in the calendar grid
        // Find button with exact text matching the day number
        val popover = page.locator("[role='dialog']")
        popover.locator("button").locator("text=${targetDay}").first().click()
        
        // Fill in the time inputs
        val (hours, minutes) = time.split(":")
        page.locator("[data-testid='edit-datetime-hours']").fill(hours)
        page.locator("[data-testid='edit-datetime-minutes']").fill(minutes)
        
        // Apply the changes
        page.locator("[data-testid='edit-datetime-apply']").click()
    }

    /**
     * Fills the edit date input (convenience method for backwards compatibility).
     * Stores the date value to be combined with time when applied.
     * @param date in format "YYYY-MM-DD" (e.g., "2024-03-15")
     */
    fun fillEditDate(date: String) {
        editDateValue = date
    }

    /**
     * Fills the edit time input (convenience method for backwards compatibility).
     * Stores the time value to be combined with date when applied.
     * @param time in format "HH:mm" (e.g., "14:30")
     */
    fun fillEditTime(time: String) {
        editTimeValue = time
        // Apply the combined date and time
        fillEditDateTime(editDateValue, time)
        // Reset for next use
        editDateValue = "2024-03-15"
        editTimeValue = "00:00"
    }

    /**
     * Clicks the save button to save the edited entry.
     */
    fun clickSaveEdit() {
        page.locator("[data-testid='save-edit-button']").click()
    }

    /**
     * Clicks the cancel button to discard changes.
     */
    fun clickCancelEdit() {
        page.locator("[data-testid='cancel-edit-button']").click()
    }
}
