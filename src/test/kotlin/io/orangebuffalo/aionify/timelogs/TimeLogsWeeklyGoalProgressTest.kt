package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.domain.GoalsSettings
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.WeekDay
import io.orangebuffalo.aionify.withLocalTime
import org.junit.jupiter.api.Test

class TimeLogsWeeklyGoalProgressTest : TimeLogsPageTestBase() {
    @Test
    fun `should render weekly goal percentage only when daily goal is disabled`() {
        val baseTime = setBaseTime("2024-03-11", "12:00")
        val userId = requireNotNull(testUser.id)

        testDatabaseSupport.insert(
            GoalsSettings(
                userId = userId,
                dailyEnabled = false,
                dailyGoalMinutes = 0,
                weeklyEnabled = true,
                weeklyGoalMinutes = 600,
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("09:00"),
                endTime = baseTime.withLocalTime("11:00"),
                title = "Weekly Task",
                ownerId = userId,
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        val progress = page.locator("[data-testid='weekly-goal-progress']")
        assertThat(progress).hasAttribute("aria-valuenow", "20")
        assertThat(page.locator("[data-testid='weekly-goal-progress-fill']")).hasAttribute("style", "width: 20%;")

        progress.focus()
        page.clock().runFor(1000)
        val tooltip = page.locator("[data-testid='weekly-goal-progress-tooltip']")
        assertThat(tooltip).isVisible()
        assertThat(tooltip).containsText("Weekly goal achievement: 20%")
        assertThat(tooltip).not().containsText("Estimated")
        assertThat(tooltip).not().containsText("Not enough")

        page.locator("[data-testid='previous-week-button']").click()
        assertThat(page.locator("[data-testid='weekly-goal-progress']")).not().isVisible()
    }

    @Test
    fun `should render weekly goal progress with daily-goal based overtime estimate`() {
        val baseTime = setBaseTime("2024-03-11", "13:00")
        val userId = requireNotNull(testUser.id)

        testDatabaseSupport.insert(
            GoalsSettings(
                userId = userId,
                dailyEnabled = true,
                dailyGoalMinutes = 240,
                weeklyEnabled = true,
                weeklyGoalMinutes = 960,
                weeklyWorkingDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("09:00"),
                endTime = baseTime.withLocalTime("13:00"),
                title = "Weekly Overtime Task",
                ownerId = userId,
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        val progress = page.locator("[data-testid='weekly-goal-progress']")
        assertThat(progress).hasAttribute("aria-valuenow", "25")
        assertThat(page.locator("[data-testid='weekly-goal-progress-fill']")).hasAttribute("style", "width: 25%;")

        progress.focus()
        page.clock().runFor(1000)
        val tooltip = page.locator("[data-testid='weekly-goal-progress-tooltip']")
        assertThat(tooltip).isVisible()
        assertThat(tooltip).containsText("Weekly goal achievement: 25%")
        assertThat(tooltip).containsText("Estimated overtime 4h on Friday")

        captureUiReviewScreenshot("weekly-goal-progress")
    }

    @Test
    fun `should render actual overtime after weekly goal is exceeded`() {
        val baseTime = setBaseTime("2024-03-11", "21:00")
        val userId = requireNotNull(testUser.id)

        testDatabaseSupport.insert(
            GoalsSettings(
                userId = userId,
                dailyEnabled = true,
                dailyGoalMinutes = 480,
                weeklyEnabled = true,
                weeklyGoalMinutes = 600,
                weeklyWorkingDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY),
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("09:00"),
                endTime = baseTime.withLocalTime("21:00"),
                title = "Weekly Actual Overtime Task",
                ownerId = userId,
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        val progress = page.locator("[data-testid='weekly-goal-progress']")
        assertThat(progress).hasAttribute("aria-valuenow", "100")
        assertThat(page.locator("[data-testid='weekly-goal-progress-fill']")).hasAttribute("style", "width: 100%;")

        progress.focus()
        page.clock().runFor(1000)
        val tooltip = page.locator("[data-testid='weekly-goal-progress-tooltip']")
        assertThat(tooltip).isVisible()
        assertThat(tooltip).containsText("Weekly goal met")
        assertThat(tooltip).containsText("Weekly overtime: 2h")
    }

    @Test
    fun `should not render weekly goal progress when disabled`() {
        val baseTime = setBaseTime("2024-03-11", "12:00")
        val userId = requireNotNull(testUser.id)

        testDatabaseSupport.insert(
            GoalsSettings(
                userId = userId,
                weeklyEnabled = false,
                weeklyGoalMinutes = 600,
            ),
        )
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = baseTime.withLocalTime("09:00"),
                endTime = baseTime.withLocalTime("11:00"),
                title = "Weekly Task",
                ownerId = userId,
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        assertThat(page.locator("[data-testid='weekly-goal-progress']")).not().isVisible()
    }
}
