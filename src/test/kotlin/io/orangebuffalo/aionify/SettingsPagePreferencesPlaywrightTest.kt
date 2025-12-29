package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserSettings
import io.orangebuffalo.aionify.domain.UserSettingsRepository
import io.orangebuffalo.aionify.domain.WeekDay
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class SettingsPagePreferencesPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var userSettingsRepository: UserSettingsRepository

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test user
        regularUser = testUsers.createRegularUser("settingsTestUser", "Settings Test User")

        // Create user settings (normally done by UserAdminResource but not by test helpers)
        testDatabaseSupport.insert(UserSettings.create(userId = requireNotNull(regularUser.id)))
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
    fun `should allow changing and persisting start of week preference`() {
        navigateToSettingsViaToken()

        // Verify preferences are loaded with Monday as default
        val startOfWeekSelect = page.locator("[data-testid='start-of-week-select']")
        assertThat(startOfWeekSelect).isVisible()
        assertThat(startOfWeekSelect).containsText("Monday")

        // Verify initial database state
        testDatabaseSupport.inTransaction {
            val initialSettings = userSettingsRepository.findByUserId(requireNotNull(regularUser.id)).orElseThrow()
            assertEquals(WeekDay.MONDAY, initialSettings.startOfWeek)
        }

        // Change to Sunday
        startOfWeekSelect.click()
        page.locator("[data-testid='start-of-week-option-sunday']").click()

        // Save preferences
        page.locator("[data-testid='save-preferences-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='preferences-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Preferences updated successfully")

        // Verify database was updated
        testDatabaseSupport.inTransaction {
            val updatedSettings = userSettingsRepository.findByUserId(requireNotNull(regularUser.id)).orElseThrow()
            assertEquals(WeekDay.SUNDAY, updatedSettings.startOfWeek)
        }

        // Reload the settings page to verify persistence
        page.navigate("$baseUrl/portal/settings")

        // Wait for page to load
        assertThat(page.locator("[data-testid='settings-page']")).isVisible()

        // Verify the saved preference is still loaded in UI
        val reloadedSelect = page.locator("[data-testid='start-of-week-select']")
        assertThat(reloadedSelect).isVisible()
        assertThat(reloadedSelect).containsText("Sunday")

        // Verify database state is still correct
        testDatabaseSupport.inTransaction {
            val persistedSettings = userSettingsRepository.findByUserId(requireNotNull(regularUser.id)).orElseThrow()
            assertEquals(WeekDay.SUNDAY, persistedSettings.startOfWeek)
        }
    }
}
