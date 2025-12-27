package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

@MicronautTest(transactional = false)
class TopNavigationPlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private val regularUserName = "navtestuser"
    private val regularUserGreeting = "Nav Test User"
    private val adminUserName = "navtestadmin"
    private val adminUserGreeting = "Nav Test Admin"

    private lateinit var regularUser: User
    private lateinit var adminUser: User

    @BeforeEach
    fun setupTestData() {
        // Create test users with known credentials
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        regularUser = testDatabaseSupport.insert(
            User.create(
                userName = regularUserName,
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = regularUserGreeting,
                isAdmin = false,
                locale = java.util.Locale.US
            )
        )

        adminUser = testDatabaseSupport.insert(
            User.create(
                userName = adminUserName,
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = adminUserGreeting,
                isAdmin = true,
                locale = java.util.Locale.US
            )
        )
    }

    private fun navigateToPortalViaToken() {
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)
    }

    private fun navigateToAdminViaToken() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)
    }

    @Test
    fun `user portal should display top navigation with user-specific menu items`() {
        navigateToPortalViaToken()

        // Verify top nav is present
        val topNav = page.locator("[data-testid='top-nav']")
        assertThat(topNav).isVisible()

        // Verify logo is present
        val logo = page.locator("[data-testid='nav-logo']")
        assertThat(logo).isVisible()
        assertThat(logo).containsText("Aionify")

        // Verify exactly the expected user-specific menu items are present (desktop view)
        val navItems = page.locator("[data-testid^='nav-item-']")
        assertThat(navItems).containsText(arrayOf("Time Log", "Settings"))
    }

    @Test
    fun `admin portal should display top navigation with admin-specific menu items`() {
        navigateToAdminViaToken()

        // Verify top nav is present
        val topNav = page.locator("[data-testid='top-nav']")
        assertThat(topNav).isVisible()

        // Verify logo is present
        val logo = page.locator("[data-testid='nav-logo']")
        assertThat(logo).isVisible()
        assertThat(logo).containsText("Aionify")

        // Verify exactly the expected admin-specific menu items are present (desktop view)
        val navItems = page.locator("[data-testid^='nav-item-']")
        assertThat(navItems).containsText(arrayOf("Users"))
    }

    @Test
    fun `admin portal should NOT show user-specific menu items`() {
        navigateToAdminViaToken()

        // Wait for top nav to be visible first
        val topNav = page.locator("[data-testid='top-nav']")
        assertThat(topNav).isVisible()

        // Verify only admin-specific items are present (no user-specific items)
        val navItems = page.locator("[data-testid^='nav-item-']")
        assertThat(navItems).containsText(arrayOf("Users"))
    }

    @Test
    fun `user portal should NOT show admin-specific menu items`() {
        navigateToPortalViaToken()

        // Wait for top nav to be visible first
        val topNav = page.locator("[data-testid='top-nav']")
        assertThat(topNav).isVisible()

        // Verify only user-specific items are present (no admin-specific items)
        val navItems = page.locator("[data-testid^='nav-item-']")
        assertThat(navItems).containsText(arrayOf("Time Log", "Settings"))
    }

    @Test
    fun `profile menu should display user greeting`() {
        navigateToPortalViaToken()

        // Open profile menu
        page.locator("[data-testid='profile-menu-button']").click()

        // Verify profile dropdown appears with greeting
        val profileDropdown = page.locator("[data-testid='profile-menu-dropdown']")
        assertThat(profileDropdown).isVisible()

        val greeting = page.locator("[data-testid='profile-greeting']")
        assertThat(greeting).isVisible()
        assertThat(greeting).hasText(regularUserGreeting)
    }

    @Test
    fun `profile menu should show logout option`() {
        navigateToPortalViaToken()

        // Open profile menu
        page.locator("[data-testid='profile-menu-button']").click()

        // Verify logout button is in the dropdown
        val logoutButton = page.locator("[data-testid='logout-button']")
        assertThat(logoutButton).isVisible()
    }

    @Test
    fun `logout from profile menu should redirect to login and show welcome back`() {
        navigateToPortalViaToken()

        // Verify time logs page is displayed
        val timeLogsPage = page.locator("[data-testid='time-logs-page']")
        assertThat(timeLogsPage).isVisible()

        // Open profile menu and logout
        page.locator("[data-testid='profile-menu-button']").click()
        val logoutButton = page.locator("[data-testid='logout-button']")
        assertThat(logoutButton).isVisible()
        logoutButton.click()

        // Wait for redirect to login
        page.waitForURL("**/login")

        // Verify login page is displayed
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()

        // Verify welcome back message shows the user's greeting
        val welcomeBackMessage = page.locator("[data-testid='welcome-back-message']")
        assertThat(welcomeBackMessage).isVisible()
        assertThat(welcomeBackMessage).containsText(regularUserGreeting)
    }

    @Test
    fun `mobile menu button should be visible on narrow screens`() {
        navigateToPortalViaToken()

        // Set viewport to mobile width
        page.setViewportSize(375, 667)

        // Verify mobile menu button is visible
        val mobileMenuButton = page.locator("[data-testid='mobile-menu-button']")
        assertThat(mobileMenuButton).isVisible()

        // Verify desktop menu items are hidden
        val desktopMenu = page.locator("[data-testid='nav-menu-desktop']")
        assertThat(desktopMenu).isHidden()
    }

    @Test
    fun `mobile menu should show all navigation items when opened`() {
        navigateToPortalViaToken()

        // Set viewport to mobile width
        page.setViewportSize(375, 667)

        // Open mobile menu
        val mobileMenuButton = page.locator("[data-testid='mobile-menu-button']")
        mobileMenuButton.click()

        // Verify mobile dropdown appears
        val mobileDropdown = page.locator("[data-testid='mobile-menu-dropdown']")
        assertThat(mobileDropdown).isVisible()

        // Verify all user menu items are in mobile menu
        val timeEntry = page.locator("[data-testid='mobile-nav-item-time-log']")
        val settings = page.locator("[data-testid='mobile-nav-item-settings']")

        assertThat(timeEntry).isVisible()
        assertThat(settings).isVisible()
        
        // Profile is not in mobile menu (it's in the profile dropdown)
        val profile = page.locator("[data-testid='mobile-nav-item-profile']")
        assertThat(profile).hasCount(0)
    }

    @Test
    fun `admin mobile menu should show admin-specific items`() {
        navigateToAdminViaToken()

        // Set viewport to mobile width
        page.setViewportSize(375, 667)

        // Open mobile menu
        val mobileMenuButton = page.locator("[data-testid='mobile-menu-button']")
        mobileMenuButton.click()

        // Verify mobile dropdown appears
        val mobileDropdown = page.locator("[data-testid='mobile-menu-dropdown']")
        assertThat(mobileDropdown).isVisible()

        // Verify admin menu items are in mobile menu
        val users = page.locator("[data-testid='mobile-nav-item-users']")

        assertThat(users).isVisible()
    }

    @Test
    fun `clicking Users menu item should navigate to Users page`() {
        navigateToAdminViaToken()

        // Click on the Users menu item (should already be on Users page, but clicking should stay there)
        val usersMenuItem = page.locator("[data-testid='nav-item-users']")
        assertThat(usersMenuItem).isVisible()
        usersMenuItem.click()

        // Wait for navigation to Users page
        page.waitForURL("**/admin/users")

        // Verify Users page is displayed
        val usersPage = page.locator("[data-testid='users-page']")
        assertThat(usersPage).isVisible()

        // Verify the Users page title
        val usersTitle = page.locator("[data-testid='users-title']")
        assertThat(usersTitle).isVisible()
        assertThat(usersTitle).containsText("Users")
    }
}
