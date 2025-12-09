package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URL

/**
 * Another Playwright test class demonstrating the reusable PlaywrightTestSupport setup.
 */
@MicronautTest
class FrontendNavigationPlaywrightTest : PlaywrightTestBase() {

        @Inject
    lateinit var server: EmbeddedServer


    lateinit var url: URL

    @Test
    fun `should navigate to root page successfully`() {
        val response = page.navigate(url.toString())
        
        // Verify the navigation was successful
        assertTrue(response?.ok() == true, "Navigation should succeed with OK status")
    }

    @Test
    fun `should have correct viewport and page state`() {
        page.navigate(url.toString())
        
        // Verify the page is not closed
        assertFalse(page.isClosed, "Page should not be closed")
        
        // Verify basic page content is loaded
        val body = page.locator("body")
        assertThat(body).isVisible()
    }
}
