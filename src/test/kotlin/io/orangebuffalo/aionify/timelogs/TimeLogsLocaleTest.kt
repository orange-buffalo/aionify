package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Tests for locale-specific date/time formatting.
 */
class TimeLogsLocaleTest : TimeLogsPageTestBase() {
    @ParameterizedTest
    @MethodSource("localeTestCases")
    fun `should display dates and times according to user locale`(testCase: LocaleTestCase) {
        // Create a user with the specified locale
        val testUser =
            testUsers.createUserWithLocale(
                username = testCase.username,
                greeting = testCase.greeting,
                locale = java.util.Locale.forLanguageTag(testCase.localeTag),
            )

        // Create an entry that started at 14:30 (2:30 PM) - afternoon time to clearly show 12/24-hour format difference
        // FIXED_TEST_TIME is Saturday, March 16, 2024 at 03:30:00 NZDT
        // We want 14:30 NZDT, which is 11 hours later
        val afternoonTime = FIXED_TEST_TIME.plusSeconds(11 * 3600) // 14:30 NZDT

        // Set the browser clock to the afternoon time for this test
        page.clock().pauseAt(afternoonTime.toEpochMilli())

        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = afternoonTime.minusSeconds(1800), // Started 30 minutes ago at 14:00 (2:00 PM)
                endTime = null,
                title = testCase.taskTitle,
                ownerId = requireNotNull(testUser.id),
            ),
        )

        // Also create a completed entry to test time ranges in the day groups
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = afternoonTime.minusSeconds(7200), // Started 2 hours ago at 12:30 (12:30 PM)
                endTime = afternoonTime.minusSeconds(5400), // Ended 1.5 hours ago at 13:00 (1:00 PM)
                title = testCase.completedTaskTitle,
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify the active entry "started at" label uses locale format
        val startedAtLocator = page.locator("[data-testid='active-entry-started-at']")
        assertThat(startedAtLocator).isVisible()
        assertThat(startedAtLocator).containsText(testCase.expectedStartedAtText)

        // Verify the active entry time range in day groups uses locale format
        val activeEntryTimeRange =
            page
                .locator("[data-testid='time-entry']:has-text('${testCase.taskTitle}')")
                .locator("[data-testid='entry-time-range']")
        assertThat(activeEntryTimeRange).isVisible()
        assertThat(activeEntryTimeRange).hasText(testCase.expectedActiveTimeRangeText)

        // Verify the completed entry time range in day groups uses locale format
        val completedEntryTimeRange =
            page
                .locator("[data-testid='time-entry']:has-text('${testCase.completedTaskTitle}')")
                .locator("[data-testid='entry-time-range']")
        assertThat(completedEntryTimeRange).isVisible()
        assertThat(completedEntryTimeRange).hasText(testCase.expectedCompletedTimeRangeText)

        // Verify week range uses locale format
        val weekRangeLocator = page.locator("[data-testid='week-range']")
        assertThat(weekRangeLocator).isVisible()
        assertThat(weekRangeLocator).hasText(testCase.expectedWeekRangeText)

        // Verify day title uses locale format
        val dayTitleLocator = page.locator("[data-testid='day-title']").first()
        assertThat(dayTitleLocator).isVisible()
        assertThat(dayTitleLocator).hasText(testCase.expectedDayTitle)

        // Click edit button to test datetime picker with the locale
        timeLogsPage.clickEditEntry()

        // Verify edit mode date input uses locale format
        val dateInput = page.locator("[data-testid='edit-date-input']")
        assertThat(dateInput).isVisible()
        assertThat(dateInput).hasValue(testCase.expectedEditDateValue)

        // Verify edit mode time input uses locale format
        val timeInput = page.locator("[data-testid='edit-time-input']")
        assertThat(timeInput).isVisible()
        assertThat(timeInput).hasValue(testCase.expectedEditTimeValue)
    }

    companion object {
        @JvmStatic
        fun localeTestCases() =
            listOf(
                // US locale - 12-hour format with AM/PM
                LocaleTestCase(
                    localeTag = "en-US",
                    username = "us_user",
                    greeting = "US User",
                    taskTitle = "Active Task",
                    completedTaskTitle = "Completed Task",
                    expectedStartedAtText = "Mar 16, 02:00 PM", // 14:00 in 12-hour format
                    expectedActiveTimeRangeText = "02:00 PM - in progress", // 14:00 in 12-hour format
                    expectedCompletedTimeRangeText = "12:30 PM - 01:00 PM", // 12:30 - 13:00 in 12-hour format
                    expectedWeekRangeText = "Mar 11 - Mar 17",
                    expectedDayTitle = "Today",
                    expectedEditDateValue = "Mar 16, 2024",
                    expectedEditTimeValue = "02:00 PM",
                ),
                // UK locale - 24-hour format
                LocaleTestCase(
                    localeTag = "en-GB",
                    username = "uk_user",
                    greeting = "UK User",
                    taskTitle = "Active Task",
                    completedTaskTitle = "Completed Task",
                    expectedStartedAtText = "16 Mar, 14:00", // 14:00 in 24-hour format
                    expectedActiveTimeRangeText = "14:00 - in progress", // 14:00 in 24-hour format
                    expectedCompletedTimeRangeText = "12:30 - 13:00", // 12:30 - 13:00 in 24-hour format
                    expectedWeekRangeText = "11 Mar - 17 Mar",
                    expectedDayTitle = "Today",
                    expectedEditDateValue = "16 Mar 2024",
                    expectedEditTimeValue = "14:00",
                ),
                // Ukrainian locale - 24-hour format
                LocaleTestCase(
                    localeTag = "uk",
                    username = "ua_user",
                    greeting = "Українець",
                    taskTitle = "Українське завдання",
                    completedTaskTitle = "Завершене завдання",
                    expectedStartedAtText = "16 бер., 14:00", // 14:00 in 24-hour format with Ukrainian month
                    expectedActiveTimeRangeText = "14:00 - виконується", // 14:00 in 24-hour format
                    expectedCompletedTimeRangeText = "12:30 - 13:00", // 12:30 - 13:00 in 24-hour format
                    expectedWeekRangeText = "11 бер. - 17 бер.",
                    expectedDayTitle = "Сьогодні",
                    expectedEditDateValue = "16 бер. 2024 р.",
                    expectedEditTimeValue = "14:00",
                ),
            )
    }

    data class LocaleTestCase(
        val localeTag: String,
        val username: String,
        val greeting: String,
        val taskTitle: String,
        val completedTaskTitle: String,
        val expectedStartedAtText: String,
        val expectedActiveTimeRangeText: String,
        val expectedCompletedTimeRangeText: String,
        val expectedWeekRangeText: String,
        val expectedDayTitle: String,
        val expectedEditDateValue: String,
        val expectedEditTimeValue: String,
    )
}
