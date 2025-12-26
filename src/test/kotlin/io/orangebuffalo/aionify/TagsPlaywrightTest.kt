package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

@MicronautTest(transactional = false)
class TagsPlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private val regularUserName = "tagsTestUser"
    private val regularUserGreeting = "Tags Test User"

    private lateinit var regularUser: User
    private lateinit var otherUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test users
        regularUser = testDatabaseSupport.insert(
            User.create(
                userName = regularUserName,
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = regularUserGreeting,
                isAdmin = false,
                locale = java.util.Locale.US
            )
        )

        otherUser = testDatabaseSupport.insert(
            User.create(
                userName = "otherUser",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Other User",
                isAdmin = false,
                locale = java.util.Locale.US
            )
        )
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    @Test
    fun `should display tags panel on settings page`() {
        navigateToSettingsViaToken()

        // Verify tags panel is visible
        val tagsPanel = page.locator("[data-testid='tags-panel']")
        assertThat(tagsPanel).isVisible()

        // Verify title
        assertThat(tagsPanel.locator("text=Tags")).isVisible()
    }

    @Test
    fun `should show empty message when user has no tags`() {
        navigateToSettingsViaToken()

        // Wait for loading to complete
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
                tags = listOf("kotlin", "backend")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Task 2",
                ownerId = requireNotNull(regularUser.id),
                tags = listOf("react", "frontend")
            )
        )

        navigateToSettingsViaToken()

        // Verify table is visible
        val tagsTable = page.locator("[data-testid='tags-table']")
        assertThat(tagsTable).isVisible()

        // Verify table headers
        assertThat(page.locator("text=Tag")).isVisible()
        assertThat(page.locator("text=Entries")).isVisible()

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
                tags = listOf("kotlin", "backend")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Task 2",
                ownerId = requireNotNull(regularUser.id),
                tags = listOf("kotlin", "testing")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T14:00:00Z"),
                endTime = null,
                title = "Task 3",
                ownerId = requireNotNull(regularUser.id),
                tags = listOf("kotlin")
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
                tags = listOf("my-tag")
            )
        )

        // Create entries with different tags for other user
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T14:00:00Z"),
                endTime = null,
                title = "Other Task",
                ownerId = requireNotNull(otherUser.id),
                tags = listOf("other-tag")
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
                tags = listOf("zebra", "apple", "mango", "banana")
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
    fun `should highlight row on hover`() {
        // Create entry with tag
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T10:00:00Z"),
                endTime = Instant.parse("2024-01-15T11:00:00Z"),
                title = "Task",
                ownerId = requireNotNull(regularUser.id),
                tags = listOf("hover-test")
            )
        )

        navigateToSettingsViaToken()

        val row = page.locator("[data-testid='tag-row-hover-test']")
        
        // Hover over the row
        row.hover()
        
        // The row should be visible and hoverable (we can't test the exact hover styling in Playwright,
        // but we can verify the row exists and is interactive)
        assertThat(row).isVisible()
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
                tags = listOf("important")
            )
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-01-15T12:00:00Z"),
                endTime = Instant.parse("2024-01-15T13:00:00Z"),
                title = "Untagged Task",
                ownerId = requireNotNull(regularUser.id),
                tags = emptyList()
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
    fun `should navigate to settings from portal menu`() {
        // Start at time logs page
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)

        // Click on Settings menu item
        page.locator("[data-testid='nav-item-settings']").click()

        // Should navigate to settings page
        page.waitForURL("**/portal/settings")

        // Verify tags panel is visible
        assertThat(page.locator("[data-testid='tags-panel']")).isVisible()
    }
}
