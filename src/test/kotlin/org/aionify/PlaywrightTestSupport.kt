package org.aionify

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import java.nio.file.Paths

/**
 * JUnit 5 extension that provides Playwright browser, context, and page management with automatic trace recording.
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
class PlaywrightTestSupport : BeforeEachCallback, AfterEachCallback {

    private var _page: Page? = null
    
    val page: Page
        get() = _page ?: throw IllegalStateException("Page not initialized. Ensure the test is running with @RegisterExtension.")

    companion object {
        private const val TRACES_DIR = "build/playwright-traces"
        private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
        private val NAMESPACE = ExtensionContext.Namespace.create(PlaywrightTestSupport::class.java)
        
        // ThreadLocal to share the current page with PlaywrightTestBase
        internal val currentPage = ThreadLocal<Page?>()
    }

    override fun beforeEach(context: ExtensionContext) {
        val playwright = Playwright.create()
        val browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(true)
        )
        val browserContext = browser.newContext()
        
        // Start tracing for this test
        browserContext.tracing().start(
            Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true)
        )
        
        _page = browserContext.newPage()
        currentPage.set(_page)
        
        // Store resources in extension context store
        val store = context.getStore(NAMESPACE)
        store.put("playwright", playwright)
        store.put("browser", browser)
        store.put("browserContext", browserContext)
    }

    override fun afterEach(context: ExtensionContext) {
        val store = context.getStore(NAMESPACE)
        val browserContext = store.get("browserContext") as? BrowserContext
        val browser = store.get("browser") as? Browser
        val playwright = store.get("playwright") as? Playwright
        
        try {
            // Generate trace filename based on test class and method
            val traceFileName = buildTraceFileName(context)
            val tracesDir = Paths.get(TRACES_DIR)
            Files.createDirectories(tracesDir)
            val tracePath = tracesDir.resolve(traceFileName)

            // Stop tracing and save to file
            browserContext?.tracing()?.stop(
                Tracing.StopOptions().setPath(tracePath)
            )
        } finally {
            _page?.close()
            browserContext?.close()
            browser?.close()
            playwright?.close()
            _page = null
            currentPage.remove()
        }
    }

    private fun buildTraceFileName(context: ExtensionContext): String {
        val className = context.testClass.map { it.simpleName }.orElse("UnknownClass")
        val methodName = context.testMethod.map { it.name }.orElse("unknownMethod")
        // Sanitize method name (replace special characters)
        val sanitizedMethodName = methodName.replace(SANITIZE_REGEX, "_")
        return "${className}_${sanitizedMethodName}.zip"
    }
}

/**
 * Base class for Playwright tests that provides convenient access to the page instance.
 * Extend this class to avoid companion object boilerplate.
 */
abstract class PlaywrightTestBase {
    @JvmField
    @RegisterExtension
    val playwright = PlaywrightTestSupport()

    val page: Page
        get() = PlaywrightTestSupport.currentPage.get() 
            ?: throw IllegalStateException("Page not initialized. Ensure the extension is registered properly.")
}
