package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class SettingsPagePreferencesPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test user
        regularUser = testUsers.createRegularUser("settingsTestUser", "Settings Test User")
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    @Test
    fun `should display preferences panel on settings page`() {
        navigateToSettingsViaToken()

        // Verify settings page is visible
        val settingsPage = page.locator("[data-testid='settings-page']")
        assertThat(settingsPage).isVisible()

        // Verify Preferences title is present
        assertThat(page.locator("[data-testid='preferences-title']")).isVisible()
    }

    @Test
    fun `should allow changing start of week`() {
        navigateToSettingsViaToken()

        // Verify preferences are loaded with Monday as default
        val startOfWeekSelect = page.locator("[data-testid='start-of-week-select']")
        assertThat(startOfWeekSelect).isVisible()
        assertThat(startOfWeekSelect).containsText("Monday")

        // Change to Sunday
        startOfWeekSelect.click()
        page.locator("[data-testid='start-of-week-option-sunday']").click()

        // Save preferences
        page.locator("[data-testid='save-preferences-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='preferences-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Preferences updated successfully")
    }

    @Test
    fun `should persist start of week preference`() {
        navigateToSettingsViaToken()

        // Change to Saturday
        val startOfWeekSelect = page.locator("[data-testid='start-of-week-select']")
        startOfWeekSelect.click()
        page.locator("[data-testid='start-of-week-option-saturday']").click()

        // Save preferences - this will cause a page reload after 1 second
        page.locator("[data-testid='save-preferences-button']").click()

        // Wait for success message before reload
        val successMessage = page.locator("[data-testid='preferences-success']")
        assertThat(successMessage).isVisible()

        // Navigate directly to settings page after save to verify persistence
        // This avoids the automatic reload that happens
        page.navigate("$baseUrl/portal/settings")

        // Wait for page to load
        assertThat(page.locator("[data-testid='settings-page']")).isVisible()

        // Verify the saved preference is loaded
        val reloadedSelect = page.locator("[data-testid='start-of-week-select']")
        assertThat(reloadedSelect).isVisible()
        assertThat(reloadedSelect).containsText("Saturday")
    }
}
