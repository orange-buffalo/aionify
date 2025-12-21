package io.orangebuffalo.aionify

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole

/**
 * Page state model representing the complete state of the Time Logs page.
 * This model is used for comprehensive page state assertions.
 */
data class TimeLogsPageState(
    val currentEntry: CurrentEntryState = CurrentEntryState.NoActiveEntry(),
    val weekNavigation: WeekNavigationState = WeekNavigationState(),
    val dayGroups: List<DayGroupState> = emptyList(),
    val noEntriesMessageVisible: Boolean = false,
    val timezoneHintVisible: Boolean = true,
    val errorMessageVisible: Boolean = false,
    val errorMessage: String? = null
)

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
     */
    data class ActiveEntry(
        val title: String,
        val duration: String,
        val stopButtonVisible: Boolean = true,
        val inputVisible: Boolean = false,
        val startButtonVisible: Boolean = false
    ) : CurrentEntryState()
}

/**
 * State of the week navigation section.
 */
data class WeekNavigationState(
    val weekRange: String? = null,
    val previousButtonVisible: Boolean = true,
    val nextButtonVisible: Boolean = true
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
     */
    fun assertPageState(expectedState: TimeLogsPageState) {
        // Assert current entry panel state
        assertCurrentEntryState(expectedState.currentEntry)

        // Assert week navigation state
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
        if (expectedState.dayGroups.isNotEmpty()) {
            assertDayGroups(expectedState.dayGroups)
            // If we have day groups, "no entries" message should not be visible
            assertThat(page.locator("[data-testid='no-entries']")).not().isVisible()
        } else {
            // No day groups - check for "no entries" message
            if (expectedState.noEntriesMessageVisible) {
                assertThat(page.locator("[data-testid='no-entries']")).isVisible()
            }
            // Verify no day groups are shown
            assertThat(page.locator("[data-testid='day-group']")).hasCount(0)
        }

        // Assert timezone hint
        if (expectedState.timezoneHintVisible) {
            assertThat(page.locator("text=/Times shown in/")).isVisible()
        } else {
            assertThat(page.locator("text=/Times shown in/")).not().isVisible()
        }
    }

    private fun assertCurrentEntryState(currentEntry: CurrentEntryState) {
        when (currentEntry) {
            is CurrentEntryState.NoActiveEntry -> {
                // Verify input and start button are visible
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

                // Active timer should not be visible
                assertThat(page.locator("[data-testid='active-timer']")).not().isVisible()
                assertThat(page.locator("[data-testid='stop-button']")).not().isVisible()
            }

            is CurrentEntryState.ActiveEntry -> {
                // Verify active entry details
                assertThat(page.locator("[data-testid='current-entry-panel']").locator("text=${currentEntry.title}")).isVisible()
                assertThat(page.locator("[data-testid='active-timer']")).isVisible()
                assertThat(page.locator("[data-testid='active-timer']")).hasText(currentEntry.duration)

                if (currentEntry.stopButtonVisible) {
                    assertThat(page.locator("[data-testid='stop-button']")).isVisible()
                } else {
                    assertThat(page.locator("[data-testid='stop-button']")).not().isVisible()
                }

                // Input and start button should not be visible
                if (!currentEntry.inputVisible) {
                    assertThat(page.locator("[data-testid='new-entry-input']")).not().isVisible()
                }
                if (!currentEntry.startButtonVisible) {
                    assertThat(page.locator("[data-testid='start-button']")).not().isVisible()
                }
            }
        }
    }

    private fun assertWeekNavigationState(weekNav: WeekNavigationState) {
        if (weekNav.previousButtonVisible) {
            assertThat(page.locator("[data-testid='previous-week-button']")).isVisible()
        } else {
            assertThat(page.locator("[data-testid='previous-week-button']")).not().isVisible()
        }

        if (weekNav.nextButtonVisible) {
            assertThat(page.locator("[data-testid='next-week-button']")).isVisible()
        } else {
            assertThat(page.locator("[data-testid='next-week-button']")).not().isVisible()
        }

        if (weekNav.weekRange != null) {
            assertThat(page.locator("[data-testid='week-range']")).isVisible()
            assertThat(page.locator("[data-testid='week-range']")).hasText(weekNav.weekRange)
        }
    }

    private fun assertDayGroups(expectedDayGroups: List<DayGroupState>) {
        val dayGroupLocator = page.locator("[data-testid='day-group']")
        
        // Assert day group titles using content-based assertion
        val expectedTitles = expectedDayGroups.map { it.displayTitle }
        val titleLocators = dayGroupLocator.locator("[data-testid='day-title']")
        assertThat(titleLocators).containsText(expectedTitles.toTypedArray())
        
        // Assert day group total durations using content-based assertion
        val expectedDurations = expectedDayGroups.map { it.totalDuration }
        val durationLocators = dayGroupLocator.locator("[data-testid='day-total-duration']")
        // Duration locators contain "Total: " prefix, so we need to check if they contain the duration
        for (i in expectedDayGroups.indices) {
            assertThat(durationLocators.nth(i)).containsText(expectedDurations[i])
        }

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
     */
    fun clickContinueForEntry(entryTitle: String) {
        page.locator("[data-testid='time-entry']:has-text('$entryTitle')")
            .first()
            .locator("[data-testid='continue-button']")
            .click()
    }

    /**
     * Deletes an entry with the given title, confirming the deletion dialog.
     */
    fun deleteEntry(entryTitle: String) {
        // Find and click the menu button for the entry
        page.locator("[data-testid='time-entry']:has-text('$entryTitle')")
            .first()
            .locator("[data-testid='entry-menu-button']")
            .click()

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
}
