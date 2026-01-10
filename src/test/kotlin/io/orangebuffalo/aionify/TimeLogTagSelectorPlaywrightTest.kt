package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.LegacyTag
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Playwright tests for tag selection functionality on time log entries.
 *
 * Tests verify:
 * - Tag selector UI appears and functions correctly
 * - Tags are loaded from the current user only
 * - Legacy tags are filtered out from selection
 * - New tags can be added via input
 * - Selected tags are saved with new entries
 */
@MicronautTest(transactional = false)
class TimeLogTagSelectorPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

    private lateinit var testUser: User
    private lateinit var otherUser: User

    @BeforeEach
    fun setupTestData() {
        testUser = testUsers.createRegularUser("tagTestUser", "Tag Test User")
        otherUser = testUsers.createRegularUser("otherTagUser", "Other Tag User")
    }

    @Test
    fun `should display tag selector button`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify tag selector button is visible
        val tagButton = page.locator("[data-testid='new-entry-tags-button']")
        assertThat(tagButton).isVisible()
    }

    @Test
    fun `should open tag selector popover when button is clicked`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        // Verify popover is visible
        val popover = page.locator("[data-testid='new-entry-tags-popover']")
        assertThat(popover).isVisible()

        // Verify new tag input is visible
        val newTagInput = page.locator("[data-testid='new-entry-tags-new-tag-input']")
        assertThat(newTagInput).isVisible()

        // Verify add button is visible
        val addButton = page.locator("[data-testid='new-entry-tags-add-tag-button']")
        assertThat(addButton).isVisible()
    }

    @Test
    fun `should show empty message when no tags exist`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        // Verify empty message is visible
        val emptyMessage = page.locator("[data-testid='new-entry-tags-empty']")
        assertThat(emptyMessage).isVisible()
        assertThat(emptyMessage).containsText("No tags yet")
    }

    @Test
    fun `should list existing tags from current user entries`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create entries with tags for test user
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime,
                endTime = baseTime.plusSeconds(3600),
                title = "Task 1",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("kotlin", "backend"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        // Verify tags list is visible
        val tagsList = page.locator("[data-testid='new-entry-tags-list']")
        assertThat(tagsList).isVisible()

        // Verify exactly the expected tags are displayed in alphabetical order
        val tagItems = page.locator("[data-testid^='new-entry-tags-item-'] span")
        assertThat(tagItems).containsText(arrayOf("backend", "kotlin"))
    }

    @Test
    fun `should filter out legacy tags from selection`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create entry with tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime,
                endTime = baseTime.plusSeconds(3600),
                title = "Task 1",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("active-tag", "legacy-tag"),
            ),
        )

        // Mark one tag as legacy
        testDatabaseSupport.insert(
            LegacyTag(
                userId = requireNotNull(testUser.id),
                name = "legacy-tag",
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        // Verify only active tag is displayed (legacy tag is filtered out)
        val tagItems = page.locator("[data-testid^='new-entry-tags-item-'] span")
        assertThat(tagItems).containsText(arrayOf("active-tag"))
    }

    @Test
    fun `should only show tags from current user entries`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create entry with tags for test user
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime,
                endTime = baseTime.plusSeconds(3600),
                title = "My Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("my-tag"),
            ),
        )

        // Create entry with tags for other user
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime,
                endTime = baseTime.plusSeconds(3600),
                title = "Other Task",
                ownerId = requireNotNull(otherUser.id),
                tags = arrayOf("other-tag"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        // Verify only current user's tag is displayed (other user's tags are filtered out)
        val tagItems = page.locator("[data-testid^='new-entry-tags-item-'] span")
        assertThat(tagItems).containsText(arrayOf("my-tag"))
    }

    @Test
    fun `should enable add button only when input has content`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        val addButton = page.locator("[data-testid='new-entry-tags-add-tag-button']")
        val newTagInput = page.locator("[data-testid='new-entry-tags-new-tag-input']")

        // Verify button is disabled when input is empty
        assertThat(addButton).isDisabled()

        // Type some text
        newTagInput.fill("new-tag")

        // Verify button is now enabled
        assertThat(addButton).isEnabled()

        // Clear the input
        newTagInput.fill("")

        // Verify button is disabled again
        assertThat(addButton).isDisabled()
    }

    @Test
    fun `should add new tag when plus button is clicked`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        val newTagInput = page.locator("[data-testid='new-entry-tags-new-tag-input']")
        val addButton = page.locator("[data-testid='new-entry-tags-add-tag-button']")

        // Type and add a new tag
        newTagInput.fill("brand-new-tag")
        addButton.click()

        // Verify the new tag appears in the list (selected)
        val newTagItem = page.locator("[data-testid='new-entry-tags-item-brand-new-tag']")
        assertThat(newTagItem).isVisible()

        // Verify checkbox is checked
        val checkbox = page.locator("[data-testid='new-entry-tags-checkbox-brand-new-tag']")
        assertThat(checkbox).isChecked()

        // Verify input is cleared
        assertThat(newTagInput).hasValue("")
    }

    @Test
    fun `should add new tag when pressing Enter in input`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        val newTagInput = page.locator("[data-testid='new-entry-tags-new-tag-input']")

        // Type and press Enter
        newTagInput.fill("enter-tag")
        newTagInput.press("Enter")

        // Verify the new tag appears in the list (selected)
        val newTagItem = page.locator("[data-testid='new-entry-tags-item-enter-tag']")
        assertThat(newTagItem).isVisible()

        // Verify checkbox is checked
        val checkbox = page.locator("[data-testid='new-entry-tags-checkbox-enter-tag']")
        assertThat(checkbox).isChecked()
    }

    @Test
    fun `should toggle tag selection when clicking checkbox`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create entry with tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime,
                endTime = baseTime.plusSeconds(3600),
                title = "Task 1",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("toggle-tag"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Click the tag selector button
        page.locator("[data-testid='new-entry-tags-button']").click()

        val checkbox = page.locator("[data-testid='new-entry-tags-checkbox-toggle-tag']")

        // Verify initially unchecked
        assertThat(checkbox).not().isChecked()

        // Click to select
        checkbox.click()
        assertThat(checkbox).isChecked()

        // Click to unselect
        checkbox.click()
        assertThat(checkbox).not().isChecked()
    }

    @Test
    fun `should highlight tag button when tags are selected`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        val tagButton = page.locator("[data-testid='new-entry-tags-button']")

        // Get initial button classes (no tags selected)
        val initialClasses = tagButton.getAttribute("class") ?: ""

        // Open popover and add a tag
        tagButton.click()
        val newTagInput = page.locator("[data-testid='new-entry-tags-new-tag-input']")
        newTagInput.fill("highlight-tag")
        newTagInput.press("Enter")

        // Close popover
        page.keyboard().press("Escape")

        // Verify button style changed (should have teal background when tags selected)
        val highlightedClasses = tagButton.getAttribute("class") ?: ""
        assertTrue(
            highlightedClasses.contains("bg-teal-600") || highlightedClasses.contains("teal"),
            "Button should have teal styling when tags are selected. Classes: $highlightedClasses",
        )
    }

    @Test
    fun `should save entry with selected tags`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create existing entry with tags to populate tag list
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusSeconds(7200),
                endTime = baseTime.minusSeconds(3600),
                title = "Previous Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("existing-tag", "another-tag"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Open tag selector and select tags
        page.locator("[data-testid='new-entry-tags-button']").click()
        page.locator("[data-testid='new-entry-tags-checkbox-existing-tag']").click()

        // Add a new tag
        val newTagInput = page.locator("[data-testid='new-entry-tags-new-tag-input']")
        newTagInput.fill("fresh-tag")
        newTagInput.press("Enter")

        // Close the popover
        page.keyboard().press("Escape")

        // Fill in title and start entry
        val titleInput = page.locator("[data-testid='new-entry-input']")
        titleInput.fill("Task with tags")
        page.locator("[data-testid='start-button']").click()

        // Wait for entry to be created
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()

        // Verify entry was saved with tags in database
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertEquals("Task with tags", activeEntry!!.title)
        assertEquals(2, activeEntry.tags.size)
        assertTrue(activeEntry.tags.contains("existing-tag"))
        assertTrue(activeEntry.tags.contains("fresh-tag"))
    }

    @Test
    fun `should start entry without tags when none are selected`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Fill in title and start entry without selecting tags
        val titleInput = page.locator("[data-testid='new-entry-input']")
        titleInput.fill("Task without tags")
        page.locator("[data-testid='start-button']").click()

        // Wait for entry to be created
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()

        // Verify entry was saved without tags in database
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(requireNotNull(testUser.id)).orElse(null)
        assertEquals("Task without tags", activeEntry!!.title)
        assertEquals(0, activeEntry.tags.size)
    }

    @Test
    fun `should clear selected tags after starting entry`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create existing entry with tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.minusSeconds(7200),
                endTime = baseTime.minusSeconds(3600),
                title = "Previous Task",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("test-tag"),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Select a tag
        page.locator("[data-testid='new-entry-tags-button']").click()
        page.locator("[data-testid='new-entry-tags-checkbox-test-tag']").click()
        page.keyboard().press("Escape")

        // Verify button is highlighted (tag selected)
        val tagButton = page.locator("[data-testid='new-entry-tags-button']")
        val highlightedClasses = tagButton.getAttribute("class") ?: ""
        assertTrue(highlightedClasses.contains("bg-teal-600"))

        // Start an entry
        val titleInput = page.locator("[data-testid='new-entry-input']")
        titleInput.fill("First task")
        page.locator("[data-testid='start-button']").click()

        // Wait for entry to be created
        assertThat(page.locator("[data-testid='active-timer']")).isVisible()

        // Stop the entry
        page.locator("[data-testid='stop-button']").click()

        // Verify tag button is no longer highlighted (tags were cleared)
        val clearedClasses = tagButton.getAttribute("class") ?: ""
        assertTrue(
            !clearedClasses.contains("bg-teal-600"),
            "Button should not have teal background after starting entry. Classes: $clearedClasses",
        )
    }
}
