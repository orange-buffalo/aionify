package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class SecurityHeadersPlaywrightTest : PlaywrightTestBase() {
    @Test
    fun `should prevent the application from being framed`() {
        page.context().newPage().use { attackerPage ->
            attackerPage.setContent("<main>Attacker page</main>")
            attackerPage.evaluate(
                """
                url => new Promise(resolve => {
                    const frame = document.createElement('iframe');
                    frame.dataset.testid = 'aionify-frame';
                    frame.addEventListener('load', resolve, { once: true });
                    frame.src = url;
                    document.body.appendChild(frame);
                })
                """.trimIndent(),
                "$baseUrl/login",
            )

            assertThat(
                attackerPage
                    .frameLocator("[data-testid='aionify-frame']")
                    .locator("[data-testid='login-page']"),
            ).not().isVisible()
        }
    }
}
