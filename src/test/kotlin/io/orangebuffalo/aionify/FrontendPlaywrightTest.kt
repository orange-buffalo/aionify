package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import java.net.URL

@QuarkusTest
class FrontendPlaywrightTest : PlaywrightTestBase() {

    @TestHTTPResource("/")
    lateinit var url: URL

    @Test
    fun `should display login page with title and form`() {
        page.navigate(url.toString())

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
        page.navigate(url.toString())

        // Verify the login page container is present
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()

        // Verify the card component is rendered
        val card = page.locator(".rounded-lg.border")
        assertThat(card).isVisible()
    }
}
