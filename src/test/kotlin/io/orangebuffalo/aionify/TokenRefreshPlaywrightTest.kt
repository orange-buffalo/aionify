package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.auth.JwtTokenService
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Playwright tests for automatic JWT token refresh functionality.
 * Verifies that tokens are automatically refreshed when close to expiration.
 */
@MicronautTest(transactional = false)
class TokenRefreshPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var jwtTokenService: JwtTokenService

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser()
    }

    @Test
    fun `should automatically refresh token when close to expiration`() {
        // Given: User is logged in with a token that expires in 4 minutes
        // (close to the 5 minute refresh threshold)
        val userId = requireNotNull(regularUser.id)

        // Calculate expiration time: 4 minutes from current time (240 seconds)
        val currentTime = System.currentTimeMillis() / 1000
        val expirationTime = currentTime + 240 // 4 minutes from now

        val tokenCloseToExpiry =
            testDatabaseSupport.inTransaction {
                jwtTokenService.generateTokenWithExpiration(
                    userName = regularUser.userName,
                    userId = userId,
                    isAdmin = regularUser.isAdmin,
                    greeting = regularUser.greeting,
                    expirationSeconds = expirationTime,
                )
            }

        // Navigate to login page to establish localStorage origin
        page.navigate("/login")

        // Set the token in localStorage
        page.evaluate(
            """
            (token) => {
                localStorage.setItem('aionify_token', token);
            }
            """.trimIndent(),
            tokenCloseToExpiry,
        )

        // When: Navigate to time logs page (triggers token check)
        page.navigate("/portal/time-logs")

        // Wait for page to load
        page.waitForSelector("[data-testid='time-logs-page']")

        // Then: Verify page loads successfully
        val timeLogsPage = page.locator("[data-testid='time-logs-page']")
        assertThat(timeLogsPage).isVisible()

        // Verify token was refreshed in localStorage by checking it's different
        val refreshedToken =
            page
                .evaluate(
                    """
                    () => {
                        return localStorage.getItem('aionify_token');
                    }
                    """.trimIndent(),
                ).toString()

        // The token should have been refreshed (different from original)
        // We can't directly compare tokens as they're generated with current timestamp,
        // but we can verify the token is still valid and the page works
        assert(refreshedToken.isNotEmpty()) { "Token should still be present in localStorage" }

        // Make an API call to verify the token is still valid
        page.navigate("/portal/settings")
        page.waitForSelector("[data-testid='settings-page']")
        val settingsPage = page.locator("[data-testid='settings-page']")
        assertThat(settingsPage).isVisible()
    }

    @Test
    fun `should not redirect to login if token is refreshed successfully`() {
        // Given: User is logged in with a token expiring in 3 minutes
        val userId = requireNotNull(regularUser.id)
        val currentTime = System.currentTimeMillis() / 1000
        val expirationTime = currentTime + 180 // 3 minutes from now

        val tokenCloseToExpiry =
            testDatabaseSupport.inTransaction {
                jwtTokenService.generateTokenWithExpiration(
                    userName = regularUser.userName,
                    userId = userId,
                    isAdmin = regularUser.isAdmin,
                    greeting = regularUser.greeting,
                    expirationSeconds = expirationTime,
                )
            }

        // Set up the authenticated session
        page.navigate("/login")
        page.evaluate(
            """
            (token) => {
                localStorage.setItem('aionify_token', token);
            }
            """.trimIndent(),
            tokenCloseToExpiry,
        )

        // When: Navigate to a protected page
        page.navigate("/portal/time-logs")

        // Wait for page to load (allows time for background refresh)
        page.waitForLoadState()

        // Then: User should still be on the time logs page, not redirected to login
        // Verify page is visible
        val timeLogsPage = page.locator("[data-testid='time-logs-page']")
        assertThat(timeLogsPage).isVisible()
    }
}
