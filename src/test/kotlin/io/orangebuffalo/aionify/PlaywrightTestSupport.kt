package io.orangebuffalo.aionify

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
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
