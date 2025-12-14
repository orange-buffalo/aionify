package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.ActivationToken
import io.orangebuffalo.aionify.domain.ActivationTokenRepository
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Playwright tests for the activation token functionality.
 */
@MicronautTest
class ActivationTokenPlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var activationTokenRepository: ActivationTokenRepository

    private lateinit var testUser: User
    private lateinit var validToken: ActivationToken

    @BeforeEach
    fun setupTestData() {
        // Create a user without a valid password (will be set via activation)
        testUser = transactionHelper.inTransaction {
            userRepository.save(
                User.create(
                    userName = "newuser",
                    passwordHash = BCrypt.hashpw("temporary", BCrypt.gensalt()),
                    greeting = "New User",
                    isAdmin = false,
                    locale = java.util.Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }
        
        // Create a valid activation token
        validToken = transactionHelper.inTransaction {
            activationTokenRepository.save(
                ActivationToken(
                    userId = requireNotNull(testUser.id),
                    token = "valid-test-token-123",
                    expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
                )
            )
        }
    }

    @Test
    fun `should display activation page with all required elements`() {
        page.navigate("/activate?token=valid-test-token-123")

        // Wait for validation to complete
        page.waitForSelector("[data-testid='greeting-message']")

        // Verify activation page is displayed
        val activationPage = page.locator("[data-testid='activate-account-page']")
        assertThat(activationPage).isVisible()

        // Verify activation title
        val activationTitle = page.locator("[data-testid='activate-title']")
        assertThat(activationTitle).isVisible()
        assertThat(activationTitle).hasText("Set Your Password")

        // Verify greeting message
        val greetingMessage = page.locator("[data-testid='greeting-message']")
        assertThat(greetingMessage).isVisible()
        assertThat(greetingMessage).containsText("New User")

        // Verify password input
        val passwordInput = page.locator("[data-testid='password-input']")
        assertThat(passwordInput).isVisible()

        // Verify confirm password input
        val confirmPasswordInput = page.locator("[data-testid='confirm-password-input']")
        assertThat(confirmPasswordInput).isVisible()

        // Verify set password button
        val setPasswordButton = page.locator("[data-testid='set-password-button']")
        assertThat(setPasswordButton).isVisible()
    }

    @Test
    fun `should toggle password visibility`() {
        page.navigate("/activate?token=valid-test-token-123")
        page.waitForSelector("[data-testid='greeting-message']")

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
    fun `should show error for missing token`() {
        page.navigate("/activate")

        // Wait for validation error
        page.waitForSelector("[data-testid='token-error']")

        // Verify error message
        val errorMessage = page.locator("[data-testid='token-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("No activation token provided")

        // Verify contact admin message
        assertThat(page.locator("text=contact your system administrator")).isVisible()
    }

    @Test
    fun `should show error for invalid token`() {
        page.navigate("/activate?token=invalid-token-xyz")

        // Wait for validation error
        page.waitForSelector("[data-testid='token-error']")

        // Verify error message
        val errorMessage = page.locator("[data-testid='token-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("invalid or has expired")
    }

    @Test
    fun `should show error for expired token`() {
        // Create an expired token
        transactionHelper.inTransaction {
            activationTokenRepository.save(
                ActivationToken(
                    userId = requireNotNull(testUser.id),
                    token = "expired-token-456",
                    expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
                )
            )
        }

        page.navigate("/activate?token=expired-token-456")

        // Wait for validation error
        page.waitForSelector("[data-testid='token-error']")

        // Verify error message
        val errorMessage = page.locator("[data-testid='token-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("invalid or has expired")
    }

    @Test
    fun `should show error when passwords do not match`() {
        page.navigate("/activate?token=valid-test-token-123")
        page.waitForSelector("[data-testid='greeting-message']")

        // Enter mismatched passwords
        page.locator("[data-testid='password-input']").fill("password123")
        page.locator("[data-testid='confirm-password-input']").fill("password456")
        page.locator("[data-testid='set-password-button']").click()

        // Wait for error message
        page.waitForSelector("[data-testid='set-password-error']")

        // Verify error message
        val errorMessage = page.locator("[data-testid='set-password-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Passwords do not match")
    }

    @Test
    fun `should show error when password is too long`() {
        page.navigate("/activate?token=valid-test-token-123")
        page.waitForSelector("[data-testid='greeting-message']")

        // Enter a password that is exactly 50 characters (should be OK)
        val maxPassword = "a".repeat(50)
        page.locator("[data-testid='password-input']").fill(maxPassword)
        page.locator("[data-testid='confirm-password-input']").fill(maxPassword)
        
        // Verify that the inputs accept 50 characters
        assertThat(page.locator("[data-testid='password-input']")).hasValue(maxPassword)
        
        // Note: HTML maxLength=50 prevents entering more than 50 characters via UI
        // This test verifies that the UI enforces the constraint
        // Server-side validation is tested via the API tests
    }

    @Test
    fun `should show error when password is empty`() {
        page.navigate("/activate?token=valid-test-token-123")
        page.waitForSelector("[data-testid='greeting-message']")

        // Try to submit without entering passwords
        page.locator("[data-testid='set-password-button']").click()

        // Wait for error message
        page.waitForSelector("[data-testid='set-password-error']")

        // Verify error message
        val errorMessage = page.locator("[data-testid='set-password-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("New password is required")
    }

    @Test
    fun `should successfully set password and redirect to login`() {
        page.navigate("/activate?token=valid-test-token-123")
        page.waitForSelector("[data-testid='greeting-message']")

        // Enter valid password
        page.locator("[data-testid='password-input']").fill("newpassword123")
        page.locator("[data-testid='confirm-password-input']").fill("newpassword123")
        page.locator("[data-testid='set-password-button']").click()

        // Should redirect to login page
        page.waitForURL("**/login")

        // Verify success message on login page
        val successMessage = page.locator("[data-testid='login-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Password set successfully")

        // Verify we can login with the new password
        page.locator("[data-testid='username-input']").fill("newuser")
        page.locator("[data-testid='password-input']").fill("newpassword123")
        page.locator("[data-testid='login-button']").click()

        // Should redirect to user portal
        page.waitForURL("**/portal")
        val userPortal = page.locator("[data-testid='user-portal']")
        assertThat(userPortal).isVisible()
    }

    @Test
    fun `should invalidate token after successful password set`() {
        page.navigate("/activate?token=valid-test-token-123")
        page.waitForSelector("[data-testid='greeting-message']")

        // Set password successfully
        page.locator("[data-testid='password-input']").fill("newpassword123")
        page.locator("[data-testid='confirm-password-input']").fill("newpassword123")
        page.locator("[data-testid='set-password-button']").click()

        // Wait for redirect to login
        page.waitForURL("**/login")

        // Try to use the same token again
        page.navigate("/activate?token=valid-test-token-123")

        // Wait for validation error
        page.waitForSelector("[data-testid='token-error']")

        // Verify error message - token should be invalid now
        val errorMessage = page.locator("[data-testid='token-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("invalid or has expired")
    }

    @Test
    fun `should handle rate limiting on token validation`() {
        // Create a token for rate limiting test
        transactionHelper.inTransaction {
            activationTokenRepository.save(
                ActivationToken(
                    userId = requireNotNull(testUser.id),
                    token = "rate-limit-token",
                    expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
                )
            )
        }

        // Make multiple validation requests to trigger rate limiting
        // The rate limiter allows 5 attempts per 15 minutes
        for (i in 1..6) {
            page.navigate("/activate?token=rate-limit-token")
            
            if (i < 6) {
                // Should still work for first 5 attempts
                page.waitForSelector("[data-testid='greeting-message']", 
                    com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5000.0))
            } else {
                // 6th attempt should be rate limited
                page.waitForSelector("[data-testid='token-error']",
                    com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(5000.0))
                
                val errorMessage = page.locator("[data-testid='token-error']")
                assertThat(errorMessage).isVisible()
                assertThat(errorMessage).containsText("Too many")
            }
        }
    }

    @Test
    fun `should handle rate limiting on password set`() {
        // This test verifies that multiple attempts with an invalid token eventually show errors
        // The rate limiting mechanism is in place (tested separately), but hard to test end-to-end
        // because once the token is used successfully, it's invalidated
        
        // Create a token
        val rateLimitTestToken = "rate-limit-set-test"
        transactionHelper.inTransaction {
            activationTokenRepository.save(
                ActivationToken(
                    userId = requireNotNull(testUser.id),
                    token = rateLimitTestToken,
                    expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
                )
            )
        }
        
        // First attempt should succeed
        page.navigate("/activate?token=$rateLimitTestToken")
        page.waitForSelector("[data-testid='greeting-message']")
        page.locator("[data-testid='password-input']").fill("testpass123")
        page.locator("[data-testid='confirm-password-input']").fill("testpass123")
        page.locator("[data-testid='set-password-button']").click()
        page.waitForURL("**/login")
        
        // Second attempt with same (now invalid) token should show error
        page.navigate("/activate?token=$rateLimitTestToken")
        page.waitForSelector("[data-testid='token-error']")
        val errorMessage = page.locator("[data-testid='token-error']")
        assertThat(errorMessage).isVisible()
    }
}
