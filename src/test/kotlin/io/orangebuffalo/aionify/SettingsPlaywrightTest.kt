package io.orangebuffalo.aionify

import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import io.quarkus.elytron.security.common.BcryptUtil
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL
import java.util.Locale

@QuarkusTest
class SettingsPlaywrightTest : PlaywrightTestBase() {

    @TestHTTPResource("/")
    lateinit var baseUrl: URL

    @TestHTTPResource("/login")
    lateinit var loginUrl: URL

    @TestHTTPResource("/portal")
    lateinit var portalUrl: URL

    @TestHTTPResource("/portal/settings")
    lateinit var userSettingsUrl: URL

    @TestHTTPResource("/admin/settings")
    lateinit var adminSettingsUrl: URL

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private val regularUserName = "settingsTestUser"
    private val regularUserGreeting = "Settings Test User"

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test user with known credentials
        regularUser = userRepository.insert(
            User(
                userName = regularUserName,
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = regularUserGreeting,
                isAdmin = false,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken(baseUrl, userSettingsUrl, regularUser, testAuthSupport)
    }

    private fun navigateToPortalViaToken() {
        loginViaToken(baseUrl, portalUrl, regularUser, testAuthSupport)
    }

    // === Full Test Suite for Regular User ===

    @Test
    fun `should display settings menu item in profile dropdown`() {
        navigateToPortalViaToken()

        // Open profile menu
        page.locator("[data-testid='profile-menu-button']").click()

        // Verify settings menu item is visible
        val settingsMenuItem = page.locator("[data-testid='settings-menu-item']")
        settingsMenuItem.waitFor()
        assertTrue(settingsMenuItem.isVisible, "Settings menu item should be visible")
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
        settingsPage.waitFor()
        assertTrue(settingsPage.isVisible, "Settings page should be visible")

        // Verify settings title
        val settingsTitle = page.locator("[data-testid='settings-title']")
        assertTrue(settingsTitle.isVisible, "Settings title should be visible")
        assertEquals("Settings", settingsTitle.textContent())
    }

    // === Profile Management Tests ===

    @Test
    fun `should display profile form with existing user data`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        greetingInput.waitFor()

        // Verify form elements are visible
        assertTrue(greetingInput.isVisible, "Greeting input should be visible")
        
        val languageSelect = page.locator("[data-testid='profile-language-select']")
        assertTrue(languageSelect.isVisible, "Language select should be visible")
        
        val localeSelect = page.locator("[data-testid='profile-locale-select']")
        assertTrue(localeSelect.isVisible, "Locale select should be visible")
        
        val saveButton = page.locator("[data-testid='profile-save-button']")
        assertTrue(saveButton.isVisible, "Save button should be visible")

        // Verify existing data is loaded
        assertEquals(regularUserGreeting, greetingInput.inputValue(), "Greeting should be pre-filled")
        assertTrue(languageSelect.textContent()?.contains("English") == true, "Language should show English")
    }

    @Test
    fun `should display profile with Ukrainian language and locale`() {
        // Create user with Ukrainian settings
        val ukUser = userRepository.insert(
            User(
                userName = "ukrainianUser",
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = "Привіт",
                isAdmin = false,
                locale = Locale.forLanguageTag("uk-UA"),
                languageCode = "uk"
            )
        )

        loginViaToken(baseUrl, userSettingsUrl, ukUser, testAuthSupport)

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        greetingInput.waitFor()

        // Verify Ukrainian data is loaded
        assertEquals("Привіт", greetingInput.inputValue(), "Greeting should show Ukrainian greeting")
        
        val languageSelect = page.locator("[data-testid='profile-language-select']")
        assertTrue(languageSelect.textContent()?.contains("Ukrainian") == true, "Language should show Ukrainian")
        
        val localeSelect = page.locator("[data-testid='profile-locale-select']")
        assertEquals("Ukrainian (Ukraine)", localeSelect.textContent(), "Locale should show Ukrainian (Ukraine)")
    }

    @Test
    fun `should display profile with German locale`() {
        // Create user with German locale
        val deUser = userRepository.insert(
            User(
                userName = "germanUser",
                passwordHash = BcryptUtil.bcryptHash(testPassword),
                greeting = "Hallo",
                isAdmin = false,
                locale = Locale.forLanguageTag("de-DE"),
                languageCode = "en"
            )
        )

        loginViaToken(baseUrl, userSettingsUrl, deUser, testAuthSupport)

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        greetingInput.waitFor()

        // Verify data is loaded
        assertEquals("Hallo", greetingInput.inputValue(), "Greeting should show German greeting")
        assertTrue(page.locator("[data-testid='profile-language-select']").textContent()?.contains("English") == true, "Language should be English")
        assertEquals("German (Germany)", page.locator("[data-testid='profile-locale-select']").textContent(), "Locale should show German (Germany)")
    }

    @Test
    fun `should show error when greeting is blank`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        greetingInput.waitFor()

        // Clear greeting and submit
        greetingInput.fill("")
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for error message
        val errorMessage = page.locator("[data-testid='profile-error']")
        errorMessage.waitFor()
        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("Greeting cannot be blank") == true,
            "Error should indicate greeting cannot be blank"
        )
    }

    @Test
    fun `should show error when greeting is only whitespace`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        greetingInput.waitFor()

        // Set greeting to whitespace and submit
        greetingInput.fill("   ")
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for error message
        val errorMessage = page.locator("[data-testid='profile-error']")
        errorMessage.waitFor()
        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("Greeting cannot be blank") == true,
            "Error should indicate greeting cannot be blank"
        )
    }

    @Test
    fun `should show error for greeting exceeding max length`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        greetingInput.waitFor()

        // Create greeting exceeding 255 chars - bypass maxLength
        val longGreeting = "a".repeat(256)
        greetingInput.evaluate("el => el.maxLength = 500")
        greetingInput.fill(longGreeting)
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for error message
        val errorMessage = page.locator("[data-testid='profile-error']")
        errorMessage.waitFor()
        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("exceed 255 characters") == true,
            "Error should indicate greeting exceeds max length"
        )
    }

    @Test
    fun `should successfully update profile and show success message`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        greetingInput.waitFor()

        // Update profile data
        greetingInput.fill("Updated Greeting")
        
        // Change language to Ukrainian
        page.locator("[data-testid='profile-language-select']").click()
        page.locator("[data-testid='language-option-uk']").click()
        
        // Change locale to Ukrainian (Ukraine)
        page.locator("[data-testid='profile-locale-select']").click()
        page.locator("[data-testid='locale-option-uk-UA']").click()

        // Submit
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='profile-success']")
        successMessage.waitFor()
        assertTrue(successMessage.isVisible, "Success message should be visible")
        assertTrue(
            successMessage.textContent()?.contains("Profile updated successfully") == true,
            "Success message should indicate profile was updated"
        )

        // Verify data was persisted by reloading
        page.reload()
        
        greetingInput.waitFor()
        assertEquals("Updated Greeting", greetingInput.inputValue(), "Updated greeting should be persisted")
        assertTrue(page.locator("[data-testid='profile-language-select']").textContent()?.contains("Ukrainian") == true, "Ukrainian language should be persisted")
        assertEquals("Ukrainian (Ukraine)", page.locator("[data-testid='profile-locale-select']").textContent(), "Ukrainian locale should be persisted")
    }

    @Test
    fun `should allow greeting at max length of 255 characters`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        greetingInput.waitFor()

        // Use exactly 255 characters
        val maxLengthGreeting = "a".repeat(255)
        greetingInput.fill(maxLengthGreeting)
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='profile-success']")
        successMessage.waitFor()
        assertTrue(successMessage.isVisible, "Success message should be visible for max length greeting")
    }

    @Test
    fun `should display language dropdown with all options`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val languageSelect = page.locator("[data-testid='profile-language-select']")
        languageSelect.waitFor()

        // Open dropdown
        languageSelect.click()

        // Verify options
        val dropdown = page.locator("[data-testid='profile-language-dropdown']")
        dropdown.waitFor()
        
        val englishOption = page.locator("[data-testid='language-option-en']")
        val ukrainianOption = page.locator("[data-testid='language-option-uk']")
        
        assertTrue(englishOption.isVisible, "English option should be visible")
        assertTrue(ukrainianOption.isVisible, "Ukrainian option should be visible")
    }

    @Test
    fun `should display locale dropdown with all options`() {
        navigateToSettingsViaToken()

        // Wait for profile to load
        val localeSelect = page.locator("[data-testid='profile-locale-select']")
        localeSelect.waitFor()

        // Open dropdown
        localeSelect.click()

        // Verify some options are visible
        val dropdown = page.locator("[data-testid='profile-locale-dropdown']")
        dropdown.waitFor()
        
        val usOption = page.locator("[data-testid='locale-option-en-US']")
        val ukOption = page.locator("[data-testid='locale-option-uk-UA']")
        val deOption = page.locator("[data-testid='locale-option-de-DE']")
        
        assertTrue(usOption.isVisible, "English (US) option should be visible")
        assertTrue(ukOption.isVisible, "Ukrainian (Ukraine) option should be visible")
        assertTrue(deOption.isVisible, "German (Germany) option should be visible")
    }

    @Test
    fun `should display change password form with all required elements`() {
        navigateToSettingsViaToken()

        // Verify all password inputs are visible
        val currentPasswordInput = page.locator("[data-testid='current-password-input']")
        assertTrue(currentPasswordInput.isVisible, "Current password input should be visible")

        val newPasswordInput = page.locator("[data-testid='new-password-input']")
        assertTrue(newPasswordInput.isVisible, "New password input should be visible")

        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")
        assertTrue(confirmPasswordInput.isVisible, "Confirm password input should be visible")

        val changePasswordButton = page.locator("[data-testid='change-password-button']")
        assertTrue(changePasswordButton.isVisible, "Change password button should be visible")
    }

    @Test
    fun `should toggle current password visibility`() {
        navigateToSettingsViaToken()

        val currentPasswordInput = page.locator("[data-testid='current-password-input']")
        val toggleButton = page.locator("[data-testid='toggle-current-password-visibility']")

        // Initially password should be hidden
        assertEquals("password", currentPasswordInput.getAttribute("type"))

        // Click toggle to show password
        toggleButton.click()
        assertEquals("text", currentPasswordInput.getAttribute("type"))

        // Click again to hide password
        toggleButton.click()
        assertEquals("password", currentPasswordInput.getAttribute("type"))
    }

    @Test
    fun `should toggle new password visibility`() {
        navigateToSettingsViaToken()

        val newPasswordInput = page.locator("[data-testid='new-password-input']")
        val toggleButton = page.locator("[data-testid='toggle-new-password-visibility']")

        // Initially password should be hidden
        assertEquals("password", newPasswordInput.getAttribute("type"))

        // Click toggle to show password
        toggleButton.click()
        assertEquals("text", newPasswordInput.getAttribute("type"))

        // Click again to hide password
        toggleButton.click()
        assertEquals("password", newPasswordInput.getAttribute("type"))
    }

    @Test
    fun `should toggle confirm password visibility`() {
        navigateToSettingsViaToken()

        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")
        val toggleButton = page.locator("[data-testid='toggle-confirm-password-visibility']")

        // Initially password should be hidden
        assertEquals("password", confirmPasswordInput.getAttribute("type"))

        // Click toggle to show password
        toggleButton.click()
        assertEquals("text", confirmPasswordInput.getAttribute("type"))

        // Click again to hide password
        toggleButton.click()
        assertEquals("password", confirmPasswordInput.getAttribute("type"))
    }

    @Test
    fun `should show error when current password is empty`() {
        navigateToSettingsViaToken()

        // Fill only new password and confirmation
        page.locator("[data-testid='new-password-input']").fill("newPassword123")
        page.locator("[data-testid='confirm-password-input']").fill("newPassword123")

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for error message
        val errorMessage = page.locator("[data-testid='change-password-error']")
        errorMessage.waitFor()
        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("Current password is required") == true,
            "Error should indicate current password is required"
        )
    }

    @Test
    fun `should show error when new password is empty`() {
        navigateToSettingsViaToken()

        // Fill only current password
        page.locator("[data-testid='current-password-input']").fill(testPassword)

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for error message
        val errorMessage = page.locator("[data-testid='change-password-error']")
        errorMessage.waitFor()
        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("New password is required") == true,
            "Error should indicate new password is required"
        )
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

        // Wait for error message
        val errorMessage = page.locator("[data-testid='change-password-error']")
        errorMessage.waitFor()
        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("do not match") == true,
            "Error should indicate passwords do not match"
        )
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

        // Wait for error message
        val errorMessage = page.locator("[data-testid='change-password-error']")
        errorMessage.waitFor()
        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("Current password is incorrect") == true,
            "Error should indicate current password is incorrect"
        )
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

        // Wait for error message
        val errorMessage = page.locator("[data-testid='change-password-error']")
        errorMessage.waitFor()
        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("exceed 50 characters") == true,
            "Error should indicate password exceeds max length"
        )
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

        // Wait for success message
        val successMessage = page.locator("[data-testid='change-password-success']")
        successMessage.waitFor()
        assertTrue(successMessage.isVisible, "Success message should be visible")
        assertTrue(
            successMessage.textContent()?.contains("Password changed successfully") == true,
            "Success message should indicate password was changed"
        )

        // Verify inputs are reset
        val currentPasswordInput = page.locator("[data-testid='current-password-input']")
        val newPasswordInput = page.locator("[data-testid='new-password-input']")
        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")

        assertEquals("", currentPasswordInput.inputValue(), "Current password input should be reset")
        assertEquals("", newPasswordInput.inputValue(), "New password input should be reset")
        assertEquals("", confirmPasswordInput.inputValue(), "Confirm password input should be reset")
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
        page.locator("[data-testid='change-password-success']").waitFor()

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
        userPortal.waitFor()
        assertTrue(userPortal.isVisible, "User portal should be visible after login with new password")
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
        errorMessage.waitFor()

        // Verify inputs are NOT reset
        val currentPasswordInput = page.locator("[data-testid='current-password-input']")
        val newPasswordInput = page.locator("[data-testid='new-password-input']")
        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")

        assertEquals(testPassword, currentPasswordInput.inputValue(), "Current password input should not be reset on error")
        assertEquals("newPassword123", newPasswordInput.inputValue(), "New password input should not be reset on error")
        assertEquals("differentPassword456", confirmPasswordInput.inputValue(), "Confirm password input should not be reset on error")
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
        successMessage.waitFor()
        assertTrue(successMessage.isVisible, "Success message should be visible for max length password")
    }

    // === Sanity Success Path Test for Admin ===

    @Test
    fun `admin should be able to access settings and change password`() {
        // Create a test admin user
        val testAdminPassword = "adminPassword123"
        val adminUser = userRepository.insert(
            User(
                userName = "settingsTestAdmin",
                passwordHash = BcryptUtil.bcryptHash(testAdminPassword),
                greeting = "Settings Test Admin",
                isAdmin = true,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )

        // Use token-based auth for admin
        loginViaToken(baseUrl, adminSettingsUrl, adminUser, testAuthSupport)

        // Verify settings page is displayed
        val settingsPage = page.locator("[data-testid='settings-page']")
        settingsPage.waitFor()
        assertTrue(settingsPage.isVisible, "Settings page should be visible for admin")

        val newPassword = "newAdminPassword789"

        // Fill with valid data
        page.locator("[data-testid='current-password-input']").fill(testAdminPassword)
        page.locator("[data-testid='new-password-input']").fill(newPassword)
        page.locator("[data-testid='confirm-password-input']").fill(newPassword)

        // Click change password
        page.locator("[data-testid='change-password-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='change-password-success']")
        successMessage.waitFor()
        assertTrue(successMessage.isVisible, "Admin should see success message after changing password")
        assertTrue(
            successMessage.textContent()?.contains("Password changed successfully") == true,
            "Success message should indicate password was changed"
        )
    }
}
