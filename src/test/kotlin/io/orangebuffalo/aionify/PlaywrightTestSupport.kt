package io.orangebuffalo.aionify

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import io.orangebuffalo.aionify.domain.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Base class for Playwright tests that provides browser lifecycle management and automatic trace recording.
 * 
 * Traces are saved to `build/playwright-traces/` with a filename based on the test class and method names.
 * 
 * Usage:
 * ```kotlin
 * @QuarkusTest
 * class MyPlaywrightTest : PlaywrightTestBase() {
 *     @Inject
 *     lateinit var testDatabaseSupport: TestDatabaseSupport
 *     
 *     @Inject
 *     lateinit var testAuthSupport: TestAuthSupport
 *     
 *     @BeforeEach
 *     fun setupTestData() {
 *         testDatabaseSupport.truncateAllTables()
 *         // Setup test-specific data
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

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var browserContext: BrowserContext
    private lateinit var _page: Page
    private lateinit var testInfo: TestInfo

    protected val page: Page
        get() = _page

    companion object {
        private const val TRACES_DIR = "build/playwright-traces"
        private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
        
        // Local storage keys matching frontend constants
        const val TOKEN_KEY = "aionify_token"
        const val LAST_USERNAME_KEY = "aionify_last_username"
    }

    @BeforeEach
    fun setupPlaywright(testInfo: TestInfo) {
        this.testInfo = testInfo
        
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(true)
        )
        browserContext = browser.newContext()
        
        // Start tracing for this test
        browserContext.tracing().start(
            Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true)
        )
        
        _page = browserContext.newPage()
    }

    /**
     * Performs UI-based login for tests that need to test the actual login flow.
     * Use this when testing login functionality itself.
     */
    protected fun loginViaUI(loginUrl: URL, userName: String, password: String, expectedRedirectPattern: String) {
        page.navigate(loginUrl.toString())
        page.locator("[data-testid='username-input']").fill(userName)
        page.locator("[data-testid='password-input']").fill(password)
        page.locator("[data-testid='login-button']").click()
        page.waitForURL(expectedRedirectPattern)
    }

    /**
     * Authenticates via JWT token and navigates to the target page.
     * This is much faster than UI login and should be used for tests that don't need to test login.
     * 
     * @param baseUrl The base URL of the application (used to set localStorage in correct origin)
     * @param targetUrl The URL to navigate to after authentication
     * @param user The user to authenticate as
     * @param testAuthSupport The auth support instance for generating tokens
     */
    protected fun loginViaToken(baseUrl: URL, targetUrl: URL, user: User, testAuthSupport: TestAuthSupport) {
        val authData = testAuthSupport.generateAuthStorageData(user)
        
        // Navigate to a page first to set the origin for localStorage
        // Using a simple page that doesn't require auth
        page.navigate(baseUrl.toString())
        
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
        page.navigate(targetUrl.toString())
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
