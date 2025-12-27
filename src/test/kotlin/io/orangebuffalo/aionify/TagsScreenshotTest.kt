package io.orangebuffalo.aionify

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeLogEntry
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Paths

@MicronautTest(transactional = false)
class TagsScreenshotTest : PlaywrightTestBase() {

    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var testUser: io.orangebuffalo.aionify.domain.User

    @BeforeEach
    fun setupTestData() {
        testUser = testUsers.createRegularUser()
        
        // Create entries with various tag configurations
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(7200),
                endTime = FIXED_TEST_TIME.minusSeconds(5400),
                title = "Implement authentication feature",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("work", "urgent", "backend")
            )
        )
        
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(5400),
                endTime = FIXED_TEST_TIME.minusSeconds(3600),
                title = "Fix responsive design issues",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("frontend", "bug-fix")
            )
        )
        
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(3600),
                endTime = FIXED_TEST_TIME.minusSeconds(1800),
                title = "Code review session",
                ownerId = requireNotNull(testUser.id),
                tags = arrayOf("team", "review")
            )
        )
        
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(1800),
                endTime = FIXED_TEST_TIME.minusSeconds(900),
                title = "Daily standup meeting",
                ownerId = requireNotNull(testUser.id),
                tags = emptyArray()
            )
        )
    }

    @Test
    fun `take screenshot of tags display`() {
        loginViaToken("/portal/time-logs", testUser, testAuthSupport)
        
        // Wait for entries to load
        page.waitForSelector("[data-testid='day-group']")
        
        // Take screenshot
        page.screenshot(
            com.microsoft.playwright.Page.ScreenshotOptions()
                .setPath(Paths.get("/tmp/tags-display-screenshot.png"))
                .setFullPage(true)
        )
        
        println("Screenshot saved to: /tmp/tags-display-screenshot.png")
    }
}
