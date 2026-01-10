package io.orangebuffalo.aionify.timelogs

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.orangebuffalo.aionify.*
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.UserSettings
import io.orangebuffalo.aionify.domain.UserSettingsRepository
import io.orangebuffalo.aionify.domain.WeekDay
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Tests for Time Logs page respecting user's start of week preference.
 * Verifies that weekly grouping and date picker follow the configured start of week.
 */
class TimeLogsStartOfWeekTest : TimeLogsPageTestBase() {
    @Inject
    lateinit var userSettingsRepository: UserSettingsRepository

    @BeforeEach
    fun setupStartOfWeekTest() {
        // Create user settings for test user (base class creates the user)
        testDatabaseSupport.insert(UserSettings.create(userId = requireNotNull(testUser.id)))
    }

    @Test
    fun `should display week range starting on Monday when start of week is Monday`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT
        // Default is Monday, so week should be Mon Mar 11 - Sun Mar 17

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify week range starts on Monday
        val weekRange = page.locator("[data-testid='week-range']")
        assertThat(weekRange).containsText("11 Mar - 17 Mar")
    }

    @Test
    fun `should display week range starting on Sunday when start of week is Sunday`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT
        // Change start of week to Sunday, so week should be Sun Mar 10 - Sat Mar 16

        // Update user settings to start week on Sunday
        testDatabaseSupport.inTransaction {
            val settings = userSettingsRepository.findByUserId(requireNotNull(testUser.id)).orElseThrow()
            userSettingsRepository.update(settings.copy(startOfWeek = WeekDay.SUNDAY))
        }

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify week range starts on Sunday
        val weekRange = page.locator("[data-testid='week-range']")
        assertThat(weekRange).containsText("10 Mar - 16 Mar")
    }

    @Test
    fun `should display week range starting on Wednesday when start of week is Wednesday`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT
        // Change start of week to Wednesday, so week should be Wed Mar 13 - Tue Mar 19

        // Update user settings to start week on Wednesday
        testDatabaseSupport.inTransaction {
            val settings = userSettingsRepository.findByUserId(requireNotNull(testUser.id)).orElseThrow()
            userSettingsRepository.update(settings.copy(startOfWeek = WeekDay.WEDNESDAY))
        }

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify week range starts on Wednesday
        val weekRange = page.locator("[data-testid='week-range']")
        assertThat(weekRange).containsText("13 Mar - 19 Mar")
    }

    @Test
    fun `should group entries by week starting on Sunday when configured`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT
        // Change start of week to Sunday, so week should be Sun Mar 10 - Sat Mar 16

        // Update user settings to start week on Sunday
        testDatabaseSupport.inTransaction {
            val settings = userSettingsRepository.findByUserId(requireNotNull(testUser.id)).orElseThrow()
            userSettingsRepository.update(settings.copy(startOfWeek = WeekDay.SUNDAY))
        }

        // Create entries:
        // - One on Saturday Mar 9 (before the week starts on Sunday Mar 10) - should NOT show
        // - One on Sunday Mar 10 (first day of week) - should show
        // - One on Saturday Mar 16 (last day of week) - should show
        // - One on Sunday Mar 17 (after week ends) - should NOT show

        val saturdayBeforeWeek = timeInTestTz("2024-03-09", "03:30") // Sat Mar 9
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = saturdayBeforeWeek,
                endTime = saturdayBeforeWeek.withLocalTime("04:30"),
                title = "Before Week Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        val sundayStartOfWeek = timeInTestTz("2024-03-10", "03:30") // Sun Mar 10
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = sundayStartOfWeek,
                endTime = sundayStartOfWeek.withLocalTime("04:30"),
                title = "Sunday Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        val saturdayEndOfWeek = baseTime // Sat Mar 16
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = saturdayEndOfWeek,
                endTime = saturdayEndOfWeek.withLocalTime("04:30"),
                title = "Saturday Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        val sundayAfterWeek = baseTime.withLocalDate("2024-03-17") // Sun Mar 17
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = sundayAfterWeek,
                endTime = sundayAfterWeek.withLocalTime("04:30"),
                title = "After Week Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify only Sunday and Saturday entries are shown (within the week)
        val dayTitles = page.locator("[data-testid='day-title']")
        assertThat(dayTitles).containsText(arrayOf("Today", "10 Mar")) // Saturday (Today) and Sunday

        // Verify the entries shown
        val entryTitles = page.locator("[data-testid='entry-title']")
        assertThat(entryTitles).containsText(arrayOf("Saturday Entry", "Sunday Entry"))
    }

    @Test
    fun `should group entries by week starting on Monday when configured`() {
        // Set base time: Saturday, March 16, 2024 at 03:30:00 NZDT
        val baseTime = setBaseTime("2024-03-16", "03:30")

        // baseTime is Saturday, March 16, 2024 at 03:30 NZDT
        // Default start of week is Monday, so week should be Mon Mar 11 - Sun Mar 17

        // Create entries:
        // - One on Sunday Mar 10 (before the week starts on Monday Mar 11) - should NOT show
        // - One on Monday Mar 11 (first day of week) - should show
        // - One on Sunday Mar 17 (last day of week) - should show
        // - One on Monday Mar 18 (after week ends) - should NOT show

        val sundayBeforeWeek = timeInTestTz("2024-03-10", "03:30") // Sun Mar 10
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = sundayBeforeWeek,
                endTime = sundayBeforeWeek.withLocalTime("04:30"),
                title = "Before Week Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        val mondayStartOfWeek = timeInTestTz("2024-03-11", "03:30") // Mon Mar 11
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = mondayStartOfWeek,
                endTime = mondayStartOfWeek.withLocalTime("04:30"),
                title = "Monday Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        val sundayEndOfWeek = baseTime.withLocalDate("2024-03-17") // Sun Mar 17
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = sundayEndOfWeek,
                endTime = sundayEndOfWeek.withLocalTime("04:30"),
                title = "Sunday Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        val mondayAfterWeek = timeInTestTz("2024-03-18", "03:30") // Mon Mar 18
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = mondayAfterWeek,
                endTime = mondayAfterWeek.withLocalTime("04:30"),
                title = "After Week Entry",
                ownerId = requireNotNull(testUser.id),
            ),
        )

        loginViaToken("/portal/time-logs", testUser, testAuthSupport)

        // Verify only Monday and Sunday entries are shown (within the week)
        // Should show: Today (Saturday), 17 Mar (Sunday), 11 Mar (Monday)
        val dayTitles = page.locator("[data-testid='day-title']")
        assertThat(dayTitles).hasCount(2) // Only Sunday and Monday

        // Verify the entries shown
        val entryTitles = page.locator("[data-testid='entry-title']")
        assertThat(entryTitles).containsText(arrayOf("Sunday Entry", "Monday Entry"))
    }
}
