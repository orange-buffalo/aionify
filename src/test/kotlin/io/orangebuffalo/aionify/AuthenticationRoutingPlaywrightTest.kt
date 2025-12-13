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
    fun `expired token should redirect to login with session expired message when making API call`() {
        // Get regular user
        val regularUser = userRepository.findByUserName(TestUsers.REGULAR_USERNAME).get()

        // Generate an expired token using Micronaut's token generator
        val expiredToken = testAuthSupport.generateExpiredToken(regularUser)

        // Navigate to login page first to set up the origin for localStorage
        page.navigate("/login")

        // Set the expired token in localStorage
        page.evaluate("""
            (token) => {
                localStorage.setItem('aionify_token', token);
            }
        """.trimIndent(), expiredToken)

        // Navigate to a page that makes an API call (settings page loads profile)
        page.navigate("/portal/settings")

        // Should be redirected to login due to 401 response from API
        page.waitForURL("**/login")

        // Verify login page is shown
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()

        // Verify session expired message is displayed
        val errorMessage = page.locator("[data-testid='login-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("session has expired")
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
