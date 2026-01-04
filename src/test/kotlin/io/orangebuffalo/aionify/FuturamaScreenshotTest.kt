package io.orangebuffalo.aionify

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.Locale

/**
 * Test to generate demo screenshots for the README using Futurama theme.
 * Creates users "Fry" (regular user) and "Farnsworth" (admin) with demo data.
 * Uses a larger viewport (1280x2261) to prevent screenshot clipping.
 */
@MicronautTest(transactional = false)
class FuturamaScreenshotTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var fry: User
    private lateinit var farnsworth: User
    private val password = "password123"

    @BeforeEach
    fun setupDemoData() {
        // Set larger viewport to prevent screenshot clipping
        // Height = largest screenshot (2161px) + 100px buffer = 2261px
        page.setViewportSize(1280, 2261)

        // Create Fry as regular user
        fry =
            testDatabaseSupport.insert(
                User.create(
                    userName = "fry",
                    passwordHash = BCrypt.hashpw(password, BCrypt.gensalt()),
                    greeting = "Philip J. Fry",
                    isAdmin = false,
                    locale = Locale.UK, // UK locale for 24-hour time format
                ),
            )

        // Create Farnsworth as admin user
        farnsworth =
            testDatabaseSupport.insert(
                User.create(
                    userName = "farnsworth",
                    passwordHash = BCrypt.hashpw(password, BCrypt.gensalt()),
                    greeting = "Professor Farnsworth",
                    isAdmin = true,
                    locale = Locale.UK,
                ),
            )

        // Create historical time log entries for Fry with Futurama-themed tasks
        // This creates data for tags statistics and week view
        val fryId = requireNotNull(fry.id)

        // FIXED_TEST_TIME is Saturday, March 16, 2024 at 03:30 NZDT (Friday Mar 15 14:30 UTC)
        // Week is Mon Mar 11 - Sun Mar 17

        // Monday (Mar 11) - Package deliveries
        val monday = FIXED_TEST_TIME.minusSeconds(5 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = monday.minusSeconds(7 * 3600),
                endTime = monday.minusSeconds(5 * 3600),
                title = "Delivery to the Moon",
                ownerId = fryId,
                tags = arrayOf("delivery", "space-travel"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = monday.minusSeconds(4 * 3600),
                endTime = monday.minusSeconds(2 * 3600),
                title = "Package sorting at Planet Express",
                ownerId = fryId,
                tags = arrayOf("delivery", "office"),
            ),
        )

        // Tuesday (Mar 12) - More deliveries
        val tuesday = FIXED_TEST_TIME.minusSeconds(4 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = tuesday.minusSeconds(8 * 3600),
                endTime = tuesday.minusSeconds(6 * 3600),
                title = "Delivery to Omicron Persei 8",
                ownerId = fryId,
                tags = arrayOf("delivery", "space-travel", "aliens"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = tuesday.minusSeconds(5 * 3600),
                endTime = tuesday.minusSeconds(4 * 3600),
                title = "Fuel the ship",
                ownerId = fryId,
                tags = arrayOf("maintenance", "ship"),
            ),
        )

        // Wednesday (Mar 13) - Mixed tasks
        val wednesday = FIXED_TEST_TIME.minusSeconds(3 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusSeconds(9 * 3600),
                endTime = wednesday.minusSeconds(7 * 3600),
                title = "Attend crew meeting",
                ownerId = fryId,
                tags = arrayOf("meeting", "office"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusSeconds(6 * 3600),
                endTime = wednesday.minusSeconds(3 * 3600 + 1800),
                title = "Help Bender with brewery plans",
                ownerId = fryId,
                tags = arrayOf("side-project", "bender"),
            ),
        )

        // Thursday (Mar 14) - Learning and development
        val thursday = FIXED_TEST_TIME.minusSeconds(2 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = thursday.minusSeconds(7 * 3600),
                endTime = thursday.minusSeconds(5 * 3600),
                title = "Study holophonor",
                ownerId = fryId,
                tags = arrayOf("learning", "music"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = thursday.minusSeconds(4 * 3600),
                endTime = thursday.minusSeconds(2 * 3600 + 1800),
                title = "Watch TV with Bender",
                ownerId = fryId,
                tags = arrayOf("break", "bender"),
            ),
        )

        // Friday (Mar 15) - Yesterday
        val friday = FIXED_TEST_TIME.minusSeconds(1 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = friday.minusSeconds(8 * 3600),
                endTime = friday.minusSeconds(6 * 3600),
                title = "Emergency delivery to Mars Vegas",
                ownerId = fryId,
                tags = arrayOf("delivery", "space-travel", "urgent"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = friday.minusSeconds(5 * 3600),
                endTime = friday.minusSeconds(3 * 3600),
                title = "Ship repairs after crash",
                ownerId = fryId,
                tags = arrayOf("maintenance", "ship", "urgent"),
            ),
        )

        // Saturday (Mar 16) - Today, with one completed entry and one active (running) entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(2 * 3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1 * 3600),
                title = "Morning coffee with Leela",
                ownerId = fryId,
                tags = arrayOf("break", "leela"),
            ),
        )

        // Active (running) task - no end time
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(30 * 60), // Started 30 minutes ago
                endTime = null, // Still running
                title = "Delivery to Neptune",
                ownerId = fryId,
                tags = arrayOf("delivery", "space-travel"),
            ),
        )

        // Generate API token for Fry (for settings page screenshot)
        testDatabaseSupport.insert(
            UserApiAccessToken(
                userId = fryId,
                token = "demo1234567890abcdefghijklmnopqrstuvwxyz1234567890",
            ),
        )
    }

    @Test
    fun `generate login page screenshot`() {
        page.navigate("/login")

        // Fill in username to show the login form populated
        page.locator("[data-testid='username-input']").fill("fry")

        // Wait for page to be fully rendered
        page.waitForTimeout(500.0)

        // Take screenshot
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/login-page.png"),
                ).setFullPage(false),
        )
    }

    @Test
    fun `generate user management page screenshot`() {
        // Login as Farnsworth (admin) and navigate to users page
        loginViaToken("/admin/users", farnsworth, testAuthSupport)

        // Wait for users table to load
        val usersTable = page.locator("[data-testid='users-table-container']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(usersTable)
            .isVisible()

        // Wait for page to be fully rendered
        page.waitForTimeout(500.0)

        // Take screenshot
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/user-management-page.png"),
                ).setFullPage(false),
        )
    }

    @Test
    fun `generate time logs page screenshot with active task and week view`() {
        // Login as Fry and navigate to time logs page
        loginViaToken("/portal/time-logs", fry, testAuthSupport)

        // Wait for time logs page to load
        val timeLogsPage = page.locator("[data-testid='time-logs-page']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(timeLogsPage)
            .isVisible()

        // Wait for active task to be visible
        val activeEntryPanel = page.locator("[data-testid='current-entry-panel']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(activeEntryPanel)
            .isVisible()

        // Wait for week view to load with grouping by days
        val weekRange = page.locator("[data-testid='week-range']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(weekRange)
            .isVisible()

        // Wait for page to be fully rendered
        page.waitForTimeout(500.0)

        // Take full page screenshot to show all entries grouped by days
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/time-logs-page.png"),
                ).setFullPage(true),
        )
    }

    @Test
    fun `generate settings page screenshot with tags statistics`() {
        // Login as Fry and navigate to settings page
        loginViaToken("/portal/settings", fry, testAuthSupport)

        // Wait for settings page to load
        val settingsPage = page.locator("[data-testid='settings-page']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(settingsPage)
            .isVisible()

        // Wait for tags statistics to load
        val tagsTable = page.locator("[data-testid='tags-table']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(tagsTable)
            .isVisible()

        // Wait for page to be fully rendered
        page.waitForTimeout(500.0)

        // Take full page screenshot to show tags statistics and API token section
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/settings-page-tags.png"),
                ).setFullPage(true),
        )
    }

    @Test
    fun `generate settings page screenshot with API token shown`() {
        // Login as Fry and navigate to settings page
        loginViaToken("/portal/settings", fry, testAuthSupport)

        // Wait for settings page to load
        val settingsPage = page.locator("[data-testid='settings-page']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(settingsPage)
            .isVisible()

        // Wait for API token section to load
        val apiTokenInput = page.locator("[data-testid='api-token-input']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(apiTokenInput)
            .isVisible()

        // Click show button to reveal the token
        val showButton = page.locator("[data-testid='show-api-token-button']")
        showButton.click()

        // Wait for token to be revealed
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(
                apiTokenInput,
            ).not()
            .hasValue("••••••••••••••••••••••••••••••••")

        // Wait for page to be fully rendered
        page.waitForTimeout(500.0)

        // Take screenshot focusing on API token section
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/settings-page-api-token.png"),
                ).setFullPage(true),
        )
    }

    @Test
    fun `generate profile page screenshot`() {
        // Login as Fry and navigate to profile page
        loginViaToken("/portal/profile", fry, testAuthSupport)

        // Wait for profile page to load
        val profilePage = page.locator("[data-testid='profile-page']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(profilePage)
            .isVisible()

        // Wait for profile form to be visible
        val greetingInput = page.locator("[data-testid='profile-greeting-input']")
        com.microsoft.playwright.assertions.PlaywrightAssertions
            .assertThat(greetingInput)
            .isVisible()

        // Wait for page to be fully rendered
        page.waitForTimeout(500.0)

        // Take screenshot
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/profile-page.png"),
                ).setFullPage(true),
        )
    }
}
