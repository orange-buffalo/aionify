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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * JUnit 5 extension that provides Playwright browser, context, and page management with automatic trace recording.
 * 
 * Traces are saved to `build/playwright-traces/` with a filename based on the test class and method names.
 */
class PlaywrightTestSupport : BeforeEachCallback, AfterEachCallback {

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var browserContext: BrowserContext
    private lateinit var _page: Page

    val page: Page
        get() = _page

    companion object {
        private const val TRACES_DIR = "build/playwright-traces"
        private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9_-]")
    }

    override fun beforeEach(context: ExtensionContext) {
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

    override fun afterEach(context: ExtensionContext) {
        // Generate trace filename based on test class and method
        val traceFileName = buildTraceFileName(context)
        val tracesDir = Paths.get(TRACES_DIR)
        Files.createDirectories(tracesDir)
        val tracePath = tracesDir.resolve(traceFileName)

        // Stop tracing and save to file
        browserContext.tracing().stop(
            Tracing.StopOptions().setPath(tracePath)
        )

        _page.close()
        browserContext.close()
        browser.close()
        playwright.close()
    }

    private fun buildTraceFileName(context: ExtensionContext): String {
        val className = context.testClass.map { it.simpleName }.orElse("UnknownClass")
        val methodName = context.testMethod.map { it.name }.orElse("unknownMethod")
        // Sanitize method name (replace special characters)
        val sanitizedMethodName = methodName.replace(SANITIZE_REGEX, "_")
        return "${className}_${sanitizedMethodName}.zip"
    }
}
