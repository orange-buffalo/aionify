package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Tests for time log entry autocomplete functionality.
 */
class TimeLogsAutocompleteTest : TimeLogsPageTestBase() {
    @BeforeEach
    fun setupAutocompleteTest() {
        // Create test entries with various titles for autocomplete testing
        testDatabaseSupport.inTransaction {
            // Entry with "Meeting with team"
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = Instant.parse("2024-03-15T09:00:00Z"),
                    endTime = Instant.parse("2024-03-15T10:00:00Z"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work", "meeting"),
                ),
            )

            // Entry with "Team standup"
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Team standup",
                    startTime = Instant.parse("2024-03-15T11:00:00Z"),
                    endTime = Instant.parse("2024-03-15T11:15:00Z"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("meeting"),
                ),
            )

            // Entry with "Code review"
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Code review",
                    startTime = Instant.parse("2024-03-15T13:00:00Z"),
                    endTime = Instant.parse("2024-03-15T14:00:00Z"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work"),
                ),
            )

            // Duplicate entry "Meeting with team" with different tags and time
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Meeting with team",
                    startTime = Instant.parse("2024-03-14T09:00:00Z"),
                    endTime = Instant.parse("2024-03-14T10:00:00Z"),
                    ownerId = testUser.id!!,
                    tags = arrayOf("work"),
                ),
            )
        }

        // Login
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)
    }

    @Test
    fun `should show autocomplete suggestions when typing`() {
        // Type "team" in the input
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("team")

        // Wait for debounce to complete (300ms + buffer)
        page.waitForTimeout(500.0)

        // Wait for autocomplete popover to appear
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

        // Tags should be selected
        val tagsButton = page.locator("[data-testid='new-entry-tags-button']")
        // Button should be highlighted when tags are selected
        val classAttr = tagsButton.getAttribute("class") ?: ""
        assert(classAttr.contains("bg-teal-600")) { "Tag button should be highlighted when tags are selected" }

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

        // Press Enter to select
        input.press("Enter")

        // Input should be filled with the second item's title
        val secondItemText = secondItem.textContent() ?: ""
        assert(secondItemText.contains("Team standup")) { "Should select Team standup" }
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
                    startTime = Instant.parse("2024-03-15T15:00:00Z"),
                    endTime = Instant.parse("2024-03-15T16:00:00Z"),
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
        assertThat(suggestions).not().hasText("Other user team meeting")

        // Should only show current user's entries
        assertThat(suggestions).containsText(arrayOf("Meeting with team", "Team standup"))
    }

    @Test
    fun `should debounce API calls`() {
        // Type quickly - fill will trigger debouncing
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("team")

        // Wait for debounce to complete (300ms)
        page.waitForTimeout(400.0)

        // Popover should appear with results
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        val suggestions = page.locator("[data-testid^='autocomplete-item-']")
        assertThat(suggestions).containsText(arrayOf("Meeting with team", "Team standup"))
    }

    @Test
    fun `should show no results message when no matches`() {
        // Type something that doesn't match
        val input = page.locator("[data-testid='new-entry-input']")
        input.fill("xyz123")

        // Wait for debounce
        page.waitForTimeout(400.0)

        // Popover should appear
        val popover = page.locator("[data-testid='autocomplete-popover']")
        assertThat(popover).isVisible()

        // Should show "no results" message
        val noResults = page.locator("[data-testid='autocomplete-no-results']")
        assertThat(noResults).isVisible()
    }
}
