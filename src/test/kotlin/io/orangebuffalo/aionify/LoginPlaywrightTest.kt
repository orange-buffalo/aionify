package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.User
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * Playwright tests for the login functionality.
 */
@MicronautTest(transactional = false)
class LoginPlaywrightTest : PlaywrightTestBase() {

    private lateinit var regularUser: User
    private lateinit var adminUser: User

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser()
        adminUser = testUsers.createAdmin()
    }

    @Test
    fun `should display login page with all required elements`() {
        page.navigate("/login")

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
        page.navigate("/login")

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
        page.navigate("/login")

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
        page.navigate("/login")

        // Enter valid username but wrong password
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill("wrongpassword")
        page.locator("[data-testid='login-button']").click()

        // Wait for error message to appear and verify
        val errorMessage = page.locator("[data-testid='login-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Invalid username or password")
    }

    @Test
    fun `should redirect regular user to time logs page after successful login`() {
        page.navigate("/login")

        // Enter valid credentials for regular user
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to time logs page
        page.waitForURL("**/portal/time-logs")

        // Verify we're on the time logs page
        val timeLogsPage = page.locator("[data-testid='time-logs-page']")
        assertThat(timeLogsPage).isVisible()

        val pageTitle = page.locator("[data-testid='time-logs-title']")
        assertThat(pageTitle).hasText("Time Log")
    }

    @Test
    fun `should redirect admin user to admin users page after successful login`() {
        page.navigate("/login")

        // Enter valid credentials for admin user (created in @BeforeEach)
        page.locator("[data-testid='username-input']").fill(TestUsers.ADMIN_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to admin users page
        page.waitForURL("**/admin/users")

        // Verify we're on the users page
        val usersPage = page.locator("[data-testid='users-page']")
        assertThat(usersPage).isVisible()

        val pageTitle = page.locator("[data-testid='users-title']")
        assertThat(pageTitle).hasText("Users")
    }

    @Test
    fun `should remember last logged in user`() {
        page.navigate("/login")

        // Login with regular user
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to time logs page
        page.waitForURL("**/portal/time-logs")

        // Logout - first open profile menu, then click logout
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()

        // Wait for redirect back to login
        page.waitForURL("**/login")

        // Verify the username is pre-filled
        val usernameInput = page.locator("[data-testid='username-input']")
        assertThat(usernameInput).hasValue(TestUsers.REGULAR_USERNAME)

        // Verify welcome back message
        val welcomeBackMessage = page.locator("[data-testid='welcome-back-message']")
        assertThat(welcomeBackMessage).isVisible()
        assertThat(welcomeBackMessage).containsText(TestUsers.REGULAR_GREETING)
    }

    @Test
    fun `should allow logout from user portal`() {
        page.navigate("/login")

        // Login
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Wait for user time logs page
        page.waitForURL("**/portal/time-logs")

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

        // Create user in a separate transaction to ensure it's committed
        testDatabaseSupport.insert(
            User.create(
                userName = testAdminName,
                passwordHash = BCrypt.hashpw(testAdminPassword, BCrypt.gensalt()),
                greeting = "Test Admin 2",
                isAdmin = true,
                locale = java.util.Locale.ENGLISH,
                languageCode = "en"
            )
        )

        page.navigate("/login")

        // Login as admin
        page.locator("[data-testid='username-input']").fill(testAdminName)
        page.locator("[data-testid='password-input']").fill(testAdminPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for admin users page
        page.waitForURL("**/admin/users")

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
        page.navigate("/".replace("/login", "/"))

        // Should redirect to login
        page.waitForURL("**/login")

        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }
    
    @Test
    fun `should display forgot password dialog when lost password link is clicked`() {
        page.navigate("/login")
        
        // Verify lost password link is visible
        val lostPasswordLink = page.locator("[data-testid='lost-password-link']")
        assertThat(lostPasswordLink).isVisible()
        
        // Click the lost password link
        lostPasswordLink.click()
        
        // Wait for dialog to appear
        page.waitForSelector("[data-testid='forgot-password-dialog']")
        
        // Verify dialog is visible
        val dialog = page.locator("[data-testid='forgot-password-dialog']")
        assertThat(dialog).isVisible()
        
        // Verify dialog contains expected text
        assertThat(dialog).containsText("Password Reset")
        assertThat(dialog).containsText("contact your system administrator")
        
        // Verify close button is present
        val closeButton = page.locator("[data-testid='forgot-password-dialog-close']")
        assertThat(closeButton).isVisible()
        
        // Click close button
        closeButton.click()
        
        // Dialog should close
        assertThat(dialog).not().isVisible()
    }
}
