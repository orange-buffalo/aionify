package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.ActivationToken
import io.orangebuffalo.aionify.domain.ActivationTokenRepository
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Playwright tests for the edit user page functionality.
 */
@MicronautTest
class EditUserPagePlaywrightTest : PlaywrightTestBase() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var activationTokenRepository: ActivationTokenRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private lateinit var adminUser: User
    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        // Create admin user
        adminUser = transactionHelper.inTransaction {
            userRepository.save(
                User.create(
                    userName = "admin",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Admin User",
                    isAdmin = true,
                    locale = java.util.Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }

        // Create regular user
        regularUser = transactionHelper.inTransaction {
            userRepository.save(
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
    }

    @Test
    fun `should display edit user page with user information`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Verify edit page is displayed
        val editPage = page.locator("[data-testid='edit-user-page']")
        assertThat(editPage).isVisible()

        // Verify title
        val title = page.locator("[data-testid='edit-user-title']")
        assertThat(title).isVisible()
        assertThat(title).hasText("Edit User")

        // Verify username input is populated
        val usernameInput = page.locator("[data-testid='username-input']")
        assertThat(usernameInput).isVisible()
        assertThat(usernameInput).hasValue("testuser")

        // Verify user info is displayed
        val greeting = page.locator("[data-testid='user-greeting']")
        assertThat(greeting).hasText("Test User")

        val userType = page.locator("[data-testid='user-type']")
        assertThat(userType).hasText("Regular User")
    }

    @Test
    fun `should navigate back to users page when back button is clicked`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Click back button
        page.locator("[data-testid='back-button']").click()

        // Verify navigation to users page
        page.waitForURL("**/admin/users")
        
        // Verify users page is displayed
        val usersPage = page.locator("[data-testid='users-page']")
        assertThat(usersPage).isVisible()
    }

    @Test
    fun `should update username successfully`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Change username
        val usernameInput = page.locator("[data-testid='username-input']")
        usernameInput.fill("newtestuser")

        // Click save button
        page.locator("[data-testid='save-button']").click()

        // Verify success message is displayed
        val successMessage = page.locator("[data-testid='edit-user-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("User updated successfully")

        // Verify username is updated in database
        val updatedUser = userRepository.findById(requireNotNull(regularUser.id)).get()
        assert(updatedUser.userName == "newtestuser") { "Username should be updated" }
    }

    @Test
    fun `should show error when username already exists`() {
        // Create another user with a different username
        val anotherUser = transactionHelper.inTransaction {
            userRepository.save(
                User.create(
                    userName = "existinguser",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Existing User",
                    isAdmin = false,
                    locale = java.util.Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }

        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Try to change username to existing one
        val usernameInput = page.locator("[data-testid='username-input']")
        usernameInput.fill("existinguser")

        // Click save button
        page.locator("[data-testid='save-button']").click()

        // Verify error message is displayed
        val errorMessage = page.locator("[data-testid='edit-user-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Username already exists")

        // Verify username is not updated in database
        val notUpdatedUser = userRepository.findById(requireNotNull(regularUser.id)).get()
        assert(notUpdatedUser.userName == "testuser") { "Username should not be updated" }
    }

    @Test
    fun `should disable save button when username is unchanged`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Verify save button is disabled initially (username not changed)
        val saveButton = page.locator("[data-testid='save-button']")
        assertThat(saveButton).isDisabled()

        // Change username
        val usernameInput = page.locator("[data-testid='username-input']")
        usernameInput.fill("newtestuser")

        // Verify save button is now enabled
        assertThat(saveButton).isEnabled()

        // Change back to original username
        usernameInput.fill("testuser")

        // Verify save button is disabled again
        assertThat(saveButton).isDisabled()
    }

    @Test
    fun `should display activation token when it exists`() {
        // Create activation token for the user
        val activationToken = transactionHelper.inTransaction {
            activationTokenRepository.save(
                ActivationToken(
                    userId = requireNotNull(regularUser.id),
                    token = "test-activation-token-123",
                    expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
                )
            )
        }

        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Verify activation URL is displayed
        val activationUrl = page.locator("[data-testid='activation-url']")
        assertThat(activationUrl).isVisible()
        assertThat(activationUrl).hasValue("http://localhost:${page.url().split(":")[2].split("/")[0]}/activate?token=test-activation-token-123")

        // Verify activation note is displayed
        val activationNote = page.locator("[data-testid='activation-note']")
        assertThat(activationNote).isVisible()
        assertThat(activationNote).containsText("Share this URL with the user")

        // Verify regenerate button is displayed
        val regenerateButton = page.locator("[data-testid='regenerate-token-button']")
        assertThat(regenerateButton).isVisible()
    }

    @Test
    fun `should show message when no activation token exists`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Verify no activation token message is displayed
        val noTokenMessage = page.locator("[data-testid='no-activation-token']")
        assertThat(noTokenMessage).isVisible()
        assertThat(noTokenMessage).containsText("No valid activation token")

        // Verify generate button is displayed
        val generateButton = page.locator("[data-testid='generate-token-button']")
        assertThat(generateButton).isVisible()
    }

    @Test
    fun `should regenerate activation token successfully`() {
        // Create activation token for the user
        val oldToken = transactionHelper.inTransaction {
            activationTokenRepository.save(
                ActivationToken(
                    userId = requireNotNull(regularUser.id),
                    token = "old-activation-token",
                    expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
                )
            )
        }

        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Click regenerate button
        page.locator("[data-testid='regenerate-token-button']").click()

        // Verify success message is displayed
        val successMessage = page.locator("[data-testid='edit-user-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Activation token regenerated successfully")

        // Verify activation URL is updated (should no longer contain old token)
        val activationUrl = page.locator("[data-testid='activation-url']")
        assertThat(activationUrl).isVisible()
        val urlValue = activationUrl.inputValue()
        assert(!urlValue.contains("old-activation-token")) { "Old token should not be in URL" }
        assert(urlValue.contains("/activate?token=")) { "New token should be in URL" }
    }

    @Test
    fun `should generate activation token when none exists`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Verify no activation token exists initially
        val noTokenMessage = page.locator("[data-testid='no-activation-token']")
        assertThat(noTokenMessage).isVisible()

        // Click generate button
        page.locator("[data-testid='generate-token-button']").click()

        // Verify success message is displayed
        val successMessage = page.locator("[data-testid='edit-user-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Activation token regenerated successfully")

        // Verify activation URL is now displayed
        val activationUrl = page.locator("[data-testid='activation-url']")
        assertThat(activationUrl).isVisible()
        val urlValue = activationUrl.inputValue()
        assert(urlValue.contains("/activate?token=")) { "Token should be in URL" }

        // Verify token was saved in database
        val token = activationTokenRepository.findByUserId(requireNotNull(regularUser.id))
        assert(token.isPresent) { "Token should be saved in database" }
    }

    @Test
    fun `should display user info section with correct information`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Verify user info section is displayed
        val userInfoTitle = page.locator("[data-testid='user-info-title']")
        assertThat(userInfoTitle).isVisible()
        assertThat(userInfoTitle).hasText("User Information")

        // Verify greeting
        val greeting = page.locator("[data-testid='user-greeting']")
        assertThat(greeting).hasText("Test User")

        // Verify user type
        val userType = page.locator("[data-testid='user-type']")
        assertThat(userType).hasText("Regular User")

        // Verify note is displayed
        val note = page.locator("[data-testid='user-info-note']")
        assertThat(note).isVisible()
        assertThat(note).containsText("User profile settings")
    }

    @Test
    fun `should display limitations section`() {
        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Verify limitations section is displayed
        val limitationsTitle = page.locator("[data-testid='limitations-title']")
        assertThat(limitationsTitle).isVisible()
        assertThat(limitationsTitle).hasText("Limitations")

        // Verify limitations list is displayed
        val limitationsList = page.locator("[data-testid='limitations-list']")
        assertThat(limitationsList).isVisible()
        assertThat(limitationsList).containsText("User type")
        assertThat(limitationsList).containsText("Passwords for other users cannot be changed")
    }

    @Test
    fun `should display admin user type for admin users`() {
        loginViaToken("/admin/users/${adminUser.id}", adminUser, testAuthSupport)

        // Verify user type shows Admin
        val userType = page.locator("[data-testid='user-type']")
        assertThat(userType).hasText("Admin")
    }

    @Test
    fun `should navigate to edit page from users page`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Click actions menu for regular user
        page.locator("[data-testid='user-actions-testuser']").click()

        // Click edit option
        page.locator("[data-testid='user-edit-testuser']").click()

        // Verify navigation to edit page
        page.waitForURL("**/admin/users/${regularUser.id}")

        // Verify edit page is displayed
        val editPage = page.locator("[data-testid='edit-user-page']")
        assertThat(editPage).isVisible()

        // Verify correct user is being edited
        val usernameInput = page.locator("[data-testid='username-input']")
        assertThat(usernameInput).hasValue("testuser")
    }

    @Test
    fun `should not show expired activation token`() {
        // Create expired activation token for the user
        val expiredToken = transactionHelper.inTransaction {
            activationTokenRepository.save(
                ActivationToken(
                    userId = requireNotNull(regularUser.id),
                    token = "expired-token",
                    expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
                )
            )
        }

        loginViaToken("/admin/users/${regularUser.id}", adminUser, testAuthSupport)

        // Verify no activation token message is displayed (expired token should not be shown)
        val noTokenMessage = page.locator("[data-testid='no-activation-token']")
        assertThat(noTokenMessage).isVisible()

        // Verify activation URL is not displayed
        val activationUrl = page.locator("[data-testid='activation-url']")
        assertThat(activationUrl).not().isVisible()
    }
}
