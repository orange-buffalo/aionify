package io.orangebuffalo.aionify

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration

/**
 * End-to-end test that validates login functionality on a Docker image.
 * 
 * This test:
 * 1. Starts the application and database using Docker Compose
 * 2. Waits for the application to be ready
 * 3. Extracts the auto-generated admin password from logs
 * 4. Uses Playwright to test login with the extracted credentials
 * 5. Validates that login is successful by checking the admin portal loads
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DockerLoginE2ETest {

    private lateinit var composeContainer: ComposeContainer
    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var page: Page
    private lateinit var appUrl: String
    private lateinit var adminPassword: String

    companion object {
        private const val APP_SERVICE = "aionify"
        private const val APP_PORT = 8080
        private const val ADMIN_USERNAME = "sudo"
    }

    @BeforeAll
    fun setup() {
        // Get the Docker image from system property (passed from Gradle)
        val dockerImage = System.getProperty("aionify.docker.image")
            ?: throw IllegalStateException("aionify.docker.image system property must be set")

        println("Using Docker image: $dockerImage")

        // Create environment variables for docker-compose
        val envVars = mapOf("AIONIFY_IMAGE" to dockerImage)

        // Find the docker-compose file
        val composeFile = File("src/test/resources/docker-compose-e2e.yml")
        if (!composeFile.exists()) {
            throw IllegalStateException("Docker compose file not found at: ${composeFile.absolutePath}")
        }

        // Start Docker Compose
        composeContainer = ComposeContainer(composeFile)
            .withExposedService(APP_SERVICE, APP_PORT, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)))
            .withEnv(envVars)
            .withLocalCompose(true)

        println("Starting Docker Compose...")
        composeContainer.start()

        // Get the application URL
        val host = composeContainer.getServiceHost(APP_SERVICE, APP_PORT)
        val port = composeContainer.getServicePort(APP_SERVICE, APP_PORT)
        appUrl = "http://$host:$port"

        println("Application available at: $appUrl")

        // Extract admin password from logs
        adminPassword = extractAdminPasswordFromLogs()
        println("Extracted admin password successfully")

        // Setup Playwright
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(true)
        )
    }

    @AfterAll
    fun teardown() {
        if (::page.isInitialized) {
            page.close()
        }
        if (::browser.isInitialized) {
            browser.close()
        }
        if (::playwright.isInitialized) {
            playwright.close()
        }
        if (::composeContainer.isInitialized) {
            composeContainer.stop()
        }
    }

    @Test
    fun `should successfully login with auto-generated admin credentials`() {
        // Create a new page for this test
        page = browser.newPage()

        // Navigate to login page
        page.navigate("$appUrl/login")

        // Verify login page is displayed
        val loginPage = page.locator("[data-testid='login-page']")
        assertThat(loginPage).isVisible()

        // Enter admin credentials
        page.locator("[data-testid='username-input']").fill(ADMIN_USERNAME)
        page.locator("[data-testid='password-input']").fill(adminPassword)

        // Click login button
        page.locator("[data-testid='login-button']").click()

        // Wait for redirect to admin portal
        page.waitForURL("**/admin", Page.WaitForURLOptions().setTimeout(10000.0))

        // Verify we're on the admin portal
        val adminPortal = page.locator("[data-testid='admin-portal']")
        assertThat(adminPortal).isVisible()

        val adminTitle = page.locator("[data-testid='admin-title']")
        assertThat(adminTitle).hasText("Admin Portal")

        println("✓ Login successful - admin portal loaded")
    }

    private fun extractAdminPasswordFromLogs(): String {
        val logs = composeContainer.getContainerByServiceName("${APP_SERVICE}_1")
            .orElseThrow { IllegalStateException("Could not find container for service $APP_SERVICE") }
            .logs
        
        // Look for the password pattern in logs
        // Expected format:
        // ============================================================
        // DEFAULT ADMIN CREATED
        // Username: sudo
        // Password: <random-password>
        // Please change this password after first login!
        // ============================================================
        
        val passwordPattern = Regex("""Password:\s*([^\s]+)""")
        val match = passwordPattern.find(logs)
            ?: throw IllegalStateException(
                "Could not find admin password in logs. Logs:\n${logs.take(5000)}"
            )

        return match.groupValues[1]
    }
}
