package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Another Playwright test class demonstrating the reusable PlaywrightTestSupport setup.
 */
@MicronautTest(transactional = false)
class FrontendNavigationPlaywrightTest : PlaywrightTestBase() {
    @Test
    fun `should navigate to root page successfully`() {
        val response = page.navigate("/")

        // Verify the navigation was successful
        assertTrue(response?.ok() == true, "Navigation should succeed with OK status")
    }

    @Test
    fun `should have correct viewport and page state`() {
        page.navigate("/")

        // Verify the page is not closed
        assertFalse(page.isClosed, "Page should not be closed")

        // Verify basic page content is loaded
        val body = page.locator("body")
        assertThat(body).isVisible()
    }
}
