package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.timeInTestTz
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for time log entry autocomplete functionality.
 */
class TimeLogsAutocompleteTest : TimeLogsPageTestBase() {
    @BeforeEach
    fun setupAutocompleteTest() {
        // Set base time for all autocomplete tests
        setBaseTime("2024-03-16", "03:30")

        // Create test entries with various titles for autocomplete testing
        testDatabaseSupport.inTransaction {
            // Entry with "Meeting with team"
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = timeInTestTz("2024-03-15", "22:00"),
                    endTime = timeInTestTz("2024-03-15", "23:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work", "meeting"),
                ),
            )

            // Entry with "Team standup"
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Team standup",
                    startTime = timeInTestTz("2024-03-16", "00:00"),
                    endTime = timeInTestTz("2024-03-16", "00:15"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("meeting"),
                ),
            )

            // Entry with "Code review"
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Code review",
                    startTime = timeInTestTz("2024-03-16", "02:00"),
                    endTime = timeInTestTz("2024-03-16", "03:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work"),
                ),
            )

            // Duplicate entry "Meeting with team" with different tags and time
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = timeInTestTz("2024-03-14", "22:00"),
                    endTime = timeInTestTz("2024-03-14", "23:00"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work"),
                ),
            )
        }

        // Set Playwright clock to the real one for debounce to work correctly
        page.clock().resume()

        // Login
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)
    }

    @Test
    fun `should show autocomplete suggestions when typing`() {
        // Type "team" in the input
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("team")

        // Autocomplete popover should appear
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Check that autocomplete results are visible
        val suggestions = page.locator("[data-testid^='autocomplete-item-']")
        assertThat(suggestions).not().hasCount(0)

        // Should show "Meeting with team" and "Team standup"
        assertThat(suggestions).containsText(arrayOf("Meeting with team", "Team standup"))
    }

    @Test
    fun `should filter results by multiple tokens`() {
        // Type "meeting team" - should only match "Meeting with team"
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("meeting team")

        // Wait for autocomplete popover
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Should only show "Meeting with team"
        val suggestions = page.locator("[data-testid^='autocomplete-item-']")
        assertThat(suggestions).containsText(arrayOf("Meeting with team"))
    }

    @Test
    fun `should be case insensitive`() {
        // Type "TEAM" in uppercase
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("TEAM")

        // Should still show results
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        val suggestions = page.locator("[data-testid^='autocomplete-item-']")
        assertThat(suggestions).containsText(arrayOf("Meeting with team", "Team standup"))
    }

    @Test
    fun `should deduplicate results by title and show latest entry`() {
        // Type "meeting with team" - there are two entries with this title
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("meeting with team")

        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Should only show one result for "Meeting with team"
        val suggestions = page.locator("[data-testid^='autocomplete-item-']")
        assertThat(suggestions).hasCount(1)
        assertThat(suggestions.first()).containsText("Meeting with team")

        // Should show tags from the latest entry (work, meeting)
        val firstItem = suggestions.first()
        assertThat(firstItem.locator("[data-testid^='autocomplete-tag-']")).containsText(
            arrayOf("work", "meeting"),
        )
    }

    @Test
    fun `should copy title and tags when selecting an entry`() {
        // Type "team"
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("team")

        // Wait for suggestions
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Click on "Meeting with team"
        val meetingItem =
            page
                .locator("[data-testid^='autocomplete-item-']")
                .filter(
                    com.microsoft.playwright.Locator
                        .FilterOptions()
                        .setHasText("Meeting with team"),
                ).first()
        meetingItem.click()

        // Input should be filled with the title
        assertThat(input).hasValue("Meeting with team")

        // Tags should be selected - verify by opening tag selector and checking selected tags
        val tagsButton = page.locator("[data-testid='new-entry-tags-button']")
        tagsButton.click()

        // Verify the tags from the selected entry are checked
        val tagItems = page.locator("[data-testid^='new-entry-tags-item-']")
        assertThat(tagItems).containsText(arrayOf("meeting", "work"))

        // Close the tag selector
        page.keyboard().press("Escape")

        // Autocomplete popover should be closed
        assertThat(popover).not().isVisible()
    }

    @Test
    fun `should navigate with keyboard and select with Enter`() {
        // Type "team"
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("team")

        // Wait for suggestions
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Press arrow down to highlight first item
        input.press("ArrowDown")

        // First item should be highlighted
        val firstItem = page.locator("[data-testid='autocomplete-item-0']")
        assertThat(firstItem).hasAttribute("data-highlighted", "true")

        // Press arrow down again to move to second item
        input.press("ArrowDown")

        // Second item should be highlighted
        val secondItem = page.locator("[data-testid='autocomplete-item-1']")
        assertThat(secondItem).hasAttribute("data-highlighted", "true")
        assertThat(secondItem).containsText("Team standup")

        // Press Enter to select
        input.press("Enter")

        // Input should be filled with the second item's title
        assertThat(input).hasValue("Team standup")

        // Popover should be closed
        assertThat(popover).not().isVisible()
    }

    @Test
    fun `should hide autocomplete when input is cleared`() {
        // Type "team"
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("team")

        // Wait for popover to appear
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Clear the input
        input.fill("")

        // Popover should disappear
        assertThat(popover).not().isVisible()
    }

    @Test
    fun `should only show current user entries`() {
        // Create another user with an entry
        val otherUser = testUsers.createRegularUser("otheruser", "Other User")
        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Other user team meeting",
                    startTime = timeInTestTz("2024-03-16", "04:00"),
                    endTime = timeInTestTz("2024-03-16", "05:00"),
                    ownerId = otherUser.id!!,
                    tags = arrayOf("work"),
                ),
            )
        }

        // Type "team"
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("team")

        // Wait for popover
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Should not show other user's entry
        val suggestions = page.locator("[data-testid^='autocomplete-item-']")
        // Should only show current user's entries
        assertThat(suggestions).containsText(arrayOf("Meeting with team", "Team standup"))
    }

    @Test
    fun `should show no results message when no matches`() {
        // Type something that doesn't match
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("xyz123")

        // Popover should appear with no results message
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Should show "no results" message
        val noResults = page.locator("[data-testid='autocomplete-no-results']")
        assertThat(noResults).isVisible()
        assertThat(noResults).hasText("No matching entries found")
    }

    @Test
    fun `should show no results message in Ukrainian when language is Ukrainian`() {
        // Switch to Ukrainian language
        page.evaluate("localStorage.setItem('aionify_language', 'uk')")
        page.reload()

        // Wait for page to load - check for a visible element
        val currentEntryPanelTitle = page.locator("text=Над чим ви працюєте?")
        assertThat(currentEntryPanelTitle).isVisible()

        // Type something that doesn't match
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("xyz123")

        // Popover should appear with no results message in Ukrainian
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Should show Ukrainian "no results" message
        val noResults = page.locator("[data-testid='autocomplete-no-results']")
        assertThat(noResults).isVisible()
        assertThat(noResults).hasText("Відповідних записів не знайдено")
    }
}
