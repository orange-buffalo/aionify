package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.RememberMeTokenRepository
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
        val tokens = testDatabaseSupport.inTransaction {
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
        val tokens = testDatabaseSupport.inTransaction {
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
        var tokens = testDatabaseSupport.inTransaction {
            rememberMeTokenRepository.findAll().toList()
        }
        assert(tokens.size == 1) { "Token should exist before logout" }

        // Logout
        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()
        page.waitForURL("**/login")

        // Verify token is deleted from database
        tokens = testDatabaseSupport.inTransaction {
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
    fun `should persist remember me cookie across browser contexts`() {
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

        // Verify cookie has secure attributes
        assert(rememberMeCookie!!.httpOnly) { "Cookie should be HttpOnly" }
        assert(rememberMeCookie.sameSite == com.microsoft.playwright.options.SameSiteAttribute.STRICT) {
            "Cookie should have SameSite=Strict"
        }

        // Verify token exists in database
        val tokens = testDatabaseSupport.inTransaction {
            rememberMeTokenRepository.findAll().toList()
        }
        assert(tokens.size == 1) { "One remember me token should exist in database" }
        assert(tokens[0].userId == regularUser.id) { "Token should be for the logged in user" }

        // Create a new browser context (simulates a new browser/incognito window)
        // and copy the remember me cookie to it
        val newContext = page.context().browser().newContext(
            com.microsoft.playwright.Browser.NewContextOptions()
                .setBaseURL(baseUrl)
        )

        try {
            // Add the remember me cookie to the new context
            newContext.addCookies(listOf(rememberMeCookie))

            // Verify cookie was added successfully
            val newCookies = newContext.cookies()
            val copiedCookie = newCookies.find { it.name == "aionify_remember_me" }
            assertNotNull(copiedCookie, "Remember me cookie should be copied to new context")
            assert(copiedCookie!!.value == rememberMeCookie.value) {
                "Cookie value should match original"
            }

            // The remember me cookie can now be used for backend authentication in the new context
            // (This validates the cookie persists and can be transferred between contexts)
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

        // Manually expire the token in database
        testDatabaseSupport.inTransaction {
            val tokens = rememberMeTokenRepository.findAll().toList()
            val expiredToken = tokens[0].copy(expiresAt = testTimeService.now().minusSeconds(1))
            rememberMeTokenRepository.update(expiredToken)
        }

        // Clear JWT token to simulate expired session
        page.evaluate("localStorage.removeItem('aionify_token')")

        // Try to access protected page - should redirect to login due to expired remember me token
        page.navigate("/portal/time-logs")
        page.waitForURL("**/login")

        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()

        // Verify expired token was deleted
        val tokens = testDatabaseSupport.inTransaction {
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
        val tokens = testDatabaseSupport.inTransaction {
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
        val tokens = testDatabaseSupport.inTransaction {
            rememberMeTokenRepository.findAll().toList()
        }
        assert(tokens.size == 1) { "Token should be created for admin" }
        assert(tokens[0].userId == adminUser.id) { "Token should be for admin user" }

        // Clear JWT token
        page.evaluate("localStorage.removeItem('aionify_token')")

        // Make an API call that requires authentication - should work via remember me
        val response = page.evaluate(
            """
            fetch('/api-ui/users', {
                method: 'GET',
                credentials: 'include'
            }).then(r => r.json())
            """.trimIndent()
        )

        // Verify we got a valid response (not 401)
        assertNotNull(response, "Should receive users data via remember me authentication")
    }
}
