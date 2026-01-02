package io.orangebuffalo.aionify

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessTokenRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

@MicronautTest(transactional = false)
class ApiTokenScreenshotsTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var userApiAccessTokenRepository: UserApiAccessTokenRepository

    private lateinit var regularUser: User

    companion object {
        private val screenshotsDir = Paths.get("/home/runner/work/aionify/aionify/docs/images")

        @JvmStatic
        @AfterAll
        fun cleanup() {
            // Note: This test is temporary and should be deleted after screenshots are captured
            println("Screenshots captured to: $screenshotsDir")
            println("Please review and commit the screenshots, then delete this test file.")
        }
    }

    @BeforeEach
    fun setupTestData() {
        // Ensure screenshots directory exists
        Files.createDirectories(screenshotsDir)
        
        // Create test user
        regularUser = testUsers.createRegularUser("screenshotUser", "Screenshot Demo User")
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    @Test
    fun `capture screenshot of generate API token state`() {
        navigateToSettingsViaToken()

        // Wait for loading to complete
        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        // Verify generate button is visible
        val generateButton = page.locator("[data-testid='generate-api-token-button']")
        assertThat(generateButton).isVisible()

        // Wait a moment for any animations to complete
        page.waitForTimeout(500)

        // Find the API Access Token card and take a screenshot
        val apiTokenCard = page.locator("[data-testid='api-token-title']").locator("..")
        val screenshotPath = screenshotsDir.resolve("api-token-generation.png")
        
        // Take screenshot of the entire card
        apiTokenCard.screenshot(Page.ScreenshotOptions().setPath(screenshotPath))
        
        println("Captured: $screenshotPath")
    }

    @Test
    fun `capture screenshot of API token display state`() {
        navigateToSettingsViaToken()

        // Wait for loading to complete
        val apiTokenLoading = page.locator("[data-testid='api-token-loading']")
        assertThat(apiTokenLoading).not().isVisible()

        // Generate token
        val generateButton = page.locator("[data-testid='generate-api-token-button']")
        generateButton.click()

        // Wait for token to be generated
        val tokenInput = page.locator("[data-testid='api-token-input']")
        assertThat(tokenInput).isVisible()

        // Click show button to reveal token
        val showButton = page.locator("[data-testid='show-api-token-button']")
        showButton.click()

        // Wait for token to be revealed (not masked)
        assertThat(tokenInput).not().hasValue("••••••••••••••••••••••••••••••••")

        // Wait a moment for any animations to complete
        page.waitForTimeout(500)

        // Find the API Access Token card and take a screenshot
        val apiTokenCard = page.locator("[data-testid='api-token-title']").locator("..")
        val screenshotPath = screenshotsDir.resolve("api-token-display.png")
        
        // Take screenshot of the entire card
        apiTokenCard.screenshot(Page.ScreenshotOptions().setPath(screenshotPath))
        
        println("Captured: $screenshotPath")
    }
}
