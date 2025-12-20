package io.orangebuffalo.aionify

import com.microsoft.playwright.*
import io.micronaut.runtime.server.EmbeddedServer
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Base class for Playwright tests that provides browser lifecycle management, automatic trace recording,
 * and database cleanup before each test.
 *
 * Traces are saved to `build/playwright-traces/` with a filename based on the test class and method names.
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
         * Fixed time for all Playwright tests: 2024-03-15T14:30:00Z (Friday, March 15, 2024 at 2:30 PM UTC)
         * This ensures deterministic behavior for time-sensitive tests.
         * This is the same time used by TestTimeService for backend operations.
         */
        val FIXED_TEST_TIME: Instant = TestTimeService.FIXED_TEST_TIME
    }

    @BeforeEach
    fun setupPlaywright(testInfo: TestInfo) {
        this.testInfo = testInfo

        // Clean up database before each test for isolation
        testDatabaseSupport.truncateAllTables()

        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(true)
        )

        // Create context with base URL for simpler navigation
        browserContext = browser.newContext(
            Browser.NewContextOptions().setBaseURL(baseUrl)
        )

        // Start tracing for this test
        browserContext.tracing().start(
            Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true)
        )

        _page = browserContext.newPage()
        
        // Install Playwright clock with fixed time for deterministic tests
        // This ensures frontend JavaScript Date API uses the same fixed time as backend TimeService
        _page.clock().install(Clock.InstallOptions().setTime(FIXED_TEST_TIME.toEpochMilli()))
    }

    /**
     * Performs UI-based login for tests that need to test the actual login flow.
     * Use this when testing login functionality itself.
     */
    protected fun loginViaUI(userName: String, password: String, expectedRedirectPattern: String) {
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
    protected fun loginViaToken(targetPath: String, user: User, testAuthSupport: TestAuthSupport) {
        val authData = testAuthSupport.generateAuthStorageData(user)

        // Navigate to a page first to set the origin for localStorage
        // Using the base URL
        page.navigate("/")

        // Set authentication data in localStorage
        page.evaluate("""
            (data) => {
                localStorage.setItem('$TOKEN_KEY', data.token);
                localStorage.setItem('$LAST_USERNAME_KEY', JSON.stringify({
                    userName: data.userName,
                    greeting: data.greeting
                }));
            }
        """.trimIndent(), mapOf(
            "token" to authData.token,
            "userName" to authData.userName,
            "greeting" to authData.greeting
        ))

        // Now navigate to the target page
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
                Tracing.StopOptions().setPath(tracePath)
            )
        } finally {
            _page.close()
            browserContext.close()
            browser.close()
            playwright.close()
        }
    }

    private fun buildTraceFileName(): String {
        val className = this::class.java.simpleName
        val methodName = testInfo.testMethod.map { it.name }.orElse("unknownMethod")
        // Sanitize method name (replace special characters)
        val sanitizedMethodName = methodName.replace(SANITIZE_REGEX, "_")
        return "${className}_${sanitizedMethodName}.zip"
    }
}
