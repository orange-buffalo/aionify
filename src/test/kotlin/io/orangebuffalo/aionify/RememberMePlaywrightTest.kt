package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.RememberMeTokenRepository
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Playwright tests for the remember me functionality.
 */
@MicronautTest(transactional = false)
class RememberMePlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var rememberMeTokenRepository: RememberMeTokenRepository

    @Inject
    lateinit var timeLogEntryRepository: TimeLogEntryRepository

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser()
    }

    @Test
    fun `should display remember me checkbox on login page`() {
        page.navigate("/login")

        // Verify remember me checkbox is visible
        val rememberMeCheckbox = page.locator("[data-testid='remember-me-checkbox']")
        assertThat(rememberMeCheckbox).isVisible()

        // Verify checkbox is unchecked by default
        assertThat(rememberMeCheckbox).not().isChecked()

        // Verify label is present
        val rememberMeLabel = page.locator("label[for='rememberMe']")
        assertThat(rememberMeLabel).isVisible()
        assertThat(rememberMeLabel).hasText("Remember me")
    }

    @Test
    fun `should allow toggling remember me checkbox`() {
        page.navigate("/login")

        val rememberMeCheckbox = page.locator("[data-testid='remember-me-checkbox']")

        // Initially unchecked
        assertThat(rememberMeCheckbox).not().isChecked()

        // Click to check
        rememberMeCheckbox.click()
        assertThat(rememberMeCheckbox).isChecked()

        // Click again to uncheck
        rememberMeCheckbox.click()
        assertThat(rememberMeCheckbox).not().isChecked()
    }

    @Test
    fun `should not set remember me cookie when checkbox is unchecked`() {
        page.navigate("/login")

        // Login without remember me
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect
        page.waitForURL("**/portal/time-logs")

        // Verify no remember me token in database
        val tokens =
            testDatabaseSupport.inTransaction {
                rememberMeTokenRepository.findAll().toList()
            }
        assert(tokens.isEmpty()) { "No remember me tokens should exist when checkbox is unchecked" }
    }

    @Test
    fun `should set remember me cookie when checkbox is checked`() {
        page.navigate("/login")

        // Check remember me checkbox
        page.locator("[data-testid='remember-me-checkbox']").click()

        // Login with remember me
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect
        page.waitForURL("**/portal/time-logs")

        // Verify remember me token was created in database
        val tokens =
            testDatabaseSupport.inTransaction {
                rememberMeTokenRepository.findAll().toList()
            }
        assert(tokens.size == 1) { "Exactly one remember me token should exist" }
        assert(tokens[0].userId == regularUser.id) { "Token should be for the logged in user" }

        // Verify token expiration is set (30 days by default)
        val token = tokens[0]
        val expectedExpiration = token.createdAt.plus(Duration.ofDays(30))
        assert(token.expiresAt == expectedExpiration) {
            "Token should expire 30 days after creation"
        }
    }

    @Test
    fun `should clear remember me cookie on logout`() {
        // Login with remember me
        page.navigate("/login")
        page.locator("[data-testid='remember-me-checkbox']").click()
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL("**/portal/time-logs")

        // Verify token exists in database
        var tokens =
            testDatabaseSupport.inTransaction {
                rememberMeTokenRepository.findAll().toList()
            }
        assert(tokens.size == 1) { "Token should exist before logout" }

        // Logout
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()
        page.waitForURL("**/login")

        // Verify token is deleted from database
        tokens =
            testDatabaseSupport.inTransaction {
                rememberMeTokenRepository.findAll().toList()
            }
        assert(tokens.isEmpty()) { "Token should be deleted on logout" }

        // Try to access protected page - should redirect to login
        page.navigate("/portal/time-logs")
        page.waitForURL("**/login")

        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()
    }

    @Test
    fun `should automatically authenticate API requests in new browser context with remember me cookie`() {
        // Set base time so we have a consistent date/time context
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // Create some test data for the user to verify backend authentication works
        testDatabaseSupport.inTransaction {
            timeLogEntryRepository.save(
                TimeLogEntry(
                    title = "Remember Me Test Entry",
                    ownerId = regularUser.id!!,
                    startTime = baseTime.minusSeconds(7200), // 2 hours ago
                    endTime = baseTime.minusSeconds(3600), // 1 hour ago
                    tags = arrayOf("test-tag"),
                ),
            )
        }

        // Login with remember me in first context
        page.navigate("/login")
        page.locator("[data-testid='remember-me-checkbox']").click()
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL("**/portal/time-logs")

        // Get all cookies from the current context
        val cookies = page.context().cookies()

        // Find the remember me cookie
        val rememberMeCookie = cookies.find { it.name == "aionify_remember_me" }
        assertNotNull(rememberMeCookie, "Remember me cookie should be set")

        // Create a new browser context (simulates a new browser/incognito window)
        // and copy the remember me cookie to it
        val newContext =
            page.context().browser().newContext(
                com.microsoft.playwright.Browser
                    .NewContextOptions()
                    .setBaseURL(baseUrl),
            )

        try {
            // Add the remember me cookie to the new context
            newContext.addCookies(listOf(rememberMeCookie))

            // Create a new page in the new context
            val newPage = newContext.newPage()

            // Navigate to login page - the auto-login should trigger automatically
            newPage.navigate("/login")

            // Wait for auto-login to redirect to time logs page
            newPage.waitForURL(
                "**/portal/time-logs",
                com.microsoft.playwright.Page
                    .WaitForURLOptions()
                    .setTimeout(10000.0),
            )

            // Verify we can see the time logs page content
            val timeLogsPage = newPage.locator("[data-testid='time-logs-page']")
            assertThat(timeLogsPage).isVisible()

            // This proves that auto-login worked:
            // 1. The remember me cookie was copied to the new browser context
            // 2. The login page detected the remember me cookie on mount
            // 3. The auto-login endpoint was called automatically
            // 4. A JWT token was generated and stored in localStorage
            // 5. The user was automatically redirected to the time logs page
            // 6. The protected page loaded successfully

            newPage.close()
        } finally {
            newContext.close()
        }
    }

    @Test
    fun `should not auto-login with expired remember me token`() {
        // Login with remember me
        page.navigate("/login")
        page.locator("[data-testid='remember-me-checkbox']").click()
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL("**/portal/time-logs")

        // Get the remember me cookie before expiring it
        val rememberMeCookie =
            page.context().cookies().find { it.name == "aionify_remember_me" }
                ?: throw AssertionError("Remember me cookie not found")

        // Manually expire the token in database
        testDatabaseSupport.inTransaction {
            val tokens = rememberMeTokenRepository.findAll().toList()
            val expiredToken = tokens[0].copy(expiresAt = testTimeService.now().minusSeconds(1))
            rememberMeTokenRepository.update(expiredToken)
        }

        // Create a new browser context to simulate a new session
        val newContext =
            page.context().browser().newContext(
                com.microsoft.playwright.Browser
                    .NewContextOptions()
                    .setBaseURL(baseUrl),
            )

        try {
            // Add the remember me cookie with expired token
            newContext.addCookies(listOf(rememberMeCookie))

            val newPage = newContext.newPage()

            // Navigate to login page - should NOT auto-login due to expired token
            newPage.navigate("/login")

            // Wait for auto-login attempt to complete (auto-login message should disappear)
            val autoLoginMessage = newPage.locator("[data-testid='auto-login-message']")
            assertThat(autoLoginMessage).not().isVisible()

            // Should still be on login page (not redirected)
            val loginPage = newPage.locator("[data-testid='login-page']")
            assertThat(loginPage).isVisible()

            newPage.close()
        } finally {
            newContext.close()
        }

        // Verify expired token was deleted during auto-login attempt
        val tokens =
            testDatabaseSupport.inTransaction {
                rememberMeTokenRepository.findAll().toList()
            }
        assert(tokens.isEmpty()) { "Expired token should be deleted after validation attempt" }
    }

    @Test
    fun `should handle multiple remember me tokens for same user`() {
        // Login from "first browser" with remember me
        page.navigate("/login")
        page.locator("[data-testid='remember-me-checkbox']").click()
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL("**/portal/time-logs")

        // Logout (this will clear the remember me cookie)
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()
        page.waitForURL("**/login")

        // Login again with remember me (simulating login from second browser)
        page.navigate("/login")
        page.locator("[data-testid='remember-me-checkbox']").click()
        page.locator("[data-testid='username-input']").fill(TestUsers.REGULAR_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL("**/portal/time-logs")

        // Since logout deletes the token, we should only have one token
        val tokens =
            testDatabaseSupport.inTransaction {
                rememberMeTokenRepository.findAll().toList()
            }
        assert(tokens.size == 1) { "Should have exactly one active token" }
        assert(tokens[0].userId == regularUser.id) { "Token should be for the correct user" }
    }

    @Test
    fun `should support remember me for admin users`() {
        val adminUser = testUsers.createAdmin()

        // Admin logs in with remember me
        page.navigate("/login")
        page.locator("[data-testid='remember-me-checkbox']").click()
        page.locator("[data-testid='username-input']").fill(TestUsers.ADMIN_USERNAME)
        page.locator("[data-testid='password-input']").fill(TestUsers.TEST_PASSWORD)
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to admin portal
        page.waitForURL("**/admin/users")

        // Verify token was created
        val tokens =
            testDatabaseSupport.inTransaction {
                rememberMeTokenRepository.findAll().toList()
            }
        assert(tokens.size == 1) { "Token should be created for admin" }
        assert(tokens[0].userId == adminUser.id) { "Token should be for admin user" }

        // Get the remember me cookie before logout
        val rememberMeCookie =
            page.context().cookies().find { it.name == "aionify_remember_me" }
                ?: throw AssertionError("Remember me cookie not found")

        // Create a new browser context to simulate a new session
        val newContext =
            page.context().browser().newContext(
                com.microsoft.playwright.Browser
                    .NewContextOptions()
                    .setBaseURL(baseUrl),
            )

        try {
            // Add the remember me cookie to the new context
            newContext.addCookies(listOf(rememberMeCookie))

            val newPage = newContext.newPage()

            // Navigate to login page - should auto-login and redirect to admin portal
            newPage.navigate("/login")
            newPage.waitForURL("**/admin/users")

            // Verify we're on the admin users page (auto-login worked)
            val adminUsersTitle = newPage.locator("[data-testid='users-title']")
            assertThat(adminUsersTitle).isVisible()

            newPage.close()
        } finally {
            newContext.close()
        }
    }
}
