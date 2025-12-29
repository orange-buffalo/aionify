package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserSettings
import io.orangebuffalo.aionify.domain.UserSettingsRepository
import io.orangebuffalo.aionify.domain.WeekDay
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Tests for date picker respecting user's start of week preference.
 * Verifies that the calendar grid starts on the configured day of the week.
 */
@MicronautTest(transactional = false)
class DatePickerStartOfWeekTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var userSettingsRepository: UserSettingsRepository

    private lateinit var regularUser: User
    private lateinit var timeLogsPage: TimeLogsPageObject

    @BeforeEach
    fun setupTestData() {
        // Create test user with user settings
        regularUser = testUsers.createRegularUser("datePickerTestUser", "Date Picker Test User")
        testDatabaseSupport.insert(UserSettings.create(userId = requireNotNull(regularUser.id)))
        timeLogsPage = TimeLogsPageObject(page)

        // Create a time entry so we can test editing and opening date picker
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-03-15T14:00:00Z"),
                endTime = Instant.parse("2024-03-15T15:00:00Z"),
                title = "Test Entry",
                ownerId = requireNotNull(regularUser.id),
            ),
        )
    }

    @Test
    fun `date picker calendar should start week on Monday by default`() {
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)

        // Click edit on the entry to open the edit form with date picker
        timeLogsPage.clickEditForEntry("Test Entry")

        // Click on the date picker to open the calendar
        page.locator("[data-testid='stopped-entry-edit-date-input']").click()

        // Wait for calendar to be visible
        val calendar = page.locator("[data-testid='calendar']")
        assertThat(calendar).isVisible()

        // Get the day name headers in the calendar
        val dayHeaders = page.locator("[data-testid='calendar'] thead th")

        // Verify the first day is Monday (MON)
        assertThat(dayHeaders.first()).containsText("MON")

        // Verify all 7 days are present in order: MON, TUE, WED, THU, FRI, SAT, SUN
        assertThat(dayHeaders).hasCount(7)
    }

    @Test
    fun `date picker calendar should start week on Sunday when configured`() {
        // Update user settings to start week on Sunday
        testDatabaseSupport.inTransaction {
            val settings = userSettingsRepository.findByUserId(requireNotNull(regularUser.id)).orElseThrow()
            userSettingsRepository.update(settings.copy(startOfWeek = WeekDay.SUNDAY))
        }

        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)

        // Click edit on the entry to open the edit form with date picker
        timeLogsPage.clickEditForEntry("Test Entry")

        // Click on the date picker to open the calendar
        page.locator("[data-testid='stopped-entry-edit-date-input']").click()

        // Wait for calendar to be visible
        val calendar = page.locator("[data-testid='calendar']")
        assertThat(calendar).isVisible()

        // Get the day name headers in the calendar
        val dayHeaders = page.locator("[data-testid='calendar'] thead th")

        // Verify the first day is Sunday (SUN)
        assertThat(dayHeaders.first()).containsText("SUN")

        // Verify all 7 days are present
        assertThat(dayHeaders).hasCount(7)
    }

    @Test
    fun `date picker calendar should start week on Saturday when configured`() {
        // Update user settings to start week on Saturday
        testDatabaseSupport.inTransaction {
            val settings = userSettingsRepository.findByUserId(requireNotNull(regularUser.id)).orElseThrow()
            userSettingsRepository.update(settings.copy(startOfWeek = WeekDay.SATURDAY))
        }

        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)

        // Click edit on the entry to open the edit form with date picker
        timeLogsPage.clickEditForEntry("Test Entry")

        // Click on the date picker to open the calendar
        page.locator("[data-testid='stopped-entry-edit-date-input']").click()

        // Wait for calendar to be visible
        val calendar = page.locator("[data-testid='calendar']")
        assertThat(calendar).isVisible()

        // Get the day name headers in the calendar
        val dayHeaders = page.locator("[data-testid='calendar'] thead th")

        // Verify the first day is Saturday (SAT)
        assertThat(dayHeaders.first()).containsText("SAT")

        // Verify all 7 days are present
        assertThat(dayHeaders).hasCount(7)
    }

    @Test
    fun `date picker calendar should start week on Wednesday when configured`() {
        // Update user settings to start week on Wednesday
        testDatabaseSupport.inTransaction {
            val settings = userSettingsRepository.findByUserId(requireNotNull(regularUser.id)).orElseThrow()
            userSettingsRepository.update(settings.copy(startOfWeek = WeekDay.WEDNESDAY))
        }

        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)

        // Click edit on the entry to open the edit form with date picker
        timeLogsPage.clickEditForEntry("Test Entry")

        // Click on the date picker to open the calendar
        page.locator("[data-testid='stopped-entry-edit-date-input']").click()

        // Wait for calendar to be visible
        val calendar = page.locator("[data-testid='calendar']")
        assertThat(calendar).isVisible()

        // Get the day name headers in the calendar
        val dayHeaders = page.locator("[data-testid='calendar'] thead th")

        // Verify the first day is Wednesday (WED)
        assertThat(dayHeaders.first()).containsText("WED")

        // Verify all 7 days are present
        assertThat(dayHeaders).hasCount(7)
    }
}
