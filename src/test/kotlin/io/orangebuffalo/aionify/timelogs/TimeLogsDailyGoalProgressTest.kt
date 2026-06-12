package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.DailyGoalBreak
import io.orangebuffalo.aionify.domain.GoalsSettings
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.withLocalDate
import io.orangebuffalo.aionify.withLocalTime
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TimeLogsDailyGoalProgressTest : TimeLogsPageTestBase() {
    @Test
    fun `should render current day daily goal progress and tooltip when enabled`() {
        val baseTime = setBaseTime("2024-03-16", "11:30")
        val userId = requireNotNull(testUser.id)

        val goalsSettings =
            testDatabaseSupport.insert(
                GoalsSettings(
                    userId = userId,
                    dailyEnabled = true,
                    dailyGoalMinutes = 240,
                ),
            )
        testDatabaseSupport.insert(
            DailyGoalBreak(
                goalsSettingsId = requireNotNull(goalsSettings.id),
                sortOrder = 0,
                fromTime = LocalTime.of(12, 0),
                toTime = LocalTime.of(12, 30),
            ),
        )

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("09:00"),
                endTime = baseTime.withLocalTime("10:00"),
                title = "Morning Task",
                ownerId = userId,
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("10:30"),
                endTime = baseTime.withLocalTime("11:30"),
                title = "Current Day Task",
                ownerId = userId,
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalDate("2024-03-15").withLocalTime("09:00"),
                endTime = baseTime.withLocalDate("2024-03-15").withLocalTime("10:00"),
                title = "Yesterday Task",
                ownerId = userId,
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        val progress = page.locator("[data-testid='daily-goal-progress']")
        assertThat(progress).hasCount(1)
        assertThat(progress).hasAttribute("aria-valuenow", "50")
        assertThat(page.locator("[data-testid='daily-goal-progress-fill']")).hasAttribute("style", "width: 50%;")

        progress.focus()
        page.clock().runFor(1000)
        val tooltip = page.locator("[data-testid='daily-goal-progress-tooltip']")
        assertThat(tooltip).isVisible()
        assertThat(tooltip).containsText("Goal achievement: 50%")
        assertThat(tooltip).containsText("Estimated completion: 14:00")

        captureUiReviewScreenshot("daily-goal-progress")
    }

    @Test
    fun `should show daily goal met tooltip when current day total reaches goal`() {
        val baseTime = setBaseTime("2024-03-16", "11:30")
        val userId = requireNotNull(testUser.id)

        testDatabaseSupport.insert(
            GoalsSettings(
                userId = userId,
                dailyEnabled = true,
                dailyGoalMinutes = 120,
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("09:00"),
                endTime = baseTime.withLocalTime("11:30"),
                title = "Goal Met Task",
                ownerId = userId,
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        val progress = page.locator("[data-testid='daily-goal-progress']")
        assertThat(progress).hasAttribute("aria-valuenow", "100")

        progress.focus()
        page.clock().runFor(1000)
        val tooltip = page.locator("[data-testid='daily-goal-progress-tooltip']")
        assertThat(tooltip).isVisible()
        assertThat(tooltip).containsText("Daily goal met")
        assertThat(tooltip).not().containsText("Goal achievement")
        assertThat(tooltip).not().containsText("Estimated completion")
    }

    @Test
    fun `should not render daily goal progress for non-today entries when enabled`() {
        val baseTime = setBaseTime("2024-03-16", "11:30")
        val userId = requireNotNull(testUser.id)

        testDatabaseSupport.insert(
            GoalsSettings(
                userId = userId,
                dailyEnabled = true,
                dailyGoalMinutes = 240,
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalDate("2024-03-15").withLocalTime("09:00"),
                endTime = baseTime.withLocalDate("2024-03-15").withLocalTime("10:00"),
                title = "Yesterday Task",
                ownerId = userId,
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        assertThat(page.locator("[data-testid='daily-goal-progress']")).not().isVisible()
    }

    @Test
    fun `should not render daily goal progress when disabled`() {
        val baseTime = setBaseTime("2024-03-16", "11:30")
        val userId = requireNotNull(testUser.id)

        testDatabaseSupport.insert(
            GoalsSettings(
                userId = userId,
                dailyEnabled = false,
                dailyGoalMinutes = 240,
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("09:00"),
                endTime = baseTime.withLocalTime("10:00"),
                title = "Morning Task",
                ownerId = userId,
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        assertThat(page.locator("[data-testid='daily-goal-progress']")).not().isVisible()
    }
}
