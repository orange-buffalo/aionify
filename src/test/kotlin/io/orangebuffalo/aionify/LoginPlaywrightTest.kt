package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import io.quarkus.elytron.security.common.BcryptUtil
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL
import java.util.Locale

@QuarkusTest
class LoginPlaywrightTest : PlaywrightTestBase() {

    @TestHTTPResource("/login")
    lateinit var loginUrl: URL

    @TestHTTPResource("/admin")
    lateinit var adminUrl: URL

    @TestHTTPResource("/portal")
    lateinit var userPortalUrl: URL

    @Inject
    lateinit var userRepository: UserRepository

    private val testPassword = "testPassword123"
    private val regularUserName = "testuser"
    private val regularUserGreeting = "Test User"

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

    @Test
    fun `should display login page with all required elements`() {
        page.navigate(loginUrl.toString())

        // Verify login page is displayed
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()

        // Verify login title
        val loginTitle = page.locator("[data-testid='login-title']")
        assertThat(loginTitle).isVisible()
        assertThat(loginTitle).hasText("Login")

        // Verify username input
        val usernameInput = page.locator("[data-testid='username-input']")
        assertThat(usernameInput).isVisible()

        // Verify password input
        val passwordInput = page.locator("[data-testid='password-input']")
        assertThat(passwordInput).isVisible()

        // Verify login button
        val loginButton = page.locator("[data-testid='login-button']")
        assertThat(loginButton).isVisible()

        // Verify lost password link
        val lostPasswordLink = page.locator("[data-testid='lost-password-link']")
        assertThat(lostPasswordLink).isVisible()
        assertThat(lostPasswordLink).hasText("Lost password?")
    }

    @Test
    fun `should toggle password visibility`() {
        page.navigate(loginUrl.toString())

        val passwordInput = page.locator("[data-testid='password-input']")
        val toggleButton = page.locator("[data-testid='toggle-password-visibility']")

        // Initially password should be hidden
        assertThat(passwordInput).hasAttribute("type", "password")

        // Click toggle to show password
        toggleButton.click()
        assertThat(passwordInput).hasAttribute("type", "text")

        // Click again to hide password
        toggleButton.click()
        assertThat(passwordInput).hasAttribute("type", "password")
    }

    @Test
    fun `should show error for invalid credentials`() {
        page.navigate(loginUrl.toString())

        // Enter invalid credentials
        page.locator("[data-testid='username-input']").fill("wronguser")
        page.locator("[data-testid='password-input']").fill("wrongpassword")
        page.locator("[data-testid='login-button']").click()

        // Wait for error message to appear and verify
        val errorMessage = page.locator("[data-testid='login-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Invalid username or password")
    }

    @Test
    fun `should show error for wrong password`() {
        page.navigate(loginUrl.toString())

        // Enter valid username but wrong password
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill("wrongpassword")
        page.locator("[data-testid='login-button']").click()

        // Wait for error message to appear and verify
        val errorMessage = page.locator("[data-testid='login-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Invalid username or password")
    }

    @Test
    fun `should redirect regular user to user portal after successful login`() {
        page.navigate(loginUrl.toString())

        // Enter valid credentials for regular user
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to user portal
        page.waitForURL("**/portal")

        // Verify we're on the user portal
        val userPortal = page.locator("[data-testid='user-portal']")
        assertThat(userPortal).isVisible()

        val userTitle = page.locator("[data-testid='user-title']")
        assertThat(userTitle).hasText("Time Tracking")
    }

    @Test
    fun `should redirect admin user to admin portal after successful login`() {
        page.navigate(loginUrl.toString())

        // Create a test admin with a known password
        val testAdminName = "testadmin"
        val testAdminPassword = "adminPass123"
        userRepository.insert(
            User(
                userName = testAdminName,
                passwordHash = BcryptUtil.bcryptHash(testAdminPassword),
                greeting = "Test Admin",
                isAdmin = true,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )

        // Enter valid credentials for admin user
        page.locator("[data-testid='username-input']").fill(testAdminName)
        page.locator("[data-testid='password-input']").fill(testAdminPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to admin portal
        page.waitForURL("**/admin")

        // Verify we're on the admin portal
        val adminPortal = page.locator("[data-testid='admin-portal']")
        assertThat(adminPortal).isVisible()

        val adminTitle = page.locator("[data-testid='admin-title']")
        assertThat(adminTitle).hasText("Admin Portal")
    }

    @Test
    fun `should remember last logged in user`() {
        page.navigate(loginUrl.toString())

        // Login with regular user
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to user portal
        page.waitForURL("**/portal")

        // Logout - first open profile menu, then click logout
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()

        // Wait for redirect back to login
        page.waitForURL("**/login")

        // Verify the username is pre-filled
        val usernameInput = page.locator("[data-testid='username-input']")
        assertThat(usernameInput).hasValue(regularUserName)

        // Verify welcome back message
        val welcomeBackMessage = page.locator("[data-testid='welcome-back-message']")
        assertThat(welcomeBackMessage).isVisible()
        assertThat(welcomeBackMessage).containsText(regularUserGreeting)
    }

    @Test
    fun `should allow logout from user portal`() {
        page.navigate(loginUrl.toString())

        // Login
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for user portal
        page.waitForURL("**/portal")

        // Click logout - first open profile menu, then click logout
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()

        // Should redirect to login
        page.waitForURL("**/login")
        
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }

    @Test
    fun `should allow logout from admin portal`() {
        val testAdminName = "testadmin2"
        val testAdminPassword = "adminPass456"
        userRepository.insert(
            User(
                userName = testAdminName,
                passwordHash = BcryptUtil.bcryptHash(testAdminPassword),
                greeting = "Test Admin 2",
                isAdmin = true,
                locale = Locale.ENGLISH,
                languageCode = "en"
            )
        )

        page.navigate(loginUrl.toString())

        // Login as admin
        page.locator("[data-testid='username-input']").fill(testAdminName)
        page.locator("[data-testid='password-input']").fill(testAdminPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for admin portal
        page.waitForURL("**/admin")

        // Click logout - first open profile menu, then click logout
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()

        // Should redirect to login
        page.waitForURL("**/login")
        
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }

    @Test
    fun `root path should redirect to login`() {
        page.navigate(loginUrl.toString().replace("/login", "/"))

        // Should redirect to login
        page.waitForURL("**/login")
        
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }
}
