package io.orangebuffalo.aionify

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertEquals("Aionify - Time Tracking", page.title())

        // Verify the login title is present
        val loginTitle = page.locator("[data-testid='login-title']")
        assertTrue(loginTitle.isVisible)
        assertEquals("Login", loginTitle.textContent())

        // Verify the login button is present
        val loginButton = page.locator("[data-testid='login-button']")
        assertTrue(loginButton.isVisible)
    }

    @Test
    fun `should have properly styled login components`() {
        page.navigate(url.toString())

        // Verify the login page container is present
        val loginPage = page.locator("[data-testid='login-page']")
        loginPage.waitFor()
        assertTrue(loginPage.isVisible)

        // Verify the card component is rendered
        val card = page.locator(".rounded-lg.border")
        card.waitFor()
        assertTrue(card.isVisible)
    }
}
