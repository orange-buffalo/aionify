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
class TopNavigationPlaywrightTest : PlaywrightTestBase() {

    @TestHTTPResource("/login")
    lateinit var loginUrl: URL

    @Inject
    lateinit var userRepository: UserRepository

    private val testPassword = "testPassword123"
    private val regularUserName = "navtestuser"
    private val regularUserGreeting = "Nav Test User"
    private val adminUserName = "navtestadmin"
    private val adminUserGreeting = "Nav Test Admin"

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

        // Create an admin user for testing if not exists
        if (userRepository.findByUserName(adminUserName) == null) {
            userRepository.insert(
                User(
                    userName = adminUserName,
                    passwordHash = BcryptUtil.bcryptHash(testPassword),
                    greeting = adminUserGreeting,
                    isAdmin = true,
                    locale = Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }
    }

    private fun loginAsRegularUser() {
        page.navigate(loginUrl.toString())
        page.locator("[data-testid='username-input']").fill(regularUserName)
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL("**/portal")
    }

    private fun loginAsAdmin() {
        page.navigate(loginUrl.toString())
        page.locator("[data-testid='username-input']").fill(adminUserName)
        page.locator("[data-testid='password-input']").fill(testPassword)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL("**/admin")
    }

    @Test
    fun `user portal should display top navigation with user-specific menu items`() {
        loginAsRegularUser()

        // Verify top nav is present
        val topNav = page.locator("[data-testid='top-nav']")
        topNav.waitFor()
        assertTrue(topNav.isVisible, "Top navigation should be visible")

        // Verify logo is present
        val logo = page.locator("[data-testid='nav-logo']")
        assertTrue(logo.isVisible, "Logo should be visible")
        assertEquals("Aionify", logo.textContent())

        // Verify user-specific menu items are present (desktop view)
        val timeEntry = page.locator("[data-testid='nav-item-time-entry']")
        val calendar = page.locator("[data-testid='nav-item-calendar']")
        val reports = page.locator("[data-testid='nav-item-reports']")

        assertTrue(timeEntry.isVisible, "Time Entry menu item should be visible")
        assertTrue(calendar.isVisible, "Calendar menu item should be visible")
        assertTrue(reports.isVisible, "Reports menu item should be visible")
    }

    @Test
    fun `admin portal should display top navigation with admin-specific menu items`() {
        loginAsAdmin()

        // Verify top nav is present
        val topNav = page.locator("[data-testid='top-nav']")
        topNav.waitFor()
        assertTrue(topNav.isVisible, "Top navigation should be visible")

        // Verify logo is present
        val logo = page.locator("[data-testid='nav-logo']")
        assertTrue(logo.isVisible, "Logo should be visible")
        assertEquals("Aionify", logo.textContent())

        // Verify admin-specific menu items are present (desktop view)
        val users = page.locator("[data-testid='nav-item-users']")
        val reports = page.locator("[data-testid='nav-item-reports']")
        val settings = page.locator("[data-testid='nav-item-settings']")

        assertTrue(users.isVisible, "Users menu item should be visible")
        assertTrue(reports.isVisible, "Reports menu item should be visible")
        assertTrue(settings.isVisible, "Settings menu item should be visible")
    }

    @Test
    fun `admin portal should NOT show user-specific menu items`() {
        loginAsAdmin()

        // Wait for top nav to be visible first
        val topNav = page.locator("[data-testid='top-nav']")
        topNav.waitFor()

        // Verify admin-specific items are there
        val users = page.locator("[data-testid='nav-item-users']")
        users.waitFor()
        assertTrue(users.isVisible, "Users menu item should be visible for admin")

        // Verify user-specific items are NOT present
        val timeEntry = page.locator("[data-testid='nav-item-time-entry']")
        val calendar = page.locator("[data-testid='nav-item-calendar']")

        assertEquals(0, timeEntry.count(), "Time Entry menu item should NOT be visible for admin")
        assertEquals(0, calendar.count(), "Calendar menu item should NOT be visible for admin")
    }

    @Test
    fun `user portal should NOT show admin-specific menu items`() {
        loginAsRegularUser()

        // Wait for top nav to be visible first
        val topNav = page.locator("[data-testid='top-nav']")
        topNav.waitFor()

        // Verify user-specific items are there
        val timeEntry = page.locator("[data-testid='nav-item-time-entry']")
        timeEntry.waitFor()
        assertTrue(timeEntry.isVisible, "Time Entry menu item should be visible for user")

        // Verify admin-specific items are NOT present
        val users = page.locator("[data-testid='nav-item-users']")
        val settings = page.locator("[data-testid='nav-item-settings']")

        assertEquals(0, users.count(), "Users menu item should NOT be visible for regular user")
        assertEquals(0, settings.count(), "Settings menu item should NOT be visible for regular user")
    }

    @Test
    fun `profile menu should display user greeting`() {
        loginAsRegularUser()

        // Open profile menu
        page.locator("[data-testid='profile-menu-button']").click()

        // Verify profile dropdown appears with greeting
        val profileDropdown = page.locator("[data-testid='profile-menu-dropdown']")
        profileDropdown.waitFor()
        assertTrue(profileDropdown.isVisible, "Profile dropdown should be visible")

        val greeting = page.locator("[data-testid='profile-greeting']")
        assertTrue(greeting.isVisible, "Greeting should be visible")
        assertEquals(regularUserGreeting, greeting.textContent())
    }

    @Test
    fun `profile menu should show logout option`() {
        loginAsRegularUser()

        // Open profile menu
        page.locator("[data-testid='profile-menu-button']").click()

        // Verify logout button is in the dropdown
        val logoutButton = page.locator("[data-testid='logout-button']")
        logoutButton.waitFor()
        assertTrue(logoutButton.isVisible, "Logout button should be visible in profile menu")
    }

    @Test
    fun `logout from profile menu should redirect to login and show welcome back`() {
        loginAsRegularUser()

        // Verify user portal is displayed
        val userPortal = page.locator("[data-testid='user-portal']")
        userPortal.waitFor()
        assertTrue(userPortal.isVisible, "User portal should be visible")

        // Open profile menu and logout
        page.locator("[data-testid='profile-menu-button']").click()
        val logoutButton = page.locator("[data-testid='logout-button']")
        logoutButton.waitFor()
        logoutButton.click()

        // Wait for redirect to login
        page.waitForURL("**/login")

        // Verify login page is displayed
        val loginPage = page.locator("[data-testid='login-page']")
        loginPage.waitFor()
        assertTrue(loginPage.isVisible, "Login page should be visible")

        // Verify welcome back message shows the user's greeting
        val welcomeBackMessage = page.locator("[data-testid='welcome-back-message']")
        welcomeBackMessage.waitFor()
        assertTrue(welcomeBackMessage.isVisible, "Welcome back message should be visible")
        assertTrue(
            welcomeBackMessage.textContent()?.contains(regularUserGreeting) == true,
            "Welcome message should contain user's greeting"
        )
    }

    @Test
    fun `mobile menu button should be visible on narrow screens`() {
        loginAsRegularUser()

        // Set viewport to mobile width
        page.setViewportSize(375, 667)

        // Verify mobile menu button is visible
        val mobileMenuButton = page.locator("[data-testid='mobile-menu-button']")
        assertTrue(mobileMenuButton.isVisible, "Mobile menu button should be visible on narrow screens")

        // Verify desktop menu items are hidden
        val desktopMenu = page.locator("[data-testid='nav-menu-desktop']")
        assertFalse(desktopMenu.isVisible, "Desktop menu should be hidden on narrow screens")
    }

    @Test
    fun `mobile menu should show all navigation items when opened`() {
        loginAsRegularUser()

        // Set viewport to mobile width
        page.setViewportSize(375, 667)

        // Open mobile menu
        val mobileMenuButton = page.locator("[data-testid='mobile-menu-button']")
        mobileMenuButton.click()

        // Verify mobile dropdown appears
        val mobileDropdown = page.locator("[data-testid='mobile-menu-dropdown']")
        mobileDropdown.waitFor()
        assertTrue(mobileDropdown.isVisible, "Mobile menu dropdown should be visible")

        // Verify all user menu items are in mobile menu
        val timeEntry = page.locator("[data-testid='mobile-nav-item-time-entry']")
        val calendar = page.locator("[data-testid='mobile-nav-item-calendar']")
        val reports = page.locator("[data-testid='mobile-nav-item-reports']")

        assertTrue(timeEntry.isVisible, "Time Entry should be in mobile menu")
        assertTrue(calendar.isVisible, "Calendar should be in mobile menu")
        assertTrue(reports.isVisible, "Reports should be in mobile menu")
    }

    @Test
    fun `admin mobile menu should show admin-specific items`() {
        loginAsAdmin()

        // Set viewport to mobile width
        page.setViewportSize(375, 667)

        // Open mobile menu
        val mobileMenuButton = page.locator("[data-testid='mobile-menu-button']")
        mobileMenuButton.click()

        // Verify mobile dropdown appears
        val mobileDropdown = page.locator("[data-testid='mobile-menu-dropdown']")
        mobileDropdown.waitFor()
        assertTrue(mobileDropdown.isVisible, "Mobile menu dropdown should be visible")

        // Verify admin menu items are in mobile menu
        val users = page.locator("[data-testid='mobile-nav-item-users']")
        val reports = page.locator("[data-testid='mobile-nav-item-reports']")
        val settings = page.locator("[data-testid='mobile-nav-item-settings']")

        assertTrue(users.isVisible, "Users should be in mobile menu")
        assertTrue(reports.isVisible, "Reports should be in mobile menu")
        assertTrue(settings.isVisible, "Settings should be in mobile menu")
    }
}
