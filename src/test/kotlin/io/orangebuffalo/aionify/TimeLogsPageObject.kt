package io.orangebuffalo.aionify

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

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
    val weekNavigation: WeekNavigationState = WeekNavigationState(weekRange = "11 Mar - 17 Mar", weeklyTotal = "00:00:00"),
    val dayGroups: List<DayGroupState> = emptyList(),
    val errorMessageVisible: Boolean = false,
    val errorMessage: String? = null,
) {
    /**
     * Registers a new entry in the specified day, creating the day group if it doesn't exist.
     */
    fun withEntry(
        dayTitle: String,
        entry: EntryState,
    ): TimeLogsPageState {
        val existingDay = dayGroups.find { it.displayTitle == dayTitle }
        val updatedDayGroups =
            if (existingDay != null) {
                dayGroups.map {
                    if (it.displayTitle == dayTitle) {
                        it.copy(entries = listOf(entry) + it.entries)
                    } else {
                        it
                    }
                }
            } else {
                listOf(
                    DayGroupState(
                        displayTitle = dayTitle,
                        totalDuration = entry.duration,
                        entries = listOf(entry),
                    ),
                ) + dayGroups
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
        val startButtonEnabled: Boolean = false,
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
        val startedAt: String, // Always present - formatted start time display (e.g., "14:00")
        val stopButtonVisible: Boolean = true,
        val inputVisible: Boolean = false,
        val startButtonVisible: Boolean = false,
        val editMode: EditModeState = EditModeState.NotEditing,
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
        val dateValue: String, // Locale-formatted date display value
        val timeValue: String, // Locale-formatted time display value
        val saveButtonEnabled: Boolean = true,
    ) : EditModeState()
}

/**
 * State of the week navigation section.
 * Business invariant: weekRange is always present and navigation buttons are always visible.
 */
data class WeekNavigationState(
    val weekRange: String,
    val weeklyTotal: String,
)

/**
 * State of a single day group in the time logs list.
 */
data class DayGroupState(
    val displayTitle: String,
    val totalDuration: String,
    val entries: List<EntryState>,
)

/**
 * State of a single time entry.
 */
data class EntryState(
    val title: String,
    val timeRange: String,
    val duration: String,
    val tags: List<String> = emptyList(),
    val continueButtonVisible: Boolean = true,
    val menuButtonVisible: Boolean = true,
    val hasDifferentDayWarning: Boolean = false,
    val isGrouped: Boolean = false,
    val groupCount: Int? = null,
    val isGroupExpanded: Boolean = false,
    val groupedEntries: List<EntryState> = emptyList(),
)

/**
 * Page Object for the Time Logs page, providing methods to interact with the page
 * and assert its state.
 */
class TimeLogsPageObject(
    private val page: Page,
) {
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
        assertWeekNavigationState(expectedState.weekNavigation, expectedState.dayGroups, expectedState.currentEntry)

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
                assertThat(page.locator("[data-testid='edit-date-trigger']")).not().isVisible()
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
                        assertThat(page.locator("[data-testid='edit-date-trigger']")).not().isVisible()
                        assertThat(page.locator("[data-testid='edit-time-input']")).not().isVisible()
                        assertThat(page.locator("[data-testid='save-edit-button']")).not().isVisible()
                        assertThat(page.locator("[data-testid='cancel-edit-button']")).not().isVisible()
                    }

                    is EditModeState.Editing -> {
                        // Edit mode: show edit form, timer/stop button NOT visible
                        val editMode = currentEntry.editMode

                        // Title input
                        assertThat(page.locator("[data-testid='edit-title-input']")).isVisible()
                        assertThat(page.locator("[data-testid='edit-title-input']")).hasValue(editMode.titleValue)

                        // Date picker input - check value
                        val dateInput = page.locator("[data-testid='edit-date-input']")
                        assertThat(dateInput).isVisible()
                        assertThat(dateInput).hasValue(editMode.dateValue)

                        // Time picker input - check value
                        val timeInput = page.locator("[data-testid='edit-time-input']")
                        assertThat(timeInput).isVisible()
                        assertThat(timeInput).hasValue(editMode.timeValue)

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

    private fun assertWeekNavigationState(
        weekNav: WeekNavigationState,
        dayGroups: List<DayGroupState>,
        currentEntry: CurrentEntryState,
    ) {
        // Business invariant: Week navigation is always visible
        assertThat(page.locator("[data-testid='previous-week-button']")).isVisible()
        assertThat(page.locator("[data-testid='next-week-button']")).isVisible()
        assertThat(page.locator("[data-testid='week-range']")).isVisible()
        assertThat(page.locator("[data-testid='week-range']")).hasText(weekNav.weekRange)

        // Assert weekly total
        assertThat(page.locator("[data-testid='weekly-total']")).isVisible()
        assertThat(page.locator("[data-testid='weekly-total']")).containsText(weekNav.weeklyTotal)
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

    private fun assertEntriesInDayGroup(
        dayGroupLocator: com.microsoft.playwright.Locator,
        expectedEntries: List<EntryState>,
    ) {
        // Separate regular and grouped entries
        val regularEntries = expectedEntries.filter { !it.isGrouped }
        val groupedEntries = expectedEntries.filter { it.isGrouped }

        // Assert regular entries
        if (regularEntries.isNotEmpty()) {
            val entryLocator = dayGroupLocator.locator("[data-testid='time-entry']")

            // Assert entry titles using content-based assertion
            val expectedTitles = regularEntries.map { it.title }
            val titleLocators = entryLocator.locator("[data-testid='entry-title']")
            assertThat(titleLocators).containsText(expectedTitles.toTypedArray())

            // Assert entry time ranges using content-based assertion
            val expectedTimeRanges = regularEntries.map { it.timeRange }
            val timeRangeLocators = entryLocator.locator("[data-testid='entry-time-range']")
            assertThat(timeRangeLocators).containsText(expectedTimeRanges.toTypedArray())

            // Assert entry durations using content-based assertion
            val expectedDurations = regularEntries.map { it.duration }
            val durationLocators = entryLocator.locator("[data-testid='entry-duration']")
            assertThat(durationLocators).containsText(expectedDurations.toTypedArray())

            // Assert tags for each entry
            for (i in regularEntries.indices) {
                val entry = regularEntries[i]
                val entryElement = entryLocator.nth(i)

                val tagLocators = entryElement.locator("[data-testid^='entry-tag-']")
                if (entry.tags.isNotEmpty()) {
                    // Assert tag text content matches expected tags using array-based assertion
                    assertThat(tagLocators).containsText(entry.tags.toTypedArray())
                } else {
                    // Tags container should not be visible when there are no tags
                    assertThat(tagLocators).isHidden()
                }
            }

            // Assert action buttons visibility for each entry
            for (i in regularEntries.indices) {
                val entry = regularEntries[i]
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

                // Assert different day warning icon visibility
                val warningIcon = entryElement.locator("[data-testid='different-day-warning']")
                if (entry.hasDifferentDayWarning) {
                    assertThat(warningIcon).isVisible()
                } else {
                    assertThat(warningIcon).not().isVisible()
                }
            }
        }

        // Assert grouped entries
        for (groupedEntry in groupedEntries) {
            assertGroupedEntry(dayGroupLocator, groupedEntry)
        }
    }

    private fun assertGroupedEntry(
        dayGroupLocator: com.microsoft.playwright.Locator,
        expectedGroupedEntry: EntryState,
    ) {
        require(expectedGroupedEntry.isGrouped) { "Entry must be marked as grouped" }
        require(expectedGroupedEntry.groupCount != null) { "Grouped entry must have a count" }

        // Find the grouped entry by title
        val groupedEntryLocator =
            dayGroupLocator
                .locator("[data-testid='grouped-time-entry']")
                .filter(
                    com.microsoft.playwright.Locator
                        .FilterOptions()
                        .setHasText(expectedGroupedEntry.title),
                )

        // Assert count badge
        assertThat(groupedEntryLocator.locator("[data-testid='entry-count-badge']"))
            .containsText(expectedGroupedEntry.groupCount.toString())

        // Assert title
        assertThat(groupedEntryLocator.locator("[data-testid='entry-title']"))
            .containsText(expectedGroupedEntry.title)

        // Assert tags
        val tagLocators = groupedEntryLocator.locator("[data-testid^='entry-tag-']")
        if (expectedGroupedEntry.tags.isNotEmpty()) {
            assertThat(tagLocators).containsText(expectedGroupedEntry.tags.toTypedArray())
        } else {
            assertThat(tagLocators).isHidden()
        }

        // Assert time range
        assertThat(groupedEntryLocator.locator("[data-testid='entry-time-range']"))
            .containsText(expectedGroupedEntry.timeRange)

        // Assert duration
        assertThat(groupedEntryLocator.locator("[data-testid='entry-duration']"))
            .containsText(expectedGroupedEntry.duration)

        // Assert continue button
        if (expectedGroupedEntry.continueButtonVisible) {
            assertThat(groupedEntryLocator.locator("[data-testid='continue-button']")).isVisible()
        } else {
            assertThat(groupedEntryLocator.locator("[data-testid='continue-button']")).not().isVisible()
        }

        // Assert burger menu is NOT visible on grouped entry
        assertThat(groupedEntryLocator.locator("[data-testid='entry-menu-button']")).not().isVisible()

        // If grouped entries are specified, verify them regardless of expansion state
        // This allows us to verify the logical composition of the group even when collapsed
        if (expectedGroupedEntry.groupedEntries.isNotEmpty()) {
            // The grouped entries list documents what individual entries make up the group
            // We verify the count matches
            require(expectedGroupedEntry.groupedEntries.size == expectedGroupedEntry.groupCount) {
                "groupedEntries size (${expectedGroupedEntry.groupedEntries.size}) must match groupCount (${expectedGroupedEntry.groupCount})"
            }

            // Verify the aggregate time range matches the individual entries
            val earliestStart =
                expectedGroupedEntry.groupedEntries
                    .minByOrNull {
                        it.timeRange.split(
                            " - ",
                        )[0]
                    }?.timeRange
                    ?.split(" - ")
                    ?.get(0)
            val latestEnd =
                if (expectedGroupedEntry.groupedEntries.any { it.timeRange.contains("in progress") }) {
                    "in progress"
                } else {
                    expectedGroupedEntry.groupedEntries
                        .maxByOrNull { it.timeRange.split(" - ")[1] }
                        ?.timeRange
                        ?.split(" - ")
                        ?.get(1)
                }
            val expectedAggregateTimeRange = "$earliestStart - $latestEnd"
            require(expectedGroupedEntry.timeRange == expectedAggregateTimeRange) {
                "Grouped entry timeRange (${expectedGroupedEntry.timeRange}) must match aggregate of individual entries ($expectedAggregateTimeRange)"
            }
        }

        // If expanded, assert the expanded entries in the UI
        if (expectedGroupedEntry.isGroupExpanded) {
            val expandedContainer = groupedEntryLocator.locator("[data-testid='grouped-entries-expanded']")
            assertThat(expandedContainer).isVisible()

            val expandedEntries = expectedGroupedEntry.groupedEntries
            val expandedEntryLocators = expandedContainer.locator("[data-testid='time-entry']")

            // Assert time ranges for expanded entries
            val expectedTimeRanges = expandedEntries.map { it.timeRange }
            val timeRangeLocators = expandedEntryLocators.locator("[data-testid='entry-time-range']")
            assertThat(timeRangeLocators).containsText(expectedTimeRanges.toTypedArray())

            // Assert durations for expanded entries
            val expectedDurations = expandedEntries.map { it.duration }
            val durationLocators = expandedEntryLocators.locator("[data-testid='entry-duration']")
            assertThat(durationLocators).containsText(expectedDurations.toTypedArray())

            // For each expanded entry, verify title IS visible but tags are NOT
            for (i in expandedEntries.indices) {
                val entry = expandedEntries[i]
                val entryElement = expandedEntryLocators.nth(i)

                // Title SHOULD be visible
                assertThat(entryElement.locator("[data-testid='entry-title']")).isVisible()
                assertThat(entryElement.locator("[data-testid='entry-title']")).containsText(entry.title)

                // Tags should NOT be visible
                assertThat(entryElement.locator("[data-testid='entry-tags']")).not().isVisible()

                // Continue button should NOT be visible
                assertThat(entryElement.locator("[data-testid='continue-button']")).not().isVisible()

                // Burger menu SHOULD be visible
                assertThat(entryElement.locator("[data-testid='entry-menu-button']")).isVisible()
            }
        } else {
            assertThat(groupedEntryLocator.locator("[data-testid='grouped-entries-expanded']")).not().isVisible()
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
        page
            .locator("[data-testid='time-entry']:has-text('$entryTitle')")
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
     * Sets the edit date/time using the separate DatePicker and TimePicker.
     * @param date in format "YYYY-MM-DD" (e.g., "2024-03-15")
     * @param time in format "HH:mm" (e.g., "14:30")
     */
    fun fillEditDateTime(
        date: String,
        time: String,
    ) {
        // Click the date trigger button to open the date picker popover
        val dateTrigger = page.locator("[data-testid='edit-date-trigger']")
        dateTrigger.click()

        // Wait for popover to appear
        page.locator("[role='dialog']").waitFor()

        // Parse the date to get the day we need to click
        val dateParts = date.split("-")
        val targetDay = dateParts[2].toInt()

        // Click on the day button in the calendar grid
        // Find button with exact text matching the day number
        // Clicking the day now automatically applies and closes the popover
        val popover = page.locator("[role='dialog']")
        popover
            .locator("button")
            .locator("text=$targetDay")
            .first()
            .click()

        // Wait for popover to close
        page
            .locator(
                "[role='dialog']",
            ).waitFor(
                com.microsoft.playwright.Locator
                    .WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN),
            )

        // Fill in the time input
        val (hours, minutes) = time.split(":")
        val timeInput = page.locator("[data-testid='edit-time-input']")

        // Use 24-hour format which is now accepted by both locale formats
        val timeStr = "${hours.padStart(2, '0')}:$minutes"

        // Use fill() to set the value
        timeInput.fill(timeStr)
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

    // Helper functions for editing stopped entries

    fun clickEditForEntry(entryTitle: String) {
        // Find the entry with the title and click the menu button
        val entryLocator = page.locator("[data-testid='time-entry']:has-text('$entryTitle')")
        entryLocator.locator("[data-testid='entry-menu-button']").click()

        // Click the edit menu item
        page.locator("[data-testid='edit-menu-item']").click()
    }

    fun fillStoppedEntryEditTitle(title: String) {
        val input = page.locator("[data-testid='stopped-entry-edit-title-input']")
        input.fill(title)
    }

    fun fillStoppedEntryEditStartDate(date: String) {
        // Format: YYYY-MM-DD
        // Click the date trigger button to open the date picker popover
        val dateTrigger = page.locator("[data-testid='stopped-entry-edit-date-trigger']")
        dateTrigger.click()

        // Wait for popover to appear
        page.locator("[role='dialog']").waitFor()

        // Parse the date to get the day we need to click
        val dateParts = date.split("-")
        val targetDay = dateParts[2].toInt()

        // Click on the day button in the calendar grid
        val popover = page.locator("[role='dialog']")
        popover
            .locator("button")
            .locator("text=$targetDay")
            .first()
            .click()

        // Wait for popover to close
        page
            .locator(
                "[role='dialog']",
            ).waitFor(
                com.microsoft.playwright.Locator
                    .WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN),
            )
    }

    fun fillStoppedEntryEditStartTime(time: String) {
        // Format: HH:MM (24-hour format)
        val input = page.locator("[data-testid='stopped-entry-edit-time-input']")
        input.fill(time)
    }

    fun fillStoppedEntryEditEndDate(date: String) {
        // Format: YYYY-MM-DD
        // Click the date trigger button to open the date picker popover
        val dateTrigger = page.locator("[data-testid='stopped-entry-edit-end-date-trigger']")
        dateTrigger.click()

        // Wait for popover to appear
        page.locator("[role='dialog']").waitFor()

        // Parse the date to get the day we need to click
        val dateParts = date.split("-")
        val targetDay = dateParts[2].toInt()

        // Click on the day button in the calendar grid
        val popover = page.locator("[role='dialog']")
        popover
            .locator("button")
            .locator("text=$targetDay")
            .first()
            .click()

        // Wait for popover to close
        page
            .locator(
                "[role='dialog']",
            ).waitFor(
                com.microsoft.playwright.Locator
                    .WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN),
            )
    }

    fun fillStoppedEntryEditEndTime(time: String) {
        // Format: HH:MM (24-hour format)
        val input = page.locator("[data-testid='stopped-entry-edit-end-time-input']")
        input.fill(time)
    }

    fun clickSaveStoppedEntryEdit() {
        page.locator("[data-testid='save-stopped-entry-edit-button']").click()
    }

    fun clickCancelStoppedEntryEdit() {
        page.locator("[data-testid='cancel-stopped-entry-edit-button']").click()
    }

    /**
     * Asserts that the stopped entry edit form is visible with all expected controls.
     * This verifies:
     * - Edit container is visible
     * - Title input is visible
     * - Start date input is visible
     * - Start time input is visible
     * - End date input is visible
     * - End time input is visible
     * - Save button is visible
     * - Cancel button is visible
     */
    fun assertStoppedEntryEditVisible() {
        assertThat(page.locator("[data-testid='time-entry-edit']")).isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-title-input']")).isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-date-input']")).isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-time-input']")).isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-end-date-input']")).isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-end-time-input']")).isVisible()
        assertThat(page.locator("[data-testid='save-stopped-entry-edit-button']")).isVisible()
        assertThat(page.locator("[data-testid='cancel-stopped-entry-edit-button']")).isVisible()
    }

    /**
     * Asserts that the stopped entry edit form is hidden with all controls not visible.
     */
    fun assertStoppedEntryEditHidden() {
        assertThat(page.locator("[data-testid='time-entry-edit']")).not().isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-title-input']")).not().isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-date-input']")).not().isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-time-input']")).not().isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-end-date-input']")).not().isVisible()
        assertThat(page.locator("[data-testid='stopped-entry-edit-end-time-input']")).not().isVisible()
        assertThat(page.locator("[data-testid='save-stopped-entry-edit-button']")).not().isVisible()
        assertThat(page.locator("[data-testid='cancel-stopped-entry-edit-button']")).not().isVisible()
    }

    /**
     * Asserts that the stopped entry edit form has the expected values loaded.
     * @param expectedTitle Expected value in title input
     * @param expectedStartDate Expected value in start date input (locale-formatted)
     * @param expectedStartTime Expected value in start time input (locale-formatted)
     * @param expectedEndDate Expected value in end date input (locale-formatted)
     * @param expectedEndTime Expected value in end time input (locale-formatted)
     */
    fun assertStoppedEntryEditValues(
        expectedTitle: String,
        expectedStartDate: String,
        expectedStartTime: String,
        expectedEndDate: String,
        expectedEndTime: String,
    ) {
        assertThat(page.locator("[data-testid='stopped-entry-edit-title-input']")).hasValue(expectedTitle)
        assertThat(page.locator("[data-testid='stopped-entry-edit-date-input']")).hasValue(expectedStartDate)
        assertThat(page.locator("[data-testid='stopped-entry-edit-time-input']")).hasValue(expectedStartTime)
        assertThat(page.locator("[data-testid='stopped-entry-edit-end-date-input']")).hasValue(expectedEndDate)
        assertThat(page.locator("[data-testid='stopped-entry-edit-end-time-input']")).hasValue(expectedEndTime)
    }

    // Tag selector interaction methods

    /**
     * Clicks the tag selector button for active entry editing.
     */
    fun clickEditTagsButton() {
        page.locator("[data-testid='edit-tags-button']").click()
    }

    /**
     * Clicks the tag selector button for stopped entry editing.
     */
    fun clickStoppedEntryEditTagsButton() {
        page.locator("[data-testid='stopped-entry-edit-tags-button']").click()
    }

    /**
     * Toggles a specific tag in the tag selector.
     * @param tag The tag name to toggle
     * @param testIdPrefix The prefix for test IDs (e.g., "edit-tags" or "stopped-entry-edit-tags")
     */
    fun toggleTag(
        tag: String,
        testIdPrefix: String = "edit-tags",
    ) {
        page.locator("[data-testid='$testIdPrefix-checkbox-$tag']").click()
    }

    /**
     * Asserts that specific tags are selected in the tag selector.
     * @param expectedTags List of tags that should be checked
     * @param testIdPrefix The prefix for test IDs
     */
    fun assertTagsSelected(
        expectedTags: List<String>,
        testIdPrefix: String = "edit-tags",
    ) {
        // For each tag, verify it's checked
        expectedTags.forEach { tag ->
            val checkbox = page.locator("[data-testid='$testIdPrefix-checkbox-$tag']")
            assertThat(checkbox).isChecked()
        }
    }

    /**
     * Asserts that the tag selector button shows the correct highlight state based on selected tags.
     * @param hasSelectedTags Whether the button should be highlighted (true) or not (false)
     * @param testIdPrefix The prefix for test IDs
     */
    fun assertTagButtonHighlight(
        hasSelectedTags: Boolean,
        testIdPrefix: String = "edit-tags",
    ) {
        val button = page.locator("[data-testid='$testIdPrefix-button']")
        if (hasSelectedTags) {
            // Button should have the highlighted class (bg-teal-600)
            val classAttr = button.getAttribute("class") ?: ""
            assertTrue(classAttr.contains("bg-teal-600"), "Tag button should be highlighted when tags are selected")
        } else {
            // Button should not have the highlighted class
            val classAttr = button.getAttribute("class") ?: ""
            assertFalse(classAttr.contains("bg-teal-600"), "Tag button should not be highlighted when no tags are selected")
        }
    }
}
