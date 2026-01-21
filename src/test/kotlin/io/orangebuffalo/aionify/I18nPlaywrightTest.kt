package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

@MicronautTest(transactional = false)
class I18nPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private val regularUserName = "i18nTestUser"
    private val regularUserGreeting = "I18n Test User"

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test user with English language
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        regularUser =
            testDatabaseSupport.insert(
                User.create(
                    userName = regularUserName,
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = regularUserGreeting,
                    isAdmin = false,
                    locale = java.util.Locale.US,
                ),
            )
    }

    @Test
    fun `should load login page in English when no language preference is saved`() {
        page.navigate("/login")

        // Clear local storage after page loads
        page.evaluate("localStorage.clear()")
        page.reload()

        // Verify login page is in English
        val loginTitle = page.locator("[data-testid='login-title']")
        assertThat(loginTitle).hasText("Login")

        val usernameLabel = page.locator("label[for='userName']")
        assertThat(usernameLabel).hasText("Username")

        val passwordLabel = page.locator("label[for='password']")
        assertThat(passwordLabel).hasText("Password")
    }

    @Test
    fun `should load login page in Ukrainian when language preference is saved`() {
        page.navigate("/login")

        // Set Ukrainian language in local storage after page loads
        page.evaluate("localStorage.setItem('aionify_language', 'uk')")
        page.reload()

        // Verify login page is in Ukrainian
        val loginTitle = page.locator("[data-testid='login-title']")
        assertThat(loginTitle).hasText("Вхід")

        val usernameLabel = page.locator("label[for='userName']")
        assertThat(usernameLabel).hasText("Ім'я користувача")

        val passwordLabel = page.locator("label[for='password']")
        assertThat(passwordLabel).hasText("Пароль")
    }

    @Test
    fun `should switch UI to user's preferred language upon login`() {
        // Create a user with Ukrainian language preference
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        val ukUser =
            testDatabaseSupport.insert(
                User.create(
                    userName = "ukrainianTestUser",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Тестовий користувач",
                    isAdmin = false,
                    locale = java.util.Locale.forLanguageTag("uk-UA"),
                ),
            )

        page.navigate("/login")
        page.evaluate("localStorage.clear()")
        page.reload()

        // Login with Ukrainian user
        page.locator("[data-testid='username-input']").fill("ukrainianTestUser")
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to time logs page
        page.waitForURL("**/portal/time-logs")

        // Verify UI switched to Ukrainian - check a visible element with translation
        val currentEntryPanelTitle = page.locator("text=Поточний запис")
        assertThat(currentEntryPanelTitle).isVisible()

        // Verify language was saved to local storage
        val savedLanguage = page.evaluate("localStorage.getItem('aionify_language')")
        assert(savedLanguage == "uk") { "Expected language to be 'uk' but was '$savedLanguage'" }

        // Logout to verify login page is in Ukrainian
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()

        // Wait for redirect to login
        page.waitForURL("**/login")

        // Verify login page is in Ukrainian
        val loginTitle = page.locator("[data-testid='login-title']")
        assertThat(loginTitle).hasText("Вхід")
    }

    @Test
    fun `should keep UI in English after login when user prefers English`() {
        page.navigate("/login")
        page.evaluate("localStorage.clear()")
        page.reload()

        // Login with English user
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to time logs page
        page.waitForURL("**/portal/time-logs")

        // Verify UI is in English - check a visible element with translation
        val currentEntryPanelTitle = page.locator("text=Current Entry")
        assertThat(currentEntryPanelTitle).isVisible()

        // Verify language was saved to local storage
        val savedLanguage = page.evaluate("localStorage.getItem('aionify_language')")
        assert(savedLanguage == "en") { "Expected language to be 'en' but was '$savedLanguage'" }
    }

    @Test
    fun `should update UI immediately when changing language in settings`() {
        loginViaToken("/portal/profile", regularUser, testAuthSupport)

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Verify page is initially in English - check card title
        val profileCardTitle = page.locator("text=My Profile")
        assertThat(profileCardTitle).isVisible()

        // Change locale to Ukrainian (which also changes UI language)
        page.locator("[data-testid='profile-locale-select']").click()
        page.locator("[data-testid='locale-option-uk-UA']").click()

        // Submit
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='profile-success']")
        assertThat(successMessage).isVisible()

        // Verify UI switched to Ukrainian immediately - check card title
        val profileCardTitleUk = page.locator("text=Мій профіль")
        assertThat(profileCardTitleUk).isVisible()
        assertThat(successMessage).containsText("успішно оновлено")

        // Verify the locale dropdown now shows Ukrainian locale in Ukrainian
        val localeSelect = page.locator("[data-testid='profile-locale-select']")
        assertThat(localeSelect).hasText("Українська (Україна)")
    }

    @Test
    fun `should display translated validation errors for profile`() {
        loginViaToken("/portal/profile", regularUser, testAuthSupport)

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Clear greeting (in English)
        greetingInput.fill("")
        page.locator("[data-testid='profile-save-button']").click()

        // Verify error message is in English
        val errorMessage = page.locator("[data-testid='profile-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Greeting cannot be blank")

        // Now switch to Ukrainian first
        greetingInput.fill("Test")
        page.locator("[data-testid='profile-locale-select']").click()
        page.locator("[data-testid='locale-option-uk-UA']").click()
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success
        val successMessage = page.locator("[data-testid='profile-success']")
        assertThat(successMessage).isVisible()

        // Now trigger a validation error in Ukrainian
        greetingInput.fill("")
        page.locator("[data-testid='profile-save-button']").click()

        // Verify error message is in Ukrainian
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("не може бути порожнім")
    }

    @Test
    fun `should display translated validation errors for password change`() {
        loginViaToken("/portal/profile", regularUser, testAuthSupport)

        // Try to change password with mismatched passwords (in English)
        page.locator("[data-testid='current-password-input']").fill(testPassword)
        page.locator("[data-testid='new-password-input']").fill("newPassword123")
        page.locator("[data-testid='confirm-password-input']").fill("differentPassword")
        page.locator("[data-testid='change-password-button']").click()

        // Verify error message is in English
        val errorMessage = page.locator("[data-testid='change-password-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("do not match")

        // Clear the form
        page.reload()
        page.locator("[data-testid='profile-greeting-input']").waitFor()

        // Switch to Ukrainian
        page.locator("[data-testid='profile-locale-select']").click()
        page.locator("[data-testid='locale-option-uk-UA']").click()
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success
        val successMessage = page.locator("[data-testid='profile-success']")
        assertThat(successMessage).isVisible()

        // Now try password change with mismatched passwords in Ukrainian
        page.locator("[data-testid='current-password-input']").fill(testPassword)
        page.locator("[data-testid='new-password-input']").fill("newPassword123")
        page.locator("[data-testid='confirm-password-input']").fill("differentPassword")
        page.locator("[data-testid='change-password-button']").click()

        // Verify error message is in Ukrainian
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("не співпадають")
    }

    @Test
    fun `should display translated API error for invalid credentials`() {
        page.navigate("/login")
        page.evaluate("localStorage.clear()")
        page.reload()

        // Try to login with invalid credentials (in English)
        page.locator("[data-testid='username-input']").fill("wronguser")
        page.locator("[data-testid='password-input']").fill("wrongpassword")
        page.locator("[data-testid='login-button']").click()

        // Verify error message is in English
        val errorMessage = page.locator("[data-testid='login-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Invalid username or password")

        // Switch to Ukrainian
        page.evaluate("localStorage.setItem('aionify_language', 'uk')")
        page.reload()

        // Try again with invalid credentials (should be in Ukrainian)
        page.locator("[data-testid='username-input']").fill("wronguser")
        page.locator("[data-testid='password-input']").fill("wrongpassword")
        page.locator("[data-testid='login-button']").click()

        // Verify error message is in Ukrainian
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Невірне ім'я користувача або пароль")
    }

    @Test
    fun `should persist language preference across page reloads`() {
        loginViaToken("/portal/profile", regularUser, testAuthSupport)

        // Change language to Ukrainian via locale
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        page.locator("[data-testid='profile-locale-select']").click()
        page.locator("[data-testid='locale-option-uk-UA']").click()
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success
        val successMessage = page.locator("[data-testid='profile-success']")
        assertThat(successMessage).isVisible()

        // Reload the page
        page.reload()

        // Verify UI is still in Ukrainian - check card title
        val profileCardTitle = page.locator("text=Мій профіль")
        assertThat(profileCardTitle).isVisible()

        // Navigate to time logs page
        page.navigate("/portal/time-logs")

        // Verify time logs page is also in Ukrainian - check a visible element
        val currentEntryPanelTitle = page.locator("text=Поточний запис")
        assertThat(currentEntryPanelTitle).isVisible()
    }
}
