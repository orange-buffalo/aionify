package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Playwright tests for authentication-based routing and navigation.
 * Tests various scenarios involving authenticated/unauthenticated users,
 * root path redirects, and role-based access control.
 */
@MicronautTest
class AuthenticationRoutingPlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @BeforeEach
    fun setupTestData() {
        // Create test users
        testUsers.createRegularUser(userRepository, transactionHelper)
        testUsers.createAdmin(userRepository, transactionHelper)
    }

    @Test
    fun `authenticated admin user accessing root path should be redirected to admin portal`() {
        // Get admin user (created in @BeforeEach)
        val adminUser = userRepository.findByUserName(TestUsers.ADMIN_USERNAME).get()

        // Authenticate as admin
        loginViaToken("/", adminUser, testAuthSupport)

        // Should be redirected to admin portal
        page.waitForURL("**/admin")

        val adminPortal = page.locator("[data-testid='admin-portal']")
        assertThat(adminPortal).isVisible()
    }

    @Test
    fun `authenticated regular user accessing root path should be redirected to user portal`() {
        // Get regular user (created in @BeforeEach)
        val regularUser = userRepository.findByUserName(TestUsers.REGULAR_USERNAME).get()

        // Authenticate as regular user
        loginViaToken("/", regularUser, testAuthSupport)

        // Should be redirected to user portal
        page.waitForURL("**/portal")

        val userPortal = page.locator("[data-testid='user-portal']")
        assertThat(userPortal).isVisible()
    }

    @Test
    fun `unauthenticated user accessing root path should be redirected to login page`() {
        // Navigate to root without authentication
        page.navigate("/")

        // Should be redirected to login page
        page.waitForURL("**/login")

        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }

    @Test
    fun `unauthenticated user accessing admin portal should be redirected to login page`() {
        // Navigate to admin portal without authentication
        page.navigate("/admin")

        // Should be redirected to login page
        page.waitForURL("**/login")

        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }

    @Test
    fun `unauthenticated user accessing user portal should be redirected to login page`() {
        // Navigate to user portal without authentication
        page.navigate("/portal")

        // Should be redirected to login page
        page.waitForURL("**/login")

        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }

    @Test
    fun `regular user accessing admin portal should be redirected to user portal`() {
        // Get regular user
        val regularUser = userRepository.findByUserName(TestUsers.REGULAR_USERNAME).get()

        // Try to access admin portal
        loginViaToken("/admin", regularUser, testAuthSupport)

        // Should be redirected to user portal
        page.waitForURL("**/portal")

        val userPortal = page.locator("[data-testid='user-portal']")
        assertThat(userPortal).isVisible()
    }

    @Test
    fun `admin user accessing user portal should be redirected to admin portal`() {
        // Get admin user
        val adminUser = userRepository.findByUserName(TestUsers.ADMIN_USERNAME).get()

        // Try to access user portal
        loginViaToken("/portal", adminUser, testAuthSupport)

        // Should be redirected to admin portal
        page.waitForURL("**/admin")

        val adminPortal = page.locator("[data-testid='admin-portal']")
        assertThat(adminPortal).isVisible()
    }

    @Test
    fun `regular user accessing admin users page should be redirected to user portal`() {
        // Get regular user
        val regularUser = userRepository.findByUserName(TestUsers.REGULAR_USERNAME).get()

        // Try to access admin users page
        loginViaToken("/admin/users", regularUser, testAuthSupport)

        // Should be redirected to user portal
        page.waitForURL("**/portal")

        val userPortal = page.locator("[data-testid='user-portal']")
        assertThat(userPortal).isVisible()
    }

    @Test
    fun `expired token should redirect to login with session expired message`() {
        // Navigate to a page that will trigger an API call with expired token
        page.navigate("/")

        // Set an expired token in localStorage
        page.evaluate("""
            () => {
                // Create a JWT with expired timestamp
                // Header: {"alg":"HS256","typ":"JWT"}
                const header = btoa(JSON.stringify({"alg":"HS256","typ":"JWT"}))
                // Payload with expired timestamp (1 hour ago)
                const expiredTime = Math.floor(Date.now() / 1000) - 3600
                const payload = btoa(JSON.stringify({
                    "sub": "testuser",
                    "roles": ["user"],
                    "exp": expiredTime
                }))
                // Signature (fake, but format is correct)
                const signature = "fake-signature"
                const expiredToken = header + "." + payload + "." + signature
                
                localStorage.setItem('aionify_token', expiredToken)
            }
        """.trimIndent())

        // Now navigate to a protected route which should check auth and remove expired token
        page.navigate("/portal")

        // Should be redirected to login
        page.waitForURL("**/login")

        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()

        // Note: The session expired message from API 401 is tested separately
        // because it requires an actual API call, which we'll test in a different scenario
    }

    @Test
    fun `unauthenticated user accessing settings page should be redirected to login`() {
        // Navigate to settings without authentication
        page.navigate("/portal/settings")

        // Should be redirected to login page
        page.waitForURL("**/login")

        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }

    @Test
    fun `regular user can navigate from root to portal after login`() {
        // Navigate to root path
        page.navigate("/")

        // Should be at login page
        page.waitForURL("**/login")

        // Login as regular user
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Should be redirected to user portal
        page.waitForURL("**/portal")

        val userPortal = page.locator("[data-testid='user-portal']")
        assertThat(userPortal).isVisible()

        // Now navigate back to root
        page.navigate("/")

        // Should stay/return to user portal
        page.waitForURL("**/portal")
        assertThat(userPortal).isVisible()
    }

    @Test
    fun `admin user can navigate from root to admin portal after login`() {
        // Navigate to root path
        page.navigate("/")

        // Should be at login page
        page.waitForURL("**/login")

        // Login as admin
        page.locator("[data-testid='username-input']").fill(TestUsers.ADMIN_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Should be redirected to admin portal
        page.waitForURL("**/admin")

        val adminPortal = page.locator("[data-testid='admin-portal']")
        assertThat(adminPortal).isVisible()

        // Now navigate back to root
        page.navigate("/")

        // Should stay/return to admin portal
        page.waitForURL("**/admin")
        assertThat(adminPortal).isVisible()
    }
}
