package io.orangebuffalo.aionify

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
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
class DockerLoginE2ETest {

    private val log = LoggerFactory.getLogger(DockerLoginE2ETest::class.java)

    companion object {
        private const val APP_SERVICE = "aionify"
        private const val APP_PORT = 8080
        private const val ADMIN_USERNAME = "sudo"
        private const val MAX_LOG_OUTPUT_LENGTH = 5000
    }

    @Test
    fun `should successfully login with auto-generated admin credentials`() {
        val dockerImage = System.getProperty("aionify.docker.image")
            ?: throw IllegalStateException("aionify.docker.image system property must be set")

        log.info("Using Docker image: {}", dockerImage)

        // Create a temporary docker-compose file with the actual image name
        val tempComposeFile = createTempComposeFile(dockerImage)

        try {
            // Start Docker Compose
            val composeContainer = ComposeContainer(tempComposeFile)
                .withExposedService(APP_SERVICE, APP_PORT,
                    Wait.forLogMessage(".*?Startup completed in.*", 1))
                        .withStartupTimeout(Duration.ofMinutes(2))
                .withLocalCompose(true)
                .withLogConsumer(APP_SERVICE, Slf4jLogConsumer(log))

            try {
                log.info("Starting Docker Compose...")
                composeContainer.start()

                // Get the application URL
                val host = composeContainer.getServiceHost(APP_SERVICE, APP_PORT)
                val port = composeContainer.getServicePort(APP_SERVICE, APP_PORT)
                val appUrl = "http://$host:$port"

                log.info("Application available at: {}", appUrl)

                // Extract admin password from logs
                val adminPassword = extractAdminPasswordFromLogs(composeContainer)
                log.info("Extracted admin password successfully")

                // Test login with Playwright
                testLoginWithPlaywright(appUrl, adminPassword)
            } finally {
                composeContainer.stop()
            }
        } finally {
            // Clean up temporary file (best effort)
            try {
                if (tempComposeFile.exists()) {
                    tempComposeFile.delete()
                }
            } catch (e: Exception) {
                log.warn("Failed to delete temporary compose file: {}", e.message)
            }
        }
    }

    private fun createTempComposeFile(dockerImage: String): File {
        // Read the template compose file
        val templateFile = File("src/test/resources/docker-compose-e2e.yml")
        if (!templateFile.exists()) {
            throw IllegalStateException("Template compose file not found at: ${templateFile.absolutePath}")
        }

        val template = templateFile.readText()

        // Replace the environment variable with the actual image name
        val content = template.replace("\${AIONIFY_IMAGE}", dockerImage)

        val tempFile = File.createTempFile("docker-compose-e2e-", ".yml")
        tempFile.deleteOnExit()
        tempFile.writeText(content)

        log.info("Created temporary compose file at: {}", tempFile.absolutePath)

        return tempFile
    }

    private fun testLoginWithPlaywright(appUrl: String, adminPassword: String) {
        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true)
            )

            browser.use {
                val context = browser.newContext()

                context.use {
                    // Start tracing
                    context.tracing().start(
                        Tracing.StartOptions()
                            .setScreenshots(true)
                            .setSnapshots(true)
                            .setSources(true)
                    )

                    try {
                        val page = context.newPage()

                        page.use {
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

                            log.info("✓ Login successful - admin portal loaded")
                        }
                    } finally {
                        // Save trace
                        val tracesDir = Paths.get("build/playwright-traces")
                        Files.createDirectories(tracesDir)
                        val tracePath = tracesDir.resolve("DockerLoginE2ETest_should_successfully_login.zip")
                        context.tracing().stop(Tracing.StopOptions().setPath(tracePath))
                        log.info("Trace saved to: {}", tracePath.toAbsolutePath())
                    }
                }
            }
        }
    }

    private fun extractAdminPasswordFromLogs(composeContainer: ComposeContainer): String {
        // Testcontainers appends '_1' to the service name for the first instance
        val containerName = "${APP_SERVICE}-1"
        val container = composeContainer.getContainerByServiceName(containerName)
            .orElseThrow {
                IllegalStateException(
                    "Could not find container for service $APP_SERVICE. " +
                    "Expected container name: $containerName. " +
                    "This might happen if Testcontainers changes its naming convention."
                )
            }

        val logs = container.logs

        // Look for the password pattern in logs
        // Expected format:
        // ============================================================
        // DEFAULT ADMIN CREATED
        // Username: sudo
        // Password: <random-password>
        // Please change this password after first login!
        // ============================================================

        val passwordPattern = Regex(""".*?Password:\s*([^\s]+)""")
        val match = passwordPattern.find(logs)
            ?: throw IllegalStateException(
                "Could not find admin password in logs. Logs:\n${logs.take(MAX_LOG_OUTPUT_LENGTH)}"
            )

        return match.groupValues[1]
    }
}
