package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@MicronautTest
class FrontendPlaywrightTest : PlaywrightTestBase() {

    @Test
    fun `should display login page with title and form`() {
        page.navigate("/")

        // Verify the page title
        assertThat(page).hasTitle("Aionify - Time Tracking")

        // Verify the login title is present
        val loginTitle = page.locator("[data-testid='login-title']")
        assertThat(loginTitle).isVisible()
        assertThat(loginTitle).hasText("Login")

        // Verify the login button is present
        val loginButton = page.locator("[data-testid='login-button']")
        assertThat(loginButton).isVisible()
    }

    @Test
    fun `should have properly styled login components`() {
        page.navigate("/")

        // Verify the login page container is present
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()

        // Verify the login form elements are visible (styling details are tested visually)
        val usernameInput = page.locator("[data-testid='username-input']")
        assertThat(usernameInput).isVisible()
        
        val loginButton = page.locator("[data-testid='login-button']")
        assertThat(loginButton).isVisible()
    }
}
