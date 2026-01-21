package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.ActivationTokenRepository
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt

/**
 * Playwright tests for the create user page functionality.
 */
@MicronautTest(transactional = false)
class CreateUserPagePlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var activationTokenRepository: ActivationTokenRepository

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private val testPassword = "testPassword123"
    private lateinit var adminUser: User

    @BeforeEach
    fun setupTestData() {
        // Create admin user
        adminUser =
            testDatabaseSupport.insert(
                User.create(
                    userName = "admin",
                    passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                    greeting = "Admin User",
                    isAdmin = true,
                    locale = java.util.Locale.US,
                ),
            )
    }

    @Test
    fun `should display create user page`() {
        loginViaToken("/admin/users/create", adminUser, testAuthSupport)

        // Verify create page is displayed
        val createPage = page.locator("[data-testid='create-user-page']")
        assertThat(createPage).isVisible()

        // Verify form fields are present
        val usernameInput = page.locator("[data-testid='username-input']")
        assertThat(usernameInput).isVisible()

        val greetingInput = page.locator("[data-testid='greeting-input']")
        assertThat(greetingInput).isVisible()

        val userTypeSelect = page.locator("[data-testid='user-type-select']")
        assertThat(userTypeSelect).isVisible()
        // Default value should be "Regular User"
        assertThat(userTypeSelect).containsText("Regular User")

        val createButton = page.locator("[data-testid='create-button']")
        assertThat(createButton).isVisible()
        assertThat(createButton).hasText("Create User")
    }

    @Test
    fun `should navigate back to users page when back button is clicked`() {
        loginViaToken("/admin/users/create", adminUser, testAuthSupport)

        // Click back button
        page.locator("[data-testid='back-button']").click()

        // Verify navigation to users page
        page.waitForURL("**/admin/users")

        val usersPage = page.locator("[data-testid='users-page']")
        assertThat(usersPage).isVisible()
    }

    @Test
    fun `should create regular user and navigate to edit page with success message`() {
        loginViaToken("/admin/users/create", adminUser, testAuthSupport)

        // Fill in the form
        page.locator("[data-testid='username-input']").fill("newuser")
        page.locator("[data-testid='greeting-input']").fill("New User")

        // Regular user is already selected by default in the dropdown

        // Click create button
        page.locator("[data-testid='create-button']").click()

        // Wait for navigation to edit page
        page.waitForURL("**/admin/users/*")

        // Verify we're on the edit page
        val editPage = page.locator("[data-testid='edit-user-page']")
        assertThat(editPage).isVisible()

        // Verify success message is displayed
        val successMessage = page.locator("[data-testid='edit-user-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("User created successfully")

        // Verify activation token is displayed
        val activationUrl = page.locator("[data-testid='activation-url']")
        assertThat(activationUrl).isVisible()
        // Just verify it has a value and contains activation link pattern
        val urlValue = activationUrl.inputValue()
        assert(urlValue.contains("/activate?token=")) { "Activation URL should contain '/activate?token=', but was: $urlValue" }

        // Verify user was created in database
        val createdUser = userRepository.findByUserName("newuser")
        assert(createdUser.isPresent) { "User should be created in database" }
        assert(createdUser.get().greeting == "New User") { "User greeting should be 'New User'" }
        assert(!createdUser.get().isAdmin) { "User should not be admin" }

        // Verify activation token was created
        val token = activationTokenRepository.findByUserId(createdUser.get().id!!).orElse(null)
        assert(token != null) { "Activation token should be created" }
    }

    @Test
    fun `should create admin user and navigate to edit page`() {
        loginViaToken("/admin/users/create", adminUser, testAuthSupport)

        // Fill in the form
        page.locator("[data-testid='username-input']").fill("newadmin")
        page.locator("[data-testid='greeting-input']").fill("New Admin")

        // Select admin user type from dropdown
        page.locator("[data-testid='user-type-select']").click()
        page.locator("[data-testid='user-type-admin']").click()

        // Click create button
        page.locator("[data-testid='create-button']").click()

        // Wait for navigation to edit page
        page.waitForURL("**/admin/users/*")

        // Verify we're on the edit page
        val editPage = page.locator("[data-testid='edit-user-page']")
        assertThat(editPage).isVisible()

        // Verify success message is displayed
        val successMessage = page.locator("[data-testid='edit-user-success']")
        assertThat(successMessage).isVisible()

        // Verify user was created in database with admin flag
        val createdUser = userRepository.findByUserName("newadmin")
        assert(createdUser.isPresent) { "User should be created in database" }
        assert(createdUser.get().isAdmin) { "User should be admin" }
    }

    @Test
    fun `should show error when username already exists`() {
        // Create a user with the same username first
        testDatabaseSupport.insert(
            User.create(
                userName = "existinguser",
                passwordHash = BCrypt.hashpw(testPassword, BCrypt.gensalt()),
                greeting = "Existing User",
                isAdmin = false,
                locale = java.util.Locale.US,
            ),
        )

        loginViaToken("/admin/users/create", adminUser, testAuthSupport)

        // Fill in the form with existing username
        page.locator("[data-testid='username-input']").fill("existinguser")
        page.locator("[data-testid='greeting-input']").fill("Another User")

        // Click create button
        page.locator("[data-testid='create-button']").click()

        // Verify error message is displayed
        val errorMessage = page.locator("[data-testid='create-user-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Username already exists")

        // Verify we're still on the create page
        val createPage = page.locator("[data-testid='create-user-page']")
        assertThat(createPage).isVisible()
    }

    @Test
    fun `should show error when username is blank`() {
        loginViaToken("/admin/users/create", adminUser, testAuthSupport)

        // Fill in greeting but leave username blank
        page.locator("[data-testid='greeting-input']").fill("Test User")

        // Click create button
        page.locator("[data-testid='create-button']").click()

        // Verify error message is displayed
        val errorMessage = page.locator("[data-testid='create-user-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Username cannot be blank")
    }

    @Test
    fun `should show error when greeting is blank`() {
        loginViaToken("/admin/users/create", adminUser, testAuthSupport)

        // Fill in username but leave greeting blank
        page.locator("[data-testid='username-input']").fill("testuser")

        // Click create button
        page.locator("[data-testid='create-button']").click()

        // Verify error message is displayed
        val errorMessage = page.locator("[data-testid='create-user-error']")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).containsText("Greeting cannot be blank")
    }

    @Test
    fun `should navigate to create user page from users page via create button`() {
        loginViaToken("/admin/users", adminUser, testAuthSupport)

        // Verify create button is visible
        val createButton = page.locator("[data-testid='create-user-button']")
        assertThat(createButton).isVisible()
        assertThat(createButton).hasText("Create User")

        // Click create button
        createButton.click()

        // Verify navigation to create page
        page.waitForURL("**/admin/users/create")

        val createPage = page.locator("[data-testid='create-user-page']")
        assertThat(createPage).isVisible()
    }
}
