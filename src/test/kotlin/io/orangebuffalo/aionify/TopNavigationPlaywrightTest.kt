package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

@MicronautTest
class TopNavigationPlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var userRepository: UserRepository

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
        regularUser = transactionHelper.inTransaction {
            userRepository.save(
                User.create(
                    userName = regularUserName,
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = regularUserGreeting,
                    isAdmin = false,
                    locale = java.util.Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }

        adminUser = transactionHelper.inTransaction {
            userRepository.save(
                User.create(
                    userName = adminUserName,
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = adminUserGreeting,
                    isAdmin = true,
                    locale = java.util.Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }
    }

    private fun navigateToPortalViaToken() {
        loginViaToken("/portal", regularUser, testAuthSupport)
    }

    private fun navigateToAdminViaToken() {
        loginViaToken("/admin", adminUser, testAuthSupport)
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
        assertThat(logo).hasText("Aionify")

        // Verify user-specific menu items are present (desktop view)
        val timeEntry = page.locator("[data-testid='nav-item-time-entry']")
        val calendar = page.locator("[data-testid='nav-item-calendar']")
        val reports = page.locator("[data-testid='nav-item-reports']")

        assertThat(timeEntry).isVisible()
        assertThat(calendar).isVisible()
        assertThat(reports).isVisible()
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
        assertThat(logo).hasText("Aionify")

        // Verify admin-specific menu items are present (desktop view)
        val users = page.locator("[data-testid='nav-item-users']")
        val reports = page.locator("[data-testid='nav-item-reports']")
        val settings = page.locator("[data-testid='nav-item-settings']")

        assertThat(users).isVisible()
        assertThat(reports).isVisible()
        assertThat(settings).isVisible()
    }

    @Test
    fun `admin portal should NOT show user-specific menu items`() {
        navigateToAdminViaToken()

        // Wait for top nav to be visible first
        val topNav = page.locator("[data-testid='top-nav']")
        assertThat(topNav).isVisible()

        // Verify admin-specific items are there
        val users = page.locator("[data-testid='nav-item-users']")
        assertThat(users).isVisible()

        // Verify user-specific items are NOT present
        val timeEntry = page.locator("[data-testid='nav-item-time-entry']")
        val calendar = page.locator("[data-testid='nav-item-calendar']")

        assertThat(timeEntry).hasCount(0)
        assertThat(calendar).hasCount(0)
    }

    @Test
    fun `user portal should NOT show admin-specific menu items`() {
        navigateToPortalViaToken()

        // Wait for top nav to be visible first
        val topNav = page.locator("[data-testid='top-nav']")
        assertThat(topNav).isVisible()

        // Verify user-specific items are there
        val timeEntry = page.locator("[data-testid='nav-item-time-entry']")
        assertThat(timeEntry).isVisible()

        // Verify admin-specific items are NOT present
        val users = page.locator("[data-testid='nav-item-users']")
        val settings = page.locator("[data-testid='nav-item-settings']")

        assertThat(users).hasCount(0)
        assertThat(settings).hasCount(0)
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

        // Verify user portal is displayed
        val userPortal = page.locator("[data-testid='user-portal']")
        assertThat(userPortal).isVisible()

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
        val timeEntry = page.locator("[data-testid='mobile-nav-item-time-entry']")
        val calendar = page.locator("[data-testid='mobile-nav-item-calendar']")
        val reports = page.locator("[data-testid='mobile-nav-item-reports']")

        assertThat(timeEntry).isVisible()
        assertThat(calendar).isVisible()
        assertThat(reports).isVisible()
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
        val reports = page.locator("[data-testid='mobile-nav-item-reports']")
        val settings = page.locator("[data-testid='mobile-nav-item-settings']")

        assertThat(users).isVisible()
        assertThat(reports).isVisible()
        assertThat(settings).isVisible()
    }
}
