package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Test

/**
 * Demonstrates the overlap warning feature with a screenshot.
 */
class TimeLogsOverlapDemoTest : TimeLogsPageTestBase() {
    @Test
    fun `demonstrate overlap warning feature`() {
        // FIXED_TEST_TIME is Saturday, March 16, 2024 at 03:30 NZDT

        // Create three entries with various overlaps to show the feature

        // Entry 1: 01:00 - 02:30 (1h 30min)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(9000), // 03:30 - 2:30 = 01:00
                endTime = FIXED_TEST_TIME.minusSeconds(3600), // 03:30 - 1:00 = 02:30
                title = "Morning Meeting",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 2: 02:00 - 03:00 (1h) - overlaps with Entry 1 from 02:00 to 02:30 (30min)
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(5400), // 03:30 - 1:30 = 02:00
                endTime = FIXED_TEST_TIME.minusSeconds(1800), // 03:30 - 0:30 = 03:00
                title = "Code Review",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Entry 3: 02:15 - 03:15 (1h) - overlaps with Entry 2
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = FIXED_TEST_TIME.minusSeconds(4500), // 03:30 - 1:15 = 02:15
                endTime = FIXED_TEST_TIME.minusSeconds(900), // 03:30 - 0:15 = 03:15
                title = "Development Work",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Wait for page to load
        assertThat(page.locator("[data-testid='day-group']")).isVisible()

        // Take a screenshot showing the overlap warnings
        page.screenshot(
            com.microsoft.playwright.Page
                .ScreenshotOptions()
                .setPath(
                    java.nio.file.Paths
                        .get("build/overlap-demo-screenshot.png"),
                ).setFullPage(true),
        )
    }
}
