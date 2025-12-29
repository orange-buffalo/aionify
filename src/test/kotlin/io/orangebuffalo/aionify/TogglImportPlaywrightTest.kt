package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserSettings
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class TogglImportPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser("importTestUser", "Import Test User")
        testDatabaseSupport.insert(UserSettings.create(userId = requireNotNull(regularUser.id)))
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    @Test
    fun `should display import data card on settings page`() {
        navigateToSettingsViaToken()

        // Verify Import Data title is present
        assertThat(page.locator("[data-testid='import-title']")).isVisible()
        assertThat(page.locator("[data-testid='import-title']")).containsText("Import Data")
    }

    @Test
    fun `should show instructions and file input for Toggl Timer source`() {
        navigateToSettingsViaToken()

        // Select Toggl Timer source
        val sourceSelect = page.locator("[data-testid='import-source-select']")
        sourceSelect.click()
        page.locator("[data-testid='import-source-toggl']").click()

        // Verify instructions are shown as a list
        assertThat(page.locator("[data-testid='toggl-instructions']")).isVisible()
        assertThat(page.locator("[data-testid='toggl-instructions'] li")).hasCount(5)

        // Verify file input is shown
        assertThat(page.locator("[data-testid='file-input-label']")).isVisible()

        // Verify import button is shown but disabled (no file selected)
        val importButton = page.locator("[data-testid='start-import-button']")
        assertThat(importButton).isVisible()
        assertThat(importButton).isDisabled()
    }
}
