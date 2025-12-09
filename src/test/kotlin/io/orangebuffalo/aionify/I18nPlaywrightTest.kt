package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import org.mindrot.jbcrypt.BCrypt
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.context.annotation.Property
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL
import java.util.Locale

@MicronautTest
class I18nPlaywrightTest : PlaywrightTestBase() {

    @Property(name = "micronaut.server.port")
    var serverPort: Int = 0


    lateinit var baseUrl: URL

    lateinit var loginUrl: URL

    lateinit var portalUrl: URL

    lateinit var userSettingsUrl: URL

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private val regularUserName = "i18nTestUser"
    private val regularUserGreeting = "I18n Test User"

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Initialize URLs
        baseUrl = URL("http://localhost:$serverPort/base")
        loginUrl = URL("http://localhost:$serverPort/login")
        portalUrl = URL("http://localhost:$serverPort/portal")
        userSettingsUrl = URL("http://localhost:$serverPort/userSettings")

        // Create test user with English language
        regularUser = userRepository.save(
            User.create(
                userName = regularUserName,
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = regularUserGreeting,
                isAdmin = false,
                locale = java.util.Locale.ENGLISH,
                languageCode = "en"
            )
        )
    }

    @Test
    fun `should load login page in English when no language preference is saved`() {
        page.navigate(loginUrl.toString())
        
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
        page.navigate(loginUrl.toString())
        
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
        val ukUser = userRepository.save(
            User.create(
                userName = "ukrainianTestUser",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Тестовий користувач",
                isAdmin = false,
                locale = java.util.Locale.forLanguageTag("uk-UA"),
                languageCode = "uk"
            )
        )

        page.navigate(loginUrl.toString())
        page.evaluate("localStorage.clear()")
        page.reload()

        // Login with Ukrainian user
        page.locator("[data-testid='username-input']").fill("ukrainianTestUser")
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to portal
        page.waitForURL("**/portal")

        // Verify UI switched to Ukrainian
        val userTitle = page.locator("[data-testid='user-title']")
        assertThat(userTitle).hasText("Облік часу")
        
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
        page.navigate(loginUrl.toString())
        page.evaluate("localStorage.clear()")
        page.reload()

        // Login with English user
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to portal
        page.waitForURL("**/portal")

        // Verify UI is in English
        val userTitle = page.locator("[data-testid='user-title']")
        assertThat(userTitle).hasText("Time Tracking")
        
        // Verify language was saved to local storage
        val savedLanguage = page.evaluate("localStorage.getItem('aionify_language')")
        assert(savedLanguage == "en") { "Expected language to be 'en' but was '$savedLanguage'" }
    }

    @Test
    fun `should update UI immediately when changing language in settings`() {
        loginViaToken(baseUrl, userSettingsUrl, regularUser, testAuthSupport)

        // Wait for profile to load
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        // Verify page is initially in English
        val settingsTitle = page.locator("[data-testid='settings-title']")
        assertThat(settingsTitle).hasText("Settings")

        // Change language to Ukrainian
        page.locator("[data-testid='profile-language-select']").click()
        page.locator("[data-testid='language-option-uk']").click()
        
        // Change locale (required field)
        page.locator("[data-testid='profile-locale-select']").click()
        page.locator("[data-testid='locale-option-uk-UA']").click()

        // Submit
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success message
        val successMessage = page.locator("[data-testid='profile-success']")
        assertThat(successMessage).isVisible()

        // Verify UI switched to Ukrainian immediately
        assertThat(settingsTitle).hasText("Налаштування")
        assertThat(successMessage).containsText("успішно оновлено")
        
        // Verify the language dropdown now shows Ukrainian label
        val languageSelect = page.locator("[data-testid='profile-language-select']")
        assertThat(languageSelect).containsText("Українська")
    }

    @Test
    fun `should display translated validation errors for profile`() {
        loginViaToken(baseUrl, userSettingsUrl, regularUser, testAuthSupport)

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
        page.locator("[data-testid='profile-language-select']").click()
        page.locator("[data-testid='language-option-uk']").click()
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
        loginViaToken(baseUrl, userSettingsUrl, regularUser, testAuthSupport)

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
        page.locator("[data-testid='profile-language-select']").click()
        page.locator("[data-testid='language-option-uk']").click()
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
        page.navigate(loginUrl.toString())
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
        loginViaToken(baseUrl, userSettingsUrl, regularUser, testAuthSupport)

        // Change language to Ukrainian
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        assertThat(greetingInput).isVisible()

        page.locator("[data-testid='profile-language-select']").click()
        page.locator("[data-testid='language-option-uk']").click()
        page.locator("[data-testid='profile-locale-select']").click()
        page.locator("[data-testid='locale-option-uk-UA']").click()
        page.locator("[data-testid='profile-save-button']").click()

        // Wait for success
        val successMessage = page.locator("[data-testid='profile-success']")
        assertThat(successMessage).isVisible()

        // Reload the page
        page.reload()

        // Verify UI is still in Ukrainian
        val settingsTitle = page.locator("[data-testid='settings-title']")
        assertThat(settingsTitle).hasText("Налаштування")

        // Navigate to portal
        page.navigate(portalUrl.toString())

        // Verify portal is also in Ukrainian
        val userTitle = page.locator("[data-testid='user-title']")
        assertThat(userTitle).hasText("Облік часу")
    }
}
