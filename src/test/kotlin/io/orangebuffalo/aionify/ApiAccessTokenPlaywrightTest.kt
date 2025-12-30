package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import io.orangebuffalo.aionify.domain.UserApiAccessTokenRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class ApiAccessTokenPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var userApiAccessTokenRepository: UserApiAccessTokenRepository

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test user
        regularUser = testUsers.createRegularUser("apiTokenTestUser", "API Token Test User")
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    @Test
    fun `should display API token panel on settings page`() {
        navigateToSettingsViaToken()

        // Verify settings page is visible
        val settingsPage = page.locator("[data-testid='settings-page']")
        assertThat(settingsPage).isVisible()

        // Verify API token title is present
        assertThat(page.locator("[data-testid='api-token-title']")).isVisible()
        assertThat(page.locator("[data-testid='api-token-title']")).containsText("API Access Token")
    }

    @Test
    fun `should show generate button when user has no API token`() {
        navigateToSettingsViaToken()

        // Wait for loading to complete
        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        // Verify no token message is visible
        val noTokenMessage = page.locator("[data-testid='api-token-no-token-message']")
        assertThat(noTokenMessage).isVisible()
        assertThat(noTokenMessage).containsText("You have not yet set up the API token")

        // Verify generate button is visible
        val generateButton = page.locator("[data-testid='generate-api-token-button']")
        assertThat(generateButton).isVisible()
        assertThat(generateButton).containsText("Generate API Token")
    }

    @Test
    fun `should generate API token when clicking generate button`() {
        navigateToSettingsViaToken()

        // Wait for loading to complete
        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        // Click generate button
        val generateButton = page.locator("[data-testid='generate-api-token-button']")
        generateButton.click()

        // Verify success message appears
        val successMessage = page.locator("[data-testid='api-token-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("API token generated successfully")

        // Verify token input and regenerate button are now visible
        val tokenInput = page.locator("[data-testid='api-token-input']")
        assertThat(tokenInput).isVisible()

        val regenerateButton = page.locator("[data-testid='regenerate-api-token-button']")
        assertThat(regenerateButton).isVisible()
        assertThat(regenerateButton).containsText("Re-generate")

        // Verify no token message is no longer visible
        val noTokenMessage = page.locator("[data-testid='api-token-no-token-message']")
        assertThat(noTokenMessage).not().isVisible()
    }

    @Test
    fun `should display masked token and show button when user has API token`() {
        // Create API token for user
        val userId = requireNotNull(regularUser.id)
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = userId,
                token = "test1234567890abcdefghijklmnopqrstuvwxyz12345678",
            ),
        )

        navigateToSettingsViaToken()

        // Wait for loading to complete
        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        // Verify token input is visible and masked
        val tokenInput = page.locator("[data-testid='api-token-input']")
        assertThat(tokenInput).isVisible()
        assertThat(tokenInput).hasValue("••••••••••••••••••••••••••••••••")

        // Verify show button (eye icon) is visible
        val showButton = page.locator("[data-testid='show-api-token-button']")
        assertThat(showButton).isVisible()

        // Verify regenerate button is visible
        val regenerateButton = page.locator("[data-testid='regenerate-api-token-button']")
        assertThat(regenerateButton).isVisible()
    }

    @Test
    fun `should reveal token when clicking show button`() {
        // Create API token for user
        val userId = requireNotNull(regularUser.id)
        val testToken = "test1234567890abcdefghijklmnopqrstuvwxyz12345678"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = userId,
                token = testToken,
            ),
        )

        navigateToSettingsViaToken()

        // Wait for loading to complete
        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        // Click show button
        val showButton = page.locator("[data-testid='show-api-token-button']")
        showButton.click()

        // Verify token is now visible
        val tokenInput = page.locator("[data-testid='api-token-input']")
        assertThat(tokenInput).hasValue(testToken)

        // Verify copy button is now visible instead of show button
        val copyButton = page.locator("[data-testid='copy-api-token-button']")
        assertThat(copyButton).isVisible()
        assertThat(showButton).not().isVisible()
    }

    @Test
    fun `should copy token to clipboard when clicking copy button`() {
        // Create API token for user
        val userId = requireNotNull(regularUser.id)
        val testToken = "test1234567890abcdefghijklmnopqrstuvwxyz12345678"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = userId,
                token = testToken,
            ),
        )

        navigateToSettingsViaToken()

        // Wait for loading to complete
        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        // Click show button to reveal token
        val showButton = page.locator("[data-testid='show-api-token-button']")
        showButton.click()

        // Click copy button
        val copyButton = page.locator("[data-testid='copy-api-token-button']")
        copyButton.click()

        // Verify success message appears
        val successMessage = page.locator("[data-testid='api-token-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("API token copied to clipboard")
    }

    @Test
    fun `should regenerate API token when clicking regenerate button`() {
        // Create API token for user
        val userId = requireNotNull(regularUser.id)
        val oldToken = "old1234567890abcdefghijklmnopqrstuvwxyz1234567"
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = userId,
                token = oldToken,
            ),
        )

        navigateToSettingsViaToken()

        // Wait for loading to complete
        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        // Show the old token first
        val showButton = page.locator("[data-testid='show-api-token-button']")
        showButton.click()

        // Verify old token is visible
        val tokenInput = page.locator("[data-testid='api-token-input']")
        assertThat(tokenInput).hasValue(oldToken)

        // Click regenerate button
        val regenerateButton = page.locator("[data-testid='regenerate-api-token-button']")
        regenerateButton.click()

        // Verify success message appears
        val successMessage = page.locator("[data-testid='api-token-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("API token regenerated successfully")

        // Verify token is now masked again
        assertThat(tokenInput).hasValue("••••••••••••••••••••••••••••••••")

        // Verify show button is visible again (not copy button)
        assertThat(showButton).isVisible()
        val copyButton = page.locator("[data-testid='copy-api-token-button']")
        assertThat(copyButton).not().isVisible()

        // Show the new token to verify it changed
        showButton.click()
        val newTokenValue = tokenInput.inputValue()

        // Verify the token has been regenerated (it should be different from old token)
        assert(newTokenValue != oldToken) {
            "Token should have been regenerated but is still the same: $newTokenValue"
        }

        // Verify the new token has correct length (50 characters)
        assert(newTokenValue.length == 50) {
            "Token should be 50 characters but is ${newTokenValue.length}"
        }
    }

    @Test
    fun `should show different tokens for different users`() {
        // Create another user
        val otherUser = testUsers.createRegularUser("otherApiTokenUser", "Other API Token User")

        // Create API tokens for both users
        val userId1 = requireNotNull(regularUser.id)
        val userId2 = requireNotNull(otherUser.id)
        val token1 = "user1token1234567890abcdefghijklmnopqrstuvwxy"
        val token2 = "user2token1234567890abcdefghijklmnopqrstuvwxy"

        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = userId1,
                token = token1,
            ),
        )

        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = userId2,
                token = token2,
            ),
        )

        // Login as first user and verify token
        navigateToSettingsViaToken()

        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        val showButton = page.locator("[data-testid='show-api-token-button']")
        showButton.click()

        val tokenInput = page.locator("[data-testid='api-token-input']")
        assertThat(tokenInput).hasValue(token1)

        // Login as second user and verify different token
        loginViaToken("/portal/settings", otherUser, testAuthSupport)

        assertThat(apiTokenLoading).not().isVisible()

        val showButton2 = page.locator("[data-testid='show-api-token-button']")
        showButton2.click()

        val tokenInput2 = page.locator("[data-testid='api-token-input']")
        assertThat(tokenInput2).hasValue(token2)
    }
}
