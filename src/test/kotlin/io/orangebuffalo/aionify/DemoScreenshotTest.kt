package io.orangebuffalo.aionify

import com.microsoft.playwright.Page
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessToken
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

/**
 * Test to generate demo screenshots for the README using Futurama theme.
 * Creates users "Fry" (regular user) and "Farnsworth" (admin) with demo data.
 * Uses content-based viewport sizing for optimal screenshot display.
 *
 * Enable and run manually when demo screenshots need to be updated.
 */
@Disabled("Enable and run manually when demo screenshots need to be updated")
@MicronautTest(transactional = false)
class DemoScreenshotTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var fry: User
    private lateinit var farnsworth: User
    private val password = "password123"

    @BeforeEach
    fun setupDemoData() {
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

        // setBaseTime("2024-03-16", "03:30") is Saturday, March 16, 2024 at 03:30 NZDT (Friday Mar 15 14:30 UTC)
        // Week is Mon Mar 11 - Sun Mar 17

        // Monday (Mar 11) - Package deliveries
        val monday = setBaseTime("2024-03-16", "03:30").minusSeconds(5 * 24 * 3600)
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
                tags = arrayOf("delivery"),
            ),
        )

        // Tuesday (Mar 12) - More deliveries
        val tuesday = setBaseTime("2024-03-16", "03:30").minusSeconds(4 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = tuesday.minusSeconds(8 * 3600),
                endTime = tuesday.minusSeconds(6 * 3600),
                title = "Delivery to Omicron Persei 8",
                ownerId = fryId,
                tags = arrayOf("delivery", "space-travel"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = tuesday.minusSeconds(5 * 3600),
                endTime = tuesday.minusSeconds(4 * 3600),
                title = "Fuel the ship",
                ownerId = fryId,
                tags = arrayOf("maintenance"),
            ),
        )

        // Wednesday (Mar 13) - Mixed tasks
        val wednesday = setBaseTime("2024-03-16", "03:30").minusSeconds(3 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusSeconds(9 * 3600),
                endTime = wednesday.minusSeconds(7 * 3600),
                title = "Attend crew meeting",
                ownerId = fryId,
                tags = arrayOf("meeting", "delivery"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = wednesday.minusSeconds(6 * 3600),
                endTime = wednesday.minusSeconds(3 * 3600 + 1800),
                title = "Help Bender with brewery plans",
                ownerId = fryId,
                tags = arrayOf("learning", "break"),
            ),
        )

        // Thursday (Mar 14) - Learning and development
        val thursday = setBaseTime("2024-03-16", "03:30").minusSeconds(2 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = thursday.minusSeconds(7 * 3600),
                endTime = thursday.minusSeconds(5 * 3600),
                title = "Study holophonor",
                ownerId = fryId,
                tags = arrayOf("learning"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = thursday.minusSeconds(4 * 3600),
                endTime = thursday.minusSeconds(2 * 3600 + 1800),
                title = "Watch TV with Bender",
                ownerId = fryId,
                tags = arrayOf("break"),
            ),
        )

        // Friday (Mar 15) - Yesterday
        val friday = setBaseTime("2024-03-16", "03:30").minusSeconds(1 * 24 * 3600)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = friday.minusSeconds(8 * 3600),
                endTime = friday.minusSeconds(6 * 3600),
                title = "Emergency delivery to Mars Vegas",
                ownerId = fryId,
                tags = arrayOf("delivery", "space-travel"),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = friday.minusSeconds(5 * 3600),
                endTime = friday.minusSeconds(3 * 3600),
                title = "Ship repairs after crash",
                ownerId = fryId,
                tags = arrayOf("maintenance"),
            ),
        )

        // Saturday (Mar 16) - Today, with one completed entry and one active (running) entry
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = setBaseTime("2024-03-16", "03:30").minusSeconds(2 * 3600),
                endTime = setBaseTime("2024-03-16", "03:30").minusSeconds(1 * 3600),
                title = "Morning coffee with Leela",
                ownerId = fryId,
                tags = arrayOf("break"),
            ),
        )

        // Active (running) task - no end time
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = setBaseTime("2024-03-16", "03:30").minusSeconds(30 * 60), // Started 30 minutes ago
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

        // Keep default viewport (1280x720) for login page to center the card vertically
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

        // Get actual content height and set viewport accordingly
        val contentHeight = page.evaluate("document.documentElement.scrollHeight") as Int
        page.setViewportSize(1280, contentHeight)

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

        // Get actual content height and set viewport accordingly
        val contentHeight = page.evaluate("document.documentElement.scrollHeight") as Int
        page.setViewportSize(1280, contentHeight)

        // Take screenshot
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/time-logs-page.png"),
                ).setFullPage(false),
        )
    }

    @Test
    fun `generate settings page screenshot`() {
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

        // Get actual content height and set viewport accordingly
        val contentHeight = page.evaluate("document.documentElement.scrollHeight") as Int
        page.setViewportSize(1280, contentHeight)

        // Take screenshot showing both tags statistics and API token
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/settings-page.png"),
                ).setFullPage(false),
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

        // Get actual content height and set viewport accordingly
        val contentHeight = page.evaluate("document.documentElement.scrollHeight") as Int
        page.setViewportSize(1280, contentHeight)

        // Take screenshot
        page.screenshot(
            Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("docs/images/profile-page.png"),
                ).setFullPage(false),
        )
    }
}
