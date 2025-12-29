package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserSettings
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class AionifyImportPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser("importTestUser", "Import Test User")
        testDatabaseSupport.insert(UserSettings.create(userId = requireNotNull(regularUser.id)))
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    @Test
    fun `should show not implemented message for Aionify Export source`() {
        navigateToSettingsViaToken()

        // Select Aionify Export source
        val sourceSelect = page.locator("[data-testid='import-source-select']")
        sourceSelect.click()
        page.locator("[data-testid='import-source-aionify']").click()

        // Verify not implemented message is shown
        assertThat(page.locator("[data-testid='aionify-not-implemented']")).isVisible()
        assertThat(page.locator("[data-testid='aionify-not-implemented']"))
            .containsText("Not yet implemented")
    }
}
