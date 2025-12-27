package io.orangebuffalo.aionify

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.LegacyTag
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@MicronautTest(transactional = false)
class TagsPlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var regularUser: User
    private lateinit var otherUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test users
        regularUser = testUsers.createRegularUser("tagsTestUser", "Tags Test User")
        otherUser = testUsers.createRegularUser("otherUser", "Other User")
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    @Test
    fun `should display tags panel on settings page`() {

        navigateToSettingsViaToken()

        // Verify settings page is visible
        val settingsPage = page.locator("[data-testid='settings-page']")
        assertThat(settingsPage).isVisible()

        // Wait for loading to complete
        val tagsLoading = page.locator("[data-testid='tags-loading']")
        assertThat(tagsLoading).not().isVisible()

        // Verify Tags title is present
        assertThat(page.locator("[data-testid='tags-title']")).isVisible()
    }

    @Test
    fun `should show empty message when user has no tags`() {
        navigateToSettingsViaToken()

        // Wait for empty state to be visible
        val tagsEmpty = page.locator("[data-testid='tags-empty']")
        assertThat(tagsEmpty).isVisible()
        assertThat(tagsEmpty).containsText("No tags found")
    }

    @Test
    fun `should display tags table when user has tags`() {
        // Create entries with tags for regular user
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task 1",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin", "backend")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Task 2",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("react", "frontend")
            )
        )

        navigateToSettingsViaToken()

        // Verify table is visible
        val tagsTable = page.locator("[data-testid='tags-table']")
        assertThat(tagsTable).isVisible()

        // Verify table headers
        assertThat(page.locator("[data-testid='tags-header-tag']")).isVisible()
        assertThat(page.locator("[data-testid='tags-header-count']")).isVisible()

        // Verify tags are displayed (sorted alphabetically)
        val backendRow = page.locator("[data-testid='tag-row-backend']")
        assertThat(backendRow).isVisible()
        assertThat(page.locator("[data-testid='tag-name-backend']")).containsText("backend")
        assertThat(page.locator("[data-testid='tag-count-backend']")).containsText("1")

        val frontendRow = page.locator("[data-testid='tag-row-frontend']")
        assertThat(frontendRow).isVisible()
        assertThat(page.locator("[data-testid='tag-name-frontend']")).containsText("frontend")
        assertThat(page.locator("[data-testid='tag-count-frontend']")).containsText("1")

        val kotlinRow = page.locator("[data-testid='tag-row-kotlin']")
        assertThat(kotlinRow).isVisible()
        assertThat(page.locator("[data-testid='tag-name-kotlin']")).containsText("kotlin")
        assertThat(page.locator("[data-testid='tag-count-kotlin']")).containsText("1")

        val reactRow = page.locator("[data-testid='tag-row-react']")
        assertThat(reactRow).isVisible()
        assertThat(page.locator("[data-testid='tag-name-react']")).containsText("react")
        assertThat(page.locator("[data-testid='tag-count-react']")).containsText("1")
    }

    @Test
    fun `should show correct count for tags used in multiple entries`() {
        // Create entries with overlapping tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task 1",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin", "backend")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Task 2",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin", "testing")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T14:00:00Z"),
                endTime = null,
                title = "Task 3",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin")
            )
        )

        navigateToSettingsViaToken()

        // Verify kotlin has count of 3
        assertThat(page.locator("[data-testid='tag-count-kotlin']")).containsText("3")

        // Verify backend has count of 1
        assertThat(page.locator("[data-testid='tag-count-backend']")).containsText("1")

        // Verify testing has count of 1
        assertThat(page.locator("[data-testid='tag-count-testing']")).containsText("1")
    }

    @Test
    fun `should only show tags from current user entries`() {
        // Create entries with tags for regular user
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "My Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("my-tag")
            )
        )

        // Create entries with different tags for other user
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T14:00:00Z"),
                endTime = null,
                title = "Other Task",
                ownerId = requireNotNull(otherUser.id),
                tags = arrayOf("other-tag")
            )
        )

        navigateToSettingsViaToken()

        // Verify only current user's tag is visible
        assertThat(page.locator("[data-testid='tag-row-my-tag']")).isVisible()

        // Verify other user's tag is not visible
        assertThat(page.locator("[data-testid='tag-row-other-tag']")).not().isVisible()
    }

    @Test
    fun `should display tags in alphabetical order`() {
        // Create entries with tags in non-alphabetical order
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("zebra", "apple", "mango", "banana")
            )
        )

        navigateToSettingsViaToken()

        // Get all tag rows
        val tagRows = page.locator("[data-testid^='tag-row-']")
        val count = tagRows.count()

        // Verify we have 4 tags
        assertThat(tagRows).hasCount(4)

        // Verify they are in alphabetical order
        assertThat(tagRows.nth(0).locator("[data-testid^='tag-name-']")).containsText("apple")
        assertThat(tagRows.nth(1).locator("[data-testid^='tag-name-']")).containsText("banana")
        assertThat(tagRows.nth(2).locator("[data-testid^='tag-name-']")).containsText("mango")
        assertThat(tagRows.nth(3).locator("[data-testid^='tag-name-']")).containsText("zebra")
    }

    @Test
    fun `should handle mix of entries with and without tags`() {
        // Create entries with and without tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Tagged Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("important")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Untagged Task",
                ownerId = requireNotNull(regularUser.id),
                tags = emptyArray()
            )
        )

        navigateToSettingsViaToken()

        // Verify only the tag from tagged entry is shown
        assertThat(page.locator("[data-testid='tag-row-important']")).isVisible()
        assertThat(page.locator("[data-testid='tag-count-important']")).containsText("1")

        // Verify we only have 1 tag row
        assertThat(page.locator("[data-testid^='tag-row-']")).hasCount(1)
    }

    @Test
    fun `should display legacy tag column with tooltip`() {
        // Create entry with tag
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin")
            )
        )

        navigateToSettingsViaToken()

        // Verify "Is Legacy?" header is visible
        assertThat(page.locator("[data-testid='tags-header-legacy']")).isVisible()

        // Verify tooltip trigger is visible
        assertThat(page.locator("[data-testid='tags-legacy-tooltip-trigger']")).isVisible()
    }

    @Test
    fun `should show Yes for legacy tags and empty for regular tags`() {
        // Create entries with tags
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task 1",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin", "java")
            )
        )

        // Mark "kotlin" as legacy
        testDatabaseSupport.insert(
            LegacyTag(
                userId = requireNotNull(regularUser.id),
                name = "kotlin"
            )
        )

        navigateToSettingsViaToken()

        // Verify "kotlin" shows "Yes" in legacy column
        val kotlinLegacyCell = page.locator("[data-testid='tag-legacy-kotlin']")
        assertThat(kotlinLegacyCell).containsText("Yes")

        // Verify "java" has empty legacy cell
        val javaLegacyCell = page.locator("[data-testid='tag-legacy-java']")
        assertThat(javaLegacyCell).not().containsText("Yes")
    }

    @Test
    fun `should show mark as legacy button for regular tags`() {
        // Create entry with tag
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin")
            )
        )

        navigateToSettingsViaToken()

        // Verify "Mark as legacy" button is visible
        assertThat(page.locator("[data-testid='tag-mark-legacy-kotlin']")).isVisible()

        // Verify "Remove legacy mark" button is not visible
        assertThat(page.locator("[data-testid='tag-unmark-legacy-kotlin']")).not().isVisible()
    }

    @Test
    fun `should show remove legacy mark button for legacy tags`() {
        // Create entry with tag
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin")
            )
        )

        // Mark as legacy
        testDatabaseSupport.insert(
            LegacyTag(
                userId = requireNotNull(regularUser.id),
                name = "kotlin"
            )
        )

        navigateToSettingsViaToken()

        // Verify "Remove legacy mark" button is visible
        assertThat(page.locator("[data-testid='tag-unmark-legacy-kotlin']")).isVisible()

        // Verify "Mark as legacy" button is not visible
        assertThat(page.locator("[data-testid='tag-mark-legacy-kotlin']")).not().isVisible()
    }

    @Test
    fun `should mark tag as legacy when clicking mark button`() {
        // Create entry with tag
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin")
            )
        )

        navigateToSettingsViaToken()

        // Click "Mark as legacy" button
        page.locator("[data-testid='tag-mark-legacy-kotlin']").click()

        // Wait for the action to complete and page to update
        assertThat(page.locator("[data-testid='tag-legacy-kotlin']")).containsText("Yes")

        // Verify button changed to "Remove legacy mark"
        assertThat(page.locator("[data-testid='tag-unmark-legacy-kotlin']")).isVisible()
        assertThat(page.locator("[data-testid='tag-mark-legacy-kotlin']")).not().isVisible()
    }

    @Test
    fun `should unmark tag as legacy when clicking remove button`() {
        // Create entry with tag
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("kotlin")
            )
        )

        // Mark as legacy
        testDatabaseSupport.insert(
            LegacyTag(
                userId = requireNotNull(regularUser.id),
                name = "kotlin"
            )
        )

        navigateToSettingsViaToken()

        // Click "Remove legacy mark" button
        page.locator("[data-testid='tag-unmark-legacy-kotlin']").click()

        // Wait for the action to complete and page to update
        assertThat(page.locator("[data-testid='tag-legacy-kotlin']")).not().containsText("Yes")

        // Verify button changed to "Mark as legacy"
        assertThat(page.locator("[data-testid='tag-mark-legacy-kotlin']")).isVisible()
        assertThat(page.locator("[data-testid='tag-unmark-legacy-kotlin']")).not().isVisible()
    }

    @Test
    fun `should only consider current user legacy tags`() {
        // Create entries with same tag for both users
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Regular User Task",
                ownerId = requireNotNull(regularUser.id),
                tags = arrayOf("shared-tag")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Other User Task",
                ownerId = requireNotNull(otherUser.id),
                tags = arrayOf("shared-tag")
            )
        )

        // Other user marks the tag as legacy
        testDatabaseSupport.insert(
            LegacyTag(
                userId = requireNotNull(otherUser.id),
                name = "shared-tag"
            )
        )

        navigateToSettingsViaToken()

        // Verify regular user sees tag as NOT legacy (other user's legacy mark doesn't affect them)
        val legacyCell = page.locator("[data-testid='tag-legacy-shared-tag']")
        assertThat(legacyCell).not().containsText("Yes")

        // Verify "Mark as legacy" button is shown (not "Remove legacy mark")
        assertThat(page.locator("[data-testid='tag-mark-legacy-shared-tag']")).isVisible()
        assertThat(page.locator("[data-testid='tag-unmark-legacy-shared-tag']")).not().isVisible()
    }
}
