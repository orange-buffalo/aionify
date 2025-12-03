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

    @BeforeEach
    fun setupTestUsers() {
        // Create a regular (non-admin) user for testing if not exists
        if (userRepository.findByUserName(regularUserName) == null) {
            userRepository.insert(
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
    }

    @Test
    fun `should display login page with all required elements`() {
        page.navigate(loginUrl.toString())

        // Verify login page is displayed
        val loginPage = page.locator("[data-testid='login-page']")
        assertTrue(loginPage.isVisible, "Login page should be visible")

        // Verify login title
        val loginTitle = page.locator("[data-testid='login-title']")
        assertTrue(loginTitle.isVisible, "Login title should be visible")
        assertEquals("Login", loginTitle.textContent())

        // Verify username input
        val usernameInput = page.locator("[data-testid='username-input']")
        assertTrue(usernameInput.isVisible, "Username input should be visible")

        // Verify password input
        val passwordInput = page.locator("[data-testid='password-input']")
        assertTrue(passwordInput.isVisible, "Password input should be visible")

        // Verify login button
        val loginButton = page.locator("[data-testid='login-button']")
        assertTrue(loginButton.isVisible, "Login button should be visible")

        // Verify lost password link
        val lostPasswordLink = page.locator("[data-testid='lost-password-link']")
        assertTrue(lostPasswordLink.isVisible, "Lost password link should be visible")
        assertEquals("Lost password?", lostPasswordLink.textContent())
    }

    @Test
    fun `should toggle password visibility`() {
        page.navigate(loginUrl.toString())

        val passwordInput = page.locator("[data-testid='password-input']")
        val toggleButton = page.locator("[data-testid='toggle-password-visibility']")

        // Initially password should be hidden
        assertEquals("password", passwordInput.getAttribute("type"))

        // Click toggle to show password
        toggleButton.click()
        assertEquals("text", passwordInput.getAttribute("type"))

        // Click again to hide password
        toggleButton.click()
        assertEquals("password", passwordInput.getAttribute("type"))
    }

    @Test
    fun `should show error for invalid credentials`() {
        page.navigate(loginUrl.toString())

        // Enter invalid credentials
        page.locator("[data-testid='username-input']").fill("wronguser")
        page.locator("[data-testid='password-input']").fill("wrongpassword")
        page.locator("[data-testid='login-button']").click()

        // Wait for error message to appear
        val errorMessage = page.locator("[data-testid='login-error']")
        errorMessage.waitFor()

        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("Invalid username or password") == true,
            "Error should indicate invalid credentials"
        )
    }

    @Test
    fun `should show error for wrong password`() {
        page.navigate(loginUrl.toString())

        // Enter valid username but wrong password
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill("wrongpassword")
        page.locator("[data-testid='login-button']").click()

        // Wait for error message to appear
        val errorMessage = page.locator("[data-testid='login-error']")
        errorMessage.waitFor()

        assertTrue(errorMessage.isVisible, "Error message should be visible")
        assertTrue(
            errorMessage.textContent()?.contains("Invalid username or password") == true,
            "Error should indicate invalid credentials"
        )
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
        userPortal.waitFor()
        assertTrue(userPortal.isVisible, "User portal should be visible")

        val userTitle = page.locator("[data-testid='user-title']")
        assertEquals("Time Tracking", userTitle.textContent())
    }

    @Test
    fun `should redirect admin user to admin portal after successful login`() {
        page.navigate(loginUrl.toString())

        // Get the admin password - we need to find it from the test output or use a known admin
        // The default admin "sudo" was created during startup
        // For testing, let's create a test admin with a known password
        val testAdminName = "testadmin"
        val testAdminPassword = "adminPass123"
        if (userRepository.findByUserName(testAdminName) == null) {
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
        }

        // Enter valid credentials for admin user
        page.locator("[data-testid='username-input']").fill(testAdminName)
        page.locator("[data-testid='password-input']").fill(testAdminPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to admin portal
        page.waitForURL("**/admin")

        // Verify we're on the admin portal
        val adminPortal = page.locator("[data-testid='admin-portal']")
        adminPortal.waitFor()
        assertTrue(adminPortal.isVisible, "Admin portal should be visible")

        val adminTitle = page.locator("[data-testid='admin-title']")
        assertEquals("Admin Portal", adminTitle.textContent())
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

        // Logout
        page.locator("[data-testid='logout-button']").click()

        // Wait for redirect back to login
        page.waitForURL("**/login")

        // Verify the username is pre-filled
        val usernameInput = page.locator("[data-testid='username-input']")
        assertEquals(regularUserName, usernameInput.inputValue())

        // Verify welcome back message
        val welcomeBackMessage = page.locator("[data-testid='welcome-back-message']")
        welcomeBackMessage.waitFor()
        assertTrue(welcomeBackMessage.isVisible, "Welcome back message should be visible")
        assertTrue(
            welcomeBackMessage.textContent()?.contains(regularUserGreeting) == true,
            "Welcome message should contain user's greeting"
        )
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

        // Click logout
        page.locator("[data-testid='logout-button']").click()

        // Should redirect to login
        page.waitForURL("**/login")
        
        val loginPage = page.locator("[data-testid='login-page']")
        assertTrue(loginPage.isVisible, "Should be back on login page")
    }

    @Test
    fun `should allow logout from admin portal`() {
        val testAdminName = "testadmin2"
        val testAdminPassword = "adminPass456"
        if (userRepository.findByUserName(testAdminName) == null) {
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
        }

        page.navigate(loginUrl.toString())

        // Login as admin
        page.locator("[data-testid='username-input']").fill(testAdminName)
        page.locator("[data-testid='password-input']").fill(testAdminPassword)
        page.locator("[data-testid='login-button']").click()

        // Wait for admin portal
        page.waitForURL("**/admin")

        // Click logout
        page.locator("[data-testid='logout-button']").click()

        // Should redirect to login
        page.waitForURL("**/login")
        
        val loginPage = page.locator("[data-testid='login-page']")
        assertTrue(loginPage.isVisible, "Should be back on login page")
    }

    @Test
    fun `root path should redirect to login`() {
        page.navigate(loginUrl.toString().replace("/login", "/"))

        // Should redirect to login
        page.waitForURL("**/login")
        
        val loginPage = page.locator("[data-testid='login-page']")
        assertTrue(loginPage.isVisible, "Should be on login page")
    }
}
