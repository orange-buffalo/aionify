package org.aionify

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL

@QuarkusTest
class FrontendPlaywrightTest {

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var page: Page

    @TestHTTPResource("/")
    lateinit var url: URL

    @BeforeEach
    fun setup() {
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(true)
        )
        page = browser.newPage()
    }

    @AfterEach
    fun teardown() {
        page.close()
        browser.close()
        playwright.close()
    }

    @Test
    fun `should display welcome page with title and button`() {
        page.navigate(url.toString())

        // Verify the page title
        assertEquals("Aionify - Time Tracking", page.title())

        // Verify the welcome title is present
        val welcomeTitle = page.locator("[data-testid='welcome-title']")
        assertTrue(welcomeTitle.isVisible)
        assertEquals("Welcome to Aionify", welcomeTitle.textContent())

        // Verify the Get Started button is present
        val getStartedButton = page.locator("[data-testid='get-started-button']")
        assertTrue(getStartedButton.isVisible)
        assertEquals("Get Started", getStartedButton.textContent())
    }

    @Test
    fun `should have properly styled components`() {
        page.navigate(url.toString())

        // Verify the app container is present
        val appContainer = page.locator("[data-testid='app']")
        assertTrue(appContainer.isVisible)

        // Verify the card component is rendered
        val card = page.locator(".rounded-lg.border")
        assertTrue(card.isVisible)
    }
}
