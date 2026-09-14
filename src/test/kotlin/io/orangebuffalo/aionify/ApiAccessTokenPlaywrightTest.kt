package io.orangebuffalo.aionify

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import io.orangebuffalo.aionify.domain.UserApiAccessTokenRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@MicronautTest(transactional = false)
class ApiAccessTokenPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var userApiAccessTokenRepository: UserApiAccessTokenRepository

    private lateinit var regularUser: User

    companion object {
        private const val MASKED_TOKEN = "••••••••••••••••••••••••••••••••"
        private const val GITHUB_TOKEN = "githubToken1234567890abcdefghijklmnopqrstuvwxyz12"
        private const val JIRA_TOKEN = "jiraToken1234567890abcdefghijklmnopqrstuvwxyz1234"
    }

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser("apiTokenTestUser", "API Token Test User")
    }

    private fun navigateToSettings(user: User = regularUser) {
        loginViaToken("/portal/settings", user, testAuthSupport)
        assertThat(page.locator("[data-testid='api-token-loading']")).not().isVisible()
    }

    private fun insertToken(
        name: String,
        token: String,
        createdAt: Instant,
        user: User = regularUser,
    ): UserApiAccessToken =
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = requireNotNull(user.id),
                token = token,
                name = name,
                createdAt = createdAt,
            ),
        )

    private fun tokenRow(name: String): Locator =
        page.locator("[data-testid='api-token-row']").filter(Locator.FilterOptions().setHasText(name))

    private fun findTokens(user: User = regularUser) =
        testDatabaseSupport.inTransaction {
            userApiAccessTokenRepository.findAllByUserId(requireNotNull(user.id))
        }

    @Test
    fun `should display API tokens panel with OpenAPI schema link`() {
        navigateToSettings()

        assertThat(page.locator("[data-testid='api-token-title']")).containsText("API Access Tokens")

        val schemaLink = page.locator("[data-testid='openapi-schema-link']")
        assertThat(schemaLink).containsText("View OpenAPI Schema")
        assertThat(schemaLink).hasAttribute("href", "/api/schema")
        assertThat(schemaLink).hasAttribute("target", "_blank")
        assertThat(schemaLink).hasAttribute("rel", "noopener noreferrer")
    }

    @Test
    fun `should show empty state when user has no API tokens`() {
        navigateToSettings()

        assertThat(page.locator("[data-testid='api-token-no-tokens-message']"))
            .containsText("You have no API tokens yet")
        assertThat(page.locator("[data-testid='api-tokens-list']")).not().isVisible()
        assertThat(page.locator("[data-testid='new-api-token-name-input']")).hasValue("")
        assertThat(page.locator("[data-testid='create-api-token-button']")).isDisabled()

        captureUiReviewScreenshot("empty-state")
    }

    @Test
    fun `should create named API token`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        navigateToSettings()

        page.locator("[data-testid='new-api-token-name-input']").fill("GitHub integration")
        page.locator("[data-testid='create-api-token-button']").click()

        assertThat(page.locator("[data-testid='api-token-success']"))
            .containsText("API token \"GitHub integration\" created successfully")
        assertThat(page.locator("[data-testid='api-token-name']")).containsText(arrayOf("GitHub integration"))
        assertThat(tokenRow("GitHub integration").locator("[data-testid='api-token-input']")).hasValue(MASKED_TOKEN)
        assertThat(tokenRow("GitHub integration").locator("[data-testid='show-api-token-button']")).isVisible()
        assertThat(tokenRow("GitHub integration").locator("[data-testid='copy-api-token-button']")).not().isVisible()
        assertThat(page.locator("[data-testid='api-token-no-tokens-message']")).not().isVisible()
        assertThat(page.locator("[data-testid='new-api-token-name-input']")).hasValue("")

        val tokens = findTokens()
        assertEquals(listOf("GitHub integration"), tokens.map { it.name })
        assertTrue(tokens[0].token.matches(Regex("^[a-zA-Z0-9]{50}$")), "Token should be 50 alphanumeric characters")
        assertEquals(baseTime, tokens[0].createdAt)
    }

    @Test
    fun `should list tokens in creation order`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        insertToken("Jira integration", JIRA_TOKEN, baseTime.minusSeconds(3600))
        insertToken("GitHub integration", GITHUB_TOKEN, baseTime.minusSeconds(7200))

        navigateToSettings()

        assertThat(page.locator("[data-testid='api-token-name']"))
            .containsText(arrayOf("GitHub integration", "Jira integration"))
        assertThat(page.locator("[data-testid='api-token-created-at']")).containsText(arrayOf("Created", "Created"))
        assertThat(tokenRow("GitHub integration").locator("[data-testid='api-token-input']")).hasValue(MASKED_TOKEN)
        assertThat(tokenRow("Jira integration").locator("[data-testid='api-token-input']")).hasValue(MASKED_TOKEN)
        assertThat(page.locator("[data-testid='api-token-no-tokens-message']")).not().isVisible()

        captureUiReviewScreenshot("tokens-list")
        captureUiReviewScreenshot("tokens-list-mobile", viewportWidth = 390)
    }

    @Test
    fun `should reveal and copy selected token`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        insertToken("GitHub integration", GITHUB_TOKEN, baseTime.minusSeconds(7200))
        insertToken("Jira integration", JIRA_TOKEN, baseTime.minusSeconds(3600))
        navigateToSettings()

        val githubRow = tokenRow("GitHub integration")
        githubRow.locator("[data-testid='show-api-token-button']").click()

        assertThat(githubRow.locator("[data-testid='api-token-input']")).hasValue(GITHUB_TOKEN)
        assertThat(githubRow.locator("[data-testid='show-api-token-button']")).not().isVisible()
        assertThat(tokenRow("Jira integration").locator("[data-testid='api-token-input']")).hasValue(MASKED_TOKEN)

        githubRow.locator("[data-testid='copy-api-token-button']").click()

        assertThat(page.locator("[data-testid='api-token-success']")).containsText("API token copied to clipboard")
        assertEquals(GITHUB_TOKEN, page.evaluate("() => navigator.clipboard.readText()"))
    }

    @Test
    fun `should regenerate only selected token`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        insertToken("GitHub integration", GITHUB_TOKEN, baseTime.minusSeconds(7200))
        insertToken("Jira integration", JIRA_TOKEN, baseTime.minusSeconds(3600))
        navigateToSettings()

        val githubRow = tokenRow("GitHub integration")
        val githubInput = githubRow.locator("[data-testid='api-token-input']")
        githubRow.locator("[data-testid='show-api-token-button']").click()
        assertThat(githubInput).hasValue(GITHUB_TOKEN)

        githubRow.locator("[data-testid='regenerate-api-token-button']").click()

        assertThat(page.locator("[data-testid='api-token-success']"))
            .containsText("API token \"GitHub integration\" regenerated successfully")
        assertThat(githubInput).hasValue(MASKED_TOKEN)

        githubRow.locator("[data-testid='show-api-token-button']").click()
        // Wait for the token value to be loaded before reading it
        assertThat(githubInput).not().hasValue(MASKED_TOKEN)
        val newTokenValue = githubInput.inputValue()

        assertNotEquals(GITHUB_TOKEN, newTokenValue)
        val tokensByName = findTokens().associate { it.name to it.token }
        assertEquals(mapOf("GitHub integration" to newTokenValue, "Jira integration" to JIRA_TOKEN), tokensByName)
    }

    @Test
    fun `should delete selected token after confirmation`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        insertToken("GitHub integration", GITHUB_TOKEN, baseTime.minusSeconds(7200))
        insertToken("Jira integration", JIRA_TOKEN, baseTime.minusSeconds(3600))
        navigateToSettings()

        tokenRow("Jira integration").locator("[data-testid='delete-api-token-button']").click()

        val dialogTitle = page.getByRole(AriaRole.HEADING, Page.GetByRoleOptions().setName("Delete API Token"))
        assertThat(dialogTitle).isVisible()
        assertThat(page.getByRole(AriaRole.DIALOG))
            .containsText("Are you sure you want to delete the API token \"Jira integration\"?")
        captureUiReviewScreenshot("delete-confirmation")

        page.locator("[data-testid='confirm-delete-api-token-button']").click()

        assertThat(page.locator("[data-testid='api-token-success']"))
            .containsText("API token \"Jira integration\" deleted successfully")
        assertThat(dialogTitle).not().isVisible()
        assertThat(page.locator("[data-testid='api-token-name']")).containsText(arrayOf("GitHub integration"))
        assertEquals(listOf("GitHub integration"), findTokens().map { it.name })
    }

    @Test
    fun `should keep token when deletion is cancelled`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        insertToken("GitHub integration", GITHUB_TOKEN, baseTime)
        navigateToSettings()

        tokenRow("GitHub integration").locator("[data-testid='delete-api-token-button']").click()
        val dialogTitle = page.getByRole(AriaRole.HEADING, Page.GetByRoleOptions().setName("Delete API Token"))
        assertThat(dialogTitle).isVisible()

        page.locator("[data-testid='cancel-delete-api-token-button']").click()

        assertThat(dialogTitle).not().isVisible()
        assertThat(page.locator("[data-testid='api-token-name']")).containsText(arrayOf("GitHub integration"))
        assertThat(page.locator("[data-testid='api-token-success']")).not().isVisible()
        assertEquals(listOf(GITHUB_TOKEN), findTokens().map { it.token })
    }

    @Test
    fun `should show error when creating token with existing name`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        insertToken("GitHub integration", GITHUB_TOKEN, baseTime)
        navigateToSettings()

        page.locator("[data-testid='new-api-token-name-input']").fill("GitHub integration")
        page.locator("[data-testid='create-api-token-button']").click()

        assertThat(page.locator("[data-testid='api-token-error']"))
            .containsText("An API token with this name already exists")
        assertThat(page.locator("[data-testid='api-token-success']")).not().isVisible()
        assertThat(page.locator("[data-testid='api-token-name']")).containsText(arrayOf("GitHub integration"))
        assertThat(page.locator("[data-testid='new-api-token-name-input']")).hasValue("GitHub integration")
        assertEquals(listOf(GITHUB_TOKEN), findTokens().map { it.token })

        captureUiReviewScreenshot("duplicate-name-error")
    }

    @Test
    fun `should not show tokens of other users`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        val otherUser = testUsers.createRegularUser("otherApiTokenUser", "Other API Token User")
        insertToken("GitHub integration", GITHUB_TOKEN, baseTime)
        insertToken("Jira integration", JIRA_TOKEN, baseTime, user = otherUser)

        navigateToSettings(otherUser)

        assertThat(page.locator("[data-testid='api-token-name']")).containsText(arrayOf("Jira integration"))
        tokenRow("Jira integration").locator("[data-testid='show-api-token-button']").click()
        assertThat(tokenRow("Jira integration").locator("[data-testid='api-token-input']")).hasValue(JIRA_TOKEN)
    }
}
