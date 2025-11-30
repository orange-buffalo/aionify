package org.aionify

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URL

@QuarkusTest
class FrontendPlaywrightTest {

    companion object {
        @JvmField
        @RegisterExtension
        val playwright = PlaywrightTestSupport()
    }

    @TestHTTPResource("/")
    lateinit var url: URL

    @Test
    fun `should display welcome page with title and button`() {
        playwright.page.navigate(url.toString())

        // Verify the page title
        assertEquals("Aionify - Time Tracking", playwright.page.title())

        // Verify the welcome title is present
        val welcomeTitle = playwright.page.locator("[data-testid='welcome-title']")
        assertTrue(welcomeTitle.isVisible)
        assertEquals("Welcome to Aionify", welcomeTitle.textContent())

        // Verify the Get Started button is present
        val getStartedButton = playwright.page.locator("[data-testid='get-started-button']")
        assertTrue(getStartedButton.isVisible)
        assertEquals("Get Started", getStartedButton.textContent())
    }

    @Test
    fun `should have properly styled components`() {
        playwright.page.navigate(url.toString())

        // Verify the app container is present
        val appContainer = playwright.page.locator("[data-testid='app']")
        assertTrue(appContainer.isVisible)

        // Verify the card component is rendered
        val card = playwright.page.locator(".rounded-lg.border")
        assertTrue(card.isVisible)
    }
}
