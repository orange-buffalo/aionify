package org.aionify

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URL

/**
 * Another Playwright test class demonstrating the reusable PlaywrightTestSupport setup.
 */
@QuarkusTest
class FrontendNavigationPlaywrightTest {

    companion object {
        @JvmField
        @RegisterExtension
        val playwright = PlaywrightTestSupport()
    }

    @TestHTTPResource("/")
    lateinit var url: URL

    @Test
    fun `should navigate to root page successfully`() {
        val response = playwright.page.navigate(url.toString())
        
        // Verify the navigation was successful
        assertTrue(response?.ok() == true, "Navigation should succeed with OK status")
    }

    @Test
    fun `should have correct viewport and page state`() {
        playwright.page.navigate(url.toString())
        
        // Verify the page is not closed
        assertFalse(playwright.page.isClosed, "Page should not be closed")
        
        // Verify basic page content is loaded
        val body = playwright.page.locator("body")
        assertTrue(body.isVisible, "Body element should be visible")
    }
}
