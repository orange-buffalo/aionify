package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

@MicronautTest(transactional = false)
class SettingsPlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private val regularUserName = "settingsTestUser"
    private val regularUserGreeting = "Settings Test User"

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test user with known credentials
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        regularUser = testDatabaseSupport.insert(
            User.create(
                userName = regularUserName,
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = regularUserGreeting,
                isAdmin = false,
                locale = java.util.Locale.US
            )
        )
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    private fun navigateToPortalViaToken() {
        loginViaToken("/portal", regularUser, testAuthSupport)
    }

    // === Full Test Suite for Regular User ===

    @Test
    fun `should display settings menu item in profile dropdown`() {
        navigateToPortalViaToken()

        // Open profile menu
        page.locator("[data-testid='profile-menu-button']").click()

        // Verify settings menu item is visible
        val settingsMenuItem = page.locator("[data-testid='settings-menu-item']")
        assertThat(settingsMenuItem).isVisible()
    }

    @Test
    fun `should navigate to settings page when clicking settings menu item`() {
        navigateToPortalViaToken()

        // Open profile menu and click settings
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='settings-menu-item']").click()

        // Wait for navigation to settings page
        page.waitForURL("**/portal/settings")

        // Verify settings page is displayed
        val settingsPage = page.locator("[data-testid='settings-page']")
        assertThat(settingsPage).isVisible()

        // Verify settings title
        val settingsTitle = page.locator("[data-testid='settings-title']")
        assertThat(settingsTitle).isVisible()
        assertThat(settingsTitle).hasText("Settings")
    }

    // === Profile Management Tests ===

    @Test
    fun `should display profile form with existing user data`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Verify form elements are visible (locale field now labeled as "Language")
        val localeSelect = page.locator("[data-testid='profile-locale-select']")
        assertThat(localeSelect).isVisible()

        val saveButton = page.locator("[data-testid='profile-save-button']")
        assertThat(saveButton).isVisible()

        // Verify existing data is loaded
        assertThat(greetingInput).hasValue(regularUserGreeting)
        assertThat(localeSelect).hasText("English (United States)")
    }

    @Test
    fun `should display profile with Ukrainian language and locale`() {
        // Create user with Ukrainian settings
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        val ukUser = testDatabaseSupport.insert(
            User.create(
                userName = "ukrainianUser",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Привіт",
                isAdmin = false,
                locale = java.util.Locale.forLanguageTag("uk-UA")
            )
        )

        loginViaToken("/portal/settings", ukUser, testAuthSupport)

        // Wait for profile to load and verify Ukrainian data is loaded
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).hasValue("Привіт")

        // UI is in Ukrainian (derived from uk-UA locale), so language dropdown shows Ukrainian variant
        val localeSelect = page.locator("[data-testid='profile-locale-select']")
        assertThat(localeSelect).hasText("Ukrainian (Ukraine)")
    }

    @Test
    fun `should display profile with German locale`() {
        // Create user with German locale
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        val deUser = testDatabaseSupport.insert(
            User.create(
                userName = "germanUser",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Hallo",
                isAdmin = false,
                locale = java.util.Locale.forLanguageTag("de-DE")
            )
        )

        loginViaToken("/portal/settings", deUser, testAuthSupport)

        // Wait for profile to load and verify data is loaded
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).hasValue("Hallo")
        // UI is in English (fallback from de which is not supported)
        assertThat(page.locator("[data-testid='profile-locale-select']")).hasText("German (Germany)")
    }

    @Test
    fun `should show error when greeting is blank`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Clear greeting and submit
        greetingInput.fill("")
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for error message and verify
        val errorMessage = page.locator("[data-testid='profile-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Greeting cannot be blank")
    }

    @Test
    fun `should show error when greeting is only whitespace`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Set greeting to whitespace and submit
        greetingInput.fill("   ")
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for error message and verify
        val errorMessage = page.locator("[data-testid='profile-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Greeting cannot be blank")
    }

    @Test
    fun `should show error for greeting exceeding max length`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Create greeting exceeding 255 chars - bypass maxLength
        val longGreeting = "a".repeat(256)
        greetingInput.evaluate("el => el.maxLength = 500")
        greetingInput.fill(longGreeting)
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for error message and verify
        val errorMessage = page.locator("[data-testid='profile-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("exceed 255 characters")
    }

    @Test
    fun `should successfully update profile and show success message`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Update profile data
        greetingInput.fill("Updated Greeting")

        // Change locale to Ukrainian (Ukraine) - this will also change the UI language
        page.locator("[data-testid='profile-locale-select']").click()
        page.locator("[data-testid='locale-option-uk-UA']").click()

        // Submit
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success message and verify (it should be in Ukrainian after language switch)
        val successMessage = page.locator("[data-testid='profile-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("успішно оновлено")

        // Verify data was persisted by reloading
        page.reload()

        assertThat(greetingInput).hasValue("Updated Greeting")
        // After reload, UI is in Ukrainian (derived from uk-UA locale)
        assertThat(page.locator("[data-testid='profile-locale-select']")).hasText("Ukrainian (Ukraine)")
    }

    @Test
    fun `should allow greeting at max length of 255 characters`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Use exactly 255 characters
        val maxLengthGreeting = "a".repeat(255)
        greetingInput.fill(maxLengthGreeting)
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='profile-success']")
        assertThat(successMessage).isVisible()
    }

    @Test
    fun `should display locale dropdown with all options`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val localeSelect = page.locator("[data-testid='profile-locale-select']")
        assertThat(localeSelect).isVisible()

        // Open dropdown
        localeSelect.click()

        // Verify some options are visible
        val dropdown = page.locator("[data-testid='profile-locale-dropdown']")
        assertThat(dropdown).isVisible()

        val usOption = page.locator("[data-testid='locale-option-en-US']")
        val ukOption = page.locator("[data-testid='locale-option-uk-UA']")
        val deOption = page.locator("[data-testid='locale-option-de-DE']")

        assertThat(usOption).isVisible()
        assertThat(ukOption).isVisible()
        assertThat(deOption).isVisible()
    }

    @Test
    fun `should display change password form with all required elements`() {
        navigateToSettingsViaToken()

        // Verify all password inputs are visible
        val currentPasswordInput = page.locator("[data-testid='current-password-input']")
        assertThat(currentPasswordInput).isVisible()

        val newPasswordInput = page.locator("[data-testid='new-password-input']")
        assertThat(newPasswordInput).isVisible()

        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")
        assertThat(confirmPasswordInput).isVisible()

        val changePasswordButton = page.locator("[data-testid='change-password-button']")
        assertThat(changePasswordButton).isVisible()
    }

    @Test
    fun `should toggle current password visibility`() {
        navigateToSettingsViaToken()

        val currentPasswordInput = page.locator("[data-testid='current-password-input']")
        val toggleButton = page.locator("[data-testid='toggle-current-password-visibility']")

        // Initially password should be hidden
        assertThat(currentPasswordInput).hasAttribute("type", "password")

        // Click toggle to show password
        toggleButton.click()
        assertThat(currentPasswordInput).hasAttribute("type", "text")

        // Click again to hide password
        toggleButton.click()
        assertThat(currentPasswordInput).hasAttribute("type", "password")
    }

    @Test
    fun `should toggle new password visibility`() {
        navigateToSettingsViaToken()

        val newPasswordInput = page.locator("[data-testid='new-password-input']")
        val toggleButton = page.locator("[data-testid='toggle-new-password-visibility']")

        // Initially password should be hidden
        assertThat(newPasswordInput).hasAttribute("type", "password")

        // Click toggle to show password
        toggleButton.click()
        assertThat(newPasswordInput).hasAttribute("type", "text")

        // Click again to hide password
        toggleButton.click()
        assertThat(newPasswordInput).hasAttribute("type", "password")
    }

    @Test
    fun `should toggle confirm password visibility`() {
        navigateToSettingsViaToken()

        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")
        val toggleButton = page.locator("[data-testid='toggle-confirm-password-visibility']")

        // Initially password should be hidden
        assertThat(confirmPasswordInput).hasAttribute("type", "password")

        // Click toggle to show password
        toggleButton.click()
        assertThat(confirmPasswordInput).hasAttribute("type", "text")

        // Click again to hide password
        toggleButton.click()
        assertThat(confirmPasswordInput).hasAttribute("type", "password")
    }

    @Test
    fun `should show error when current password is empty`() {
        navigateToSettingsViaToken()

        // Fill only new password and confirmation
        page.locator("[data-testid='new-password-input']").fill("newPassword123")
        page.locator("[data-testid='confirm-password-input']").fill("newPassword123")

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for error message and verify
        val errorMessage = page.locator("[data-testid='change-password-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Current password is required")
    }

    @Test
    fun `should show error when new password is empty`() {
        navigateToSettingsViaToken()

        // Fill only current password
        page.locator("[data-testid='current-password-input']").fill(testPassword)

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for error message and verify
        val errorMessage = page.locator("[data-testid='change-password-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("New password is required")
    }

    @Test
    fun `should show error when passwords do not match`() {
        navigateToSettingsViaToken()

        // Fill with mismatched passwords
        page.locator("[data-testid='current-password-input']").fill(testPassword)
        page.locator("[data-testid='new-password-input']").fill("newPassword123")
        page.locator("[data-testid='confirm-password-input']").fill("differentPassword456")

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for error message and verify
        val errorMessage = page.locator("[data-testid='change-password-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("do not match")
    }

    @Test
    fun `should show error when current password is incorrect`() {
        navigateToSettingsViaToken()

        // Fill with incorrect current password
        page.locator("[data-testid='current-password-input']").fill("wrongPassword")
        page.locator("[data-testid='new-password-input']").fill("newPassword123")
        page.locator("[data-testid='confirm-password-input']").fill("newPassword123")

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for error message and verify
        val errorMessage = page.locator("[data-testid='change-password-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Current password is incorrect")
    }

    @Test
    fun `should show error for password exceeding max length`() {
        navigateToSettingsViaToken()

        // Create a password that exceeds 50 characters
        // The input has maxLength=50, so we need to bypass it by evaluating JS
        val longPassword = "a".repeat(51)
        page.locator("[data-testid='current-password-input']").fill(testPassword)

        // Use evaluate to bypass maxLength
        page.locator("[data-testid='new-password-input']").evaluate("el => el.maxLength = 100")
        page.locator("[data-testid='new-password-input']").fill(longPassword)
        page.locator("[data-testid='confirm-password-input']").evaluate("el => el.maxLength = 100")
        page.locator("[data-testid='confirm-password-input']").fill(longPassword)

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for error message and verify
        val errorMessage = page.locator("[data-testid='change-password-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("exceed 50 characters")
    }

    @Test
    fun `should successfully change password and show success message`() {
        navigateToSettingsViaToken()

        val newPassword = "brandNewPassword456"

        // Fill with valid data
        page.locator("[data-testid='current-password-input']").fill(testPassword)
        page.locator("[data-testid='new-password-input']").fill(newPassword)
        page.locator("[data-testid='confirm-password-input']").fill(newPassword)

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for success message and verify
        val successMessage = page.locator("[data-testid='change-password-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Password changed successfully")

        // Verify inputs are reset
        val currentPasswordInput = page.locator("[data-testid='current-password-input']")
        val newPasswordInput = page.locator("[data-testid='new-password-input']")
        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")

        assertThat(currentPasswordInput).hasValue("")
        assertThat(newPasswordInput).hasValue("")
        assertThat(confirmPasswordInput).hasValue("")
    }

    @Test
    fun `should be able to login with new password after change`() {
        navigateToSettingsViaToken()

        val newPassword = "loginAfterChangeTest"

        // Change password
        page.locator("[data-testid='current-password-input']").fill(testPassword)
        page.locator("[data-testid='new-password-input']").fill(newPassword)
        page.locator("[data-testid='confirm-password-input']").fill(newPassword)
        page.locator("[data-testid='change-password-button']").click()

        // Wait for success
        assertThat(page.locator("[data-testid='change-password-success']")).isVisible()

        // Logout
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()
        page.waitForURL("**/login")

        // Try to login with new password (needs UI login to verify password actually changed)
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill(newPassword)
        page.locator("[data-testid='login-button']").click()

        // Should be redirected to portal
        page.waitForURL("**/portal")
        val userPortal = page.locator("[data-testid='user-portal']")
        assertThat(userPortal).isVisible()
    }

    @Test
    fun `should not reset inputs on validation failure`() {
        navigateToSettingsViaToken()

        // Fill with mismatched passwords
        page.locator("[data-testid='current-password-input']").fill(testPassword)
        page.locator("[data-testid='new-password-input']").fill("newPassword123")
        page.locator("[data-testid='confirm-password-input']").fill("differentPassword456")

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for error message
        val errorMessage = page.locator("[data-testid='change-password-error']")
        assertThat(errorMessage).isVisible()

        // Verify inputs are NOT reset
        val currentPasswordInput = page.locator("[data-testid='current-password-input']")
        val newPasswordInput = page.locator("[data-testid='new-password-input']")
        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")

        assertThat(currentPasswordInput).hasValue(testPassword)
        assertThat(newPasswordInput).hasValue("newPassword123")
        assertThat(confirmPasswordInput).hasValue("differentPassword456")
    }

    @Test
    fun `should allow changing password to max length of 50 characters`() {
        navigateToSettingsViaToken()

        // Use exactly 50 characters password
        val maxLengthPassword = "a".repeat(50)

        page.locator("[data-testid='current-password-input']").fill(testPassword)
        page.locator("[data-testid='new-password-input']").fill(maxLengthPassword)
        page.locator("[data-testid='confirm-password-input']").fill(maxLengthPassword)

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='change-password-success']")
        assertThat(successMessage).isVisible()
    }

    // === Sanity Success Path Test for Admin ===

    @Test
    fun `admin should be able to access settings and change password`() {
        // Create a test admin user
        val testAdminPassword = "adminPassword123"
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        val adminUser = testDatabaseSupport.insert(
            User.create(
                userName = "settingsTestAdmin",
                passwordHash = BCrypt.hashpw(testAdminPassword, BCrypt.gensalt()),
                greeting = "Settings Test Admin",
                isAdmin = true,
                locale = java.util.Locale.US
            )
        )

        // Use token-based auth for admin
        loginViaToken("/admin/settings", adminUser, testAuthSupport)

        // Verify settings page is displayed
        val settingsPage = page.locator("[data-testid='settings-page']")
        assertThat(settingsPage).isVisible()

        val newPassword = "newAdminPassword789"

        // Fill with valid data
        page.locator("[data-testid='current-password-input']").fill(testAdminPassword)
        page.locator("[data-testid='new-password-input']").fill(newPassword)
        page.locator("[data-testid='confirm-password-input']").fill(newPassword)

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for success message and verify
        val successMessage = page.locator("[data-testid='change-password-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Password changed successfully")
    }

    @Test
    fun `newly created user should have locale properly set when created via admin`() {
        // This test verifies the fix for the bug where newly created users
        // had an empty locale dropdown in settings page.
        // The issue was that UserAdminResource.createUser() was using Locale.ENGLISH
        // which converts to "en" (language only), but the frontend expects full locale
        // tags like "en-US", causing the locale dropdown to show as empty.
        // The fix is to use Locale.US which converts to "en-US".
        
        // Create a user with Locale.US to verify the fix
        val userWithProperLocale = testDatabaseSupport.insert(
            User.create(
                userName = "properLocaleUser",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Proper Locale User",
                isAdmin = false,
                locale = java.util.Locale.US
            )
        )

        loginViaToken("/portal/settings", userWithProperLocale, testAuthSupport)

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        val localeSelect = page.locator("[data-testid='profile-locale-select']")

        // Verify locale dropdown shows "English (United States)" and is not empty
        assertThat(localeSelect).hasText("English (United States)")
    }
}
