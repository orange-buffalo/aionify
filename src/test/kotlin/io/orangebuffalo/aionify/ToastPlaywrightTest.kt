package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * Playwright tests for toast notification functionality.
 * Tests that toasts can be closed manually, auto-close for success messages,
 * and that new messages replace old ones.
 */
@MicronautTest(transactional = false)
class ToastPlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private lateinit var adminUser: User
    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Create admin user
        adminUser = testDatabaseSupport.insert(
            User.create(
                userName = "admin",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Admin User",
                isAdmin = true,
                locale = java.util.Locale.ENGLISH,
                languageCode = "en"
            )
        )

        // Create regular user
        regularUser = testDatabaseSupport.insert(
            User.create(
                userName = "testuser",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Test User",
                isAdmin = false,
                locale = java.util.Locale.ENGLISH,
                languageCode = "en"
            )
        )
    }

    @Test
    fun `should display success toast and auto-close after 10 seconds`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Change username to trigger success
        val usernameInput = page.locator("[data-testid='username-input']")
        usernameInput.fill("newusername")

        // Click save button
        page.locator("[data-testid='save-button']").click()

        // Verify success toast is displayed
        val toast = page.locator("[data-testid='toast-message']")
        assertThat(toast).isVisible()
        assertThat(toast).containsText("User updated successfully")

        // Advance time by 10 seconds to trigger auto-close
        page.clock().fastForward(10000)

        // Verify toast is auto-closed
        assertThat(toast).not().isVisible()
    }

    @Test
    fun `should display success toast and allow manual close before auto-close`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Change username to trigger success
        val usernameInput = page.locator("[data-testid='username-input']")
        usernameInput.fill("newusername2")

        // Click save button
        page.locator("[data-testid='save-button']").click()

        // Verify success toast is displayed
        val toast = page.locator("[data-testid='toast-message']")
        assertThat(toast).isVisible()
        assertThat(toast).containsText("User updated successfully")

        // Click the close button before auto-close
        val closeButton = page.locator("[data-testid='toast-message-close']")
        assertThat(closeButton).isVisible()
        closeButton.click()

        // Verify toast is closed immediately
        assertThat(toast).not().isVisible()
    }

    @Test
    fun `should display error toast from server and allow manual close`() {
        // Create another user to trigger username conflict error
        testDatabaseSupport.insert(
            User.create(
                userName = "existinguser",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Existing User",
                isAdmin = false,
                locale = java.util.Locale.ENGLISH,
                languageCode = "en"
            )
        )

        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Try to change username to existing one
        val usernameInput = page.locator("[data-testid='username-input']")
        usernameInput.fill("existinguser")

        // Click save button
        page.locator("[data-testid='save-button']").click()

        // Verify error toast is displayed
        val toast = page.locator("[data-testid='toast-message']")
        assertThat(toast).isVisible()
        assertThat(toast).containsText("Username already exists")

        // Click the close button
        val closeButton = page.locator("[data-testid='toast-message-close']")
        assertThat(closeButton).isVisible()
        closeButton.click()

        // Verify toast is closed
        assertThat(toast).not().isVisible()
    }

    @Test
    fun `should not auto-close error toast`() {
        // Create another user to trigger error
        testDatabaseSupport.insert(
            User.create(
                userName = "existinguser2",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Existing User 2",
                isAdmin = false,
                locale = java.util.Locale.ENGLISH,
                languageCode = "en"
            )
        )

        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Try to change username to existing one
        val usernameInput = page.locator("[data-testid='username-input']")
        usernameInput.fill("existinguser2")

        // Click save button
        page.locator("[data-testid='save-button']").click()

        // Verify error toast is displayed
        val toast = page.locator("[data-testid='toast-message']")
        assertThat(toast).isVisible()
        assertThat(toast).containsText("Username already exists")

        // Advance time by 15 seconds (more than auto-close timeout for success)
        page.clock().fastForward(15000)

        // Verify error toast is still visible (should not auto-close)
        assertThat(toast).isVisible()
        assertThat(toast).containsText("Username already exists")
    }

    @Test
    fun `should replace success toast with new success toast`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Change username to trigger first success
        val usernameInput = page.locator("[data-testid='username-input']")
        usernameInput.fill("firstchange")

        // Click save button
        page.locator("[data-testid='save-button']").click()

        // Verify first success toast is displayed
        val toast = page.locator("[data-testid='toast-message']")
        assertThat(toast).isVisible()
        assertThat(toast).containsText("User updated successfully")

        // Change username again to trigger second success
        usernameInput.fill("secondchange")
        page.locator("[data-testid='save-button']").click()

        // Verify the toast is still visible with the same message (both are success)
        assertThat(toast).isVisible()
        assertThat(toast).containsText("User updated successfully")
    }
}
