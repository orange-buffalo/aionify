package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * Playwright tests for the users overview page functionality.
 * Tests admin-only access, table display, pagination, and delete functionality.
 */
@MicronautTest
class UsersPagePlaywrightTest : PlaywrightTestBase() {

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
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
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
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        regularUser = transactionHelper.inTransaction {
            userRepository.save(
                User.create(
                    userName = "regularuser",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Regular User",
                    isAdmin = false,
                    locale = java.util.Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }
    }

    @Test
    fun `should display users page with table for admin user`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Verify users page is displayed
        val usersPage = page.locator("[data-testid='users-page']")
        assertThat(usersPage).isVisible()

        // Verify title
        val title = page.locator("[data-testid='users-title']")
        assertThat(title).isVisible()
        assertThat(title).hasText("Users")

        // Verify table is displayed
        val tableContainer = page.locator("[data-testid='users-table-container']")
        assertThat(tableContainer).isVisible()
    }

    @Test
    fun `should display all users in the table`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Verify admin user row
        val adminRow = page.locator("[data-testid='user-row-admin']")
        assertThat(adminRow).isVisible()

        val adminUsername = page.locator("[data-testid='user-username-admin']")
        assertThat(adminUsername).hasText("admin")

        val adminGreeting = page.locator("[data-testid='user-greeting-admin']")
        assertThat(adminGreeting).hasText("Admin User")

        val adminType = page.locator("[data-testid='user-type-admin']")
        assertThat(adminType).hasText("Admin")

        // Verify regular user row
        val regularUserRow = page.locator("[data-testid='user-row-regularuser']")
        assertThat(regularUserRow).isVisible()

        val regularUsername = page.locator("[data-testid='user-username-regularuser']")
        assertThat(regularUsername).hasText("regularuser")

        val regularGreeting = page.locator("[data-testid='user-greeting-regularuser']")
        assertThat(regularGreeting).hasText("Regular User")

        val regularType = page.locator("[data-testid='user-type-regularuser']")
        assertThat(regularType).hasText("Regular User")
    }

    @Test
    fun `should not show actions menu for own user`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Admin user should not have actions menu (cannot delete self)
        val adminActions = page.locator("[data-testid='user-actions-admin']")
        assertThat(adminActions).not().isVisible()

        // Regular user should have actions menu
        val regularUserActions = page.locator("[data-testid='user-actions-regularuser']")
        assertThat(regularUserActions).isVisible()
    }

    @Test
    fun `should display delete confirmation dialog when delete is clicked`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Click actions menu for regular user
        page.locator("[data-testid='user-actions-regularuser']").click()

        // Click delete option
        page.locator("[data-testid='user-delete-regularuser']").click()

        // Verify confirmation dialog is displayed
        val confirmDialog = page.locator("[data-testid='delete-confirm-regularuser']")
        assertThat(confirmDialog).isVisible()
        assertThat(confirmDialog).containsText("Are you sure you want to delete user regularuser?")

        // Verify cancel and confirm buttons are present
        val cancelButton = page.locator("[data-testid='delete-cancel-regularuser']")
        assertThat(cancelButton).isVisible()
        assertThat(cancelButton).hasText("Cancel")

        val confirmButton = page.locator("[data-testid='delete-confirm-button-regularuser']")
        assertThat(confirmButton).isVisible()
        assertThat(confirmButton).hasText("Delete")
    }

    @Test
    fun `should cancel deletion when cancel button is clicked`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Click actions menu and delete
        page.locator("[data-testid='user-actions-regularuser']").click()
        page.locator("[data-testid='user-delete-regularuser']").click()

        // Click cancel
        page.locator("[data-testid='delete-cancel-regularuser']").click()

        // Verify dialog is closed
        val confirmDialog = page.locator("[data-testid='delete-confirm-regularuser']")
        assertThat(confirmDialog).not().isVisible()

        // Verify user is still in the table
        val regularUserRow = page.locator("[data-testid='user-row-regularuser']")
        assertThat(regularUserRow).isVisible()
    }

    @Test
    fun `should delete user and show success message when confirmed`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Click actions menu and delete
        page.locator("[data-testid='user-actions-regularuser']").click()
        page.locator("[data-testid='user-delete-regularuser']").click()

        // Click confirm
        page.locator("[data-testid='delete-confirm-button-regularuser']").click()

        // Wait a bit for the API call to complete
        page.waitForTimeout(1000.0)

        // Check if there's an error message instead of success
        val errorMessage = page.locator("[data-testid='users-error']")
        if (errorMessage.isVisible) {
            val errorText = errorMessage.textContent()
            throw AssertionError("Expected success but got error: $errorText")
        }

        // Verify success message is displayed
        val successMessage = page.locator("[data-testid='users-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("User deleted successfully")

        // Verify user is removed from the table
        val regularUserRow = page.locator("[data-testid='user-row-regularuser']")
        assertThat(regularUserRow).not().isVisible()

        // Verify user is deleted from database
        val deletedUser = userRepository.findByUserName("regularuser")
        assert(deletedUser.isEmpty ) { "User should be deleted from database" }
    }

    @Test
    fun `should display pagination when there are more than 20 users`() {
        // Create 25 users to test pagination
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        transactionHelper.inTransaction {
            for (i in 1..25) {
                userRepository.save(
                    User.create(
                        userName = "user$i",
                        passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                        greeting = "User $i",
                        isAdmin = false,
                        locale = java.util.Locale.ENGLISH,
                        languageCode = "en"
                    )
                )
            }
        }

        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Verify pagination is displayed
        val pagination = page.locator("[data-testid='users-pagination']")
        assertThat(pagination).isVisible()

        // Verify pagination info
        val paginationInfo = page.locator("[data-testid='pagination-info']")
        assertThat(paginationInfo).isVisible()
        assertThat(paginationInfo).containsText("Page 1 of 2")

        // Verify showing text
        assertThat(pagination).containsText("Showing 1 to 20 of")
    }

    @Test
    fun `should navigate between pages using pagination controls`() {
        // Create 25 users to test pagination
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        transactionHelper.inTransaction {
            for (i in 1..25) {
                userRepository.save(
                    User.create(
                        userName = "user$i",
                        passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                        greeting = "User $i",
                        isAdmin = false,
                        locale = java.util.Locale.ENGLISH,
                        languageCode = "en"
                    )
                )
            }
        }

        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Verify we're on page 1
        val paginationInfo = page.locator("[data-testid='pagination-info']")
        assertThat(paginationInfo).containsText("Page 1 of 2")

        // Verify first user on page 1
        val firstUserOnPage1 = page.locator("[data-testid='user-username-admin']")
        assertThat(firstUserOnPage1).isVisible()

        // Verify previous button is disabled
        val previousButton = page.locator("[data-testid='pagination-previous']")
        assertThat(previousButton).isDisabled()

        // Click next button
        val nextButton = page.locator("[data-testid='pagination-next']")
        assertThat(nextButton).isEnabled()
        nextButton.click()

        // Verify we're on page 2 by checking pagination info
        assertThat(paginationInfo).containsText("Page 2 of 2")

        // Verify first user on page 1 is no longer visible (table content has changed)
        assertThat(firstUserOnPage1).not().isVisible()

        // Verify next button is disabled on last page
        assertThat(nextButton).isDisabled()

        // Verify previous button is enabled
        assertThat(previousButton).isEnabled()

        // Click previous to go back to page 1
        previousButton.click()

        // Verify we're back on page 1
        assertThat(paginationInfo).containsText("Page 1 of 2")

        // Verify first user on page 1 is visible again (table content has changed back)
        assertThat(firstUserOnPage1).isVisible()
    }

    @Test
    fun `should display correct number of users on each page`() {
        // Create 25 users
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        transactionHelper.inTransaction {
            for (i in 1..25) {
                userRepository.save(
                    User.create(
                        userName = "user$i",
                        passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                        greeting = "User $i",
                        isAdmin = false,
                        locale = java.util.Locale.ENGLISH,
                        languageCode = "en"
                    )
                )
            }
        }

        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Verify usernames on page 1 (should be first 20 users alphabetically)
        val page1Usernames = page.locator("tbody tr td:nth-child(1)").allTextContents()
        assert(page1Usernames.size == 20) { "Page 1 should have 20 rows, but has ${page1Usernames.size}" }
        // First user should be "admin" (alphabetically first)
        assert(page1Usernames[0] == "admin") { "First user should be admin, but was ${page1Usernames[0]}" }

        // Go to page 2
        page.locator("[data-testid='pagination-next']").click()

        // Verify pagination info updates
        val paginationInfo = page.locator("[data-testid='pagination-info']")
        assertThat(paginationInfo).containsText("Page 2 of 2")

        // Verify usernames on page 2 (should be remaining 7 users)
        val page2Usernames = page.locator("tbody tr td:nth-child(1)").allTextContents()
        assert(page2Usernames.size == 7) { "Page 2 should have 7 rows, but has ${page2Usernames.size}" }
        // Verify these are different users than page 1 (checking first username differs)
        assert(page2Usernames[0] != page1Usernames[0]) { "Page 2 should have different users than page 1" }
    }

    @Test
    fun `should display users sorted by username`() {
        // Create users with names that would be out of order if not sorted
        // Wrap in transaction to commit immediately and make visible to browser HTTP requests
        transactionHelper.inTransaction {
            userRepository.save(
                User.create(
                    userName = "zebra",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Zebra User",
                    isAdmin = false,
                    locale = java.util.Locale.ENGLISH,
                    languageCode = "en"
                )
            )
            userRepository.save(
                User.create(
                    userName = "apple",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Apple User",
                    isAdmin = false,
                    locale = java.util.Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }

        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Get all username cells in order
        val usernames = page.locator("tbody tr td:nth-child(1)").allTextContents()

        // Verify they are sorted alphabetically
        val expectedOrder = listOf("admin", "apple", "regularuser", "zebra")
        assert(usernames == expectedOrder) {
            "Users should be sorted by username. Expected: $expectedOrder, Got: $usernames"
        }
    }

    @Test
    fun `should hide actions button for current user`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Admin user logged in - verify they cannot delete themselves
        val adminActionsButton = page.locator("[data-testid='user-actions-admin']")
        assertThat(adminActionsButton).not().isVisible()

        // But they can delete other users
        val regularUserActionsButton = page.locator("[data-testid='user-actions-regularuser']")
        assertThat(regularUserActionsButton).isVisible()
    }
}
