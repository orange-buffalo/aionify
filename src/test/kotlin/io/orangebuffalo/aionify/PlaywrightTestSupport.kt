package io.orangebuffalo.aionify

import com.microsoft.playwright.*
import io.micronaut.runtime.server.EmbeddedServer
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant

/**
 * Base class for Playwright tests that provides browser lifecycle management, automatic trace recording,
 * browser console verification, and database cleanup before each test.
 *
 * Traces are saved to `build/playwright-traces/` with a filename based on the test class and method names.
 *
 * Browser console errors and warnings are automatically captured and will cause the test to fail if detected.
 *
 * Usage:
 * ```kotlin
 * @MicronautTest(transactional = false)
 * class MyPlaywrightTest : PlaywrightTestBase() {
 *     @Inject
 *     lateinit var testAuthSupport: TestAuthSupport
 *
 *     @Inject
 *     lateinit var userRepository: UserRepository
 *
 *     private lateinit var testUser: User
 *
 *     @BeforeEach
 *     fun setupTestData() {
 *         // Database is already truncated by base class
 *         // Setup test-specific data
 *         testUser = userRepository.save(User(...))
 *     }
 *
 *     @Test
 *     fun `my test`() {
 *         page.navigate(url.toString())
 *     }
 * }
 * ```
 */
abstract class PlaywrightTestBase {
    private val log = LoggerFactory.getLogger(PlaywrightTestBase::class.java)

    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport

    @Inject
    lateinit var server: EmbeddedServer

    @Inject
    lateinit var testUsers: TestUsers

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var browserContext: BrowserContext
    private lateinit var _page: Page
    private lateinit var testInfo: TestInfo
    private val consoleMessages = mutableListOf<ConsoleMessage>()

    /**
     * Base URL for the application, set automatically from the embedded server.
     */
    protected val baseUrl: String
        get() = "http://localhost:${server.port}"

    protected val page: Page
        get() = _page

    companion object {
        private const val TRACES_DIR = "build/playwright-traces"
        private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")

        // Local storage keys matching frontend constants
        const val TOKEN_KEY = "aionify_token"
        const val LAST_USERNAME_KEY = "aionify_last_username"

        /**
         * Fixed time for all Playwright tests defined in NZDT (New Zealand Daylight Time, UTC+13):
         * Saturday, March 16, 2024 at 03:30:00 NZDT
         *
         * This corresponds to Friday, March 15, 2024 at 14:30:00 UTC.
         * This is the same time used by TestTimeService for backend operations.
         *
         * All tests run in Pacific/Auckland timezone to ensure timezone awareness and catch
         * any timezone-related bugs early. Test expectations should use NZDT values (Saturday 03:30).
         */
        val FIXED_TEST_TIME: Instant = TestTimeService.FIXED_TEST_TIME
    }

    @BeforeEach
    fun setupPlaywright(testInfo: TestInfo) {
        this.testInfo = testInfo

        // Clean up database before each test for isolation
        testDatabaseSupport.truncateAllTables()

        // Clear console messages from previous test
        consoleMessages.clear()

        playwright = Playwright.create()
        browser =
            playwright.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true),
            )

        // Create context with base URL for simpler navigation
        // Can be overridden by calling createBrowserContext() before navigation
        browserContext = createBrowserContext()

        // Start tracing for this test
        browserContext.tracing().start(
            Tracing
                .StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true),
        )

        _page = browserContext.newPage()

        // Add console message listener to capture errors and warnings
        _page.onConsoleMessage { message ->
            consoleMessages.add(message)
        }

        // Add request/response logging for AJAX debugging
        _page.onRequest { request ->
            if (request.url().contains("/api-ui/")) {
                log.debug("[REQUEST] ${request.method()} ${request.url()}")
                if (request.postData() != null) {
                    log.debug("[REQUEST BODY] ${request.postData()}")
                }
            }
        }

        _page.onResponse { response ->
            if (response.url().contains("/api-ui/")) {
                log.debug("[RESPONSE] ${response.status()} ${response.url()}")
                try {
                    val body = response.text()
                    log.debug("[RESPONSE BODY] $body")
                } catch (e: Exception) {
                    log.debug("[RESPONSE BODY] (unable to read: ${e.message})")
                }
            }
        }

        // Install Playwright clock with fixed time for deterministic tests
        // Use pauseAt to prevent automatic time progression - time only advances when explicitly requested
        // This ensures frontend JavaScript Date API uses the same fixed time as backend TimeService
        _page.clock().pauseAt(FIXED_TEST_TIME.toEpochMilli())
    }

    private fun createBrowserContext(): BrowserContext =
        browser.newContext(
            Browser
                .NewContextOptions()
                .setBaseURL(baseUrl)
                .setTimezoneId("Pacific/Auckland")
                .setPermissions(listOf("clipboard-read", "clipboard-write")),
        )

    /**
     * Performs UI-based login for tests that need to test the actual login flow.
     * Use this when testing login functionality itself.
     */
    protected fun loginViaUI(
        userName: String,
        password: String,
        expectedRedirectPattern: String,
    ) {
        page.navigate("/login")
        page.locator("[data-testid='username-input']").fill(userName)
        page.locator("[data-testid='password-input']").fill(password)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL(expectedRedirectPattern)
    }

    /**
     * Authenticates via JWT token and navigates to the target page.
     * This is much faster than UI login and should be used for tests that don't need to test login.
     *
     * @param targetPath The path to navigate to after authentication (relative to base URL)
     * @param user The user to authenticate as
     * @param testAuthSupport The auth support instance for generating tokens
     */
    protected fun loginViaToken(
        targetPath: String,
        user: User,
        testAuthSupport: TestAuthSupport,
    ) {
        val authData = testAuthSupport.generateAuthStorageData(user)

        // Navigate to login page (which doesn't make authenticated API calls) to establish localStorage origin
        // This prevents race conditions where API calls are made with stale tokens during user switching
        page.navigate("/login")

        // Set localStorage items
        page.evaluate(
            """
            (data) => {
                localStorage.setItem('aionify_language', data.languageCode);
                localStorage.setItem('$TOKEN_KEY', data.token);
                localStorage.setItem('$LAST_USERNAME_KEY', JSON.stringify({
                    userName: data.userName,
                    greeting: data.greeting
                }));
            }
            """.trimIndent(),
            mapOf(
                "token" to authData.token,
                "userName" to authData.userName,
                "greeting" to authData.greeting,
                "languageCode" to user.languageCode,
            ),
        )

        // Navigate to the target page - localStorage will already be set and i18n will initialize with localStorage values
        page.navigate(targetPath)
    }

    @AfterEach
    fun teardownPlaywright() {
        try {
            // Generate trace filename based on test class and method
            val traceFileName = buildTraceFileName()
            val tracesDir = Paths.get(TRACES_DIR)
            Files.createDirectories(tracesDir)
            val tracePath = tracesDir.resolve(traceFileName)

            // Stop tracing and save to file
            browserContext.tracing().stop(
                Tracing.StopOptions().setPath(tracePath),
            )
        } finally {
            _page.close()
            browserContext.close()
            browser.close()
            playwright.close()

            // Verify browser console for errors and warnings after cleanup
            verifyConsoleMessages()
        }
    }

    private fun buildTraceFileName(): String {
        val className = this::class.java.simpleName
        val methodName = testInfo.testMethod.map { it.name }.orElse("unknownMethod")
        // Sanitize method name (replace special characters)
        val sanitizedMethodName = methodName.replace(SANITIZE_REGEX, "_")
        return "${className}_$sanitizedMethodName.zip"
    }

    /**
     * Verifies that no browser console errors or warnings were logged during the test.
     * Throws an AssertionError if any errors or warnings are found.
     *
     * Note: "Failed to load resource" errors are ignored as they are expected when testing
     * error scenarios (e.g., 401 Unauthorized, 400 Bad Request responses).
     */
    private fun verifyConsoleMessages() {
        val errorMessages =
            consoleMessages.filter { message ->
                val isErrorOrWarning = message.type() == "error" || message.type() == "warning"
                val isNetworkError = message.text().startsWith("Failed to load resource:")

                // Filter to only actual errors/warnings, excluding network-related errors
                isErrorOrWarning && !isNetworkError
            }

        if (errorMessages.isNotEmpty()) {
            val formattedMessages =
                errorMessages.joinToString("\n") { message ->
                    "[${message.type().uppercase()}] ${message.text()} (${message.location()})"
                }
            throw AssertionError(
                "Browser console contained ${errorMessages.size} error(s) or warning(s):\n$formattedMessages",
            )
        }
    }
}
