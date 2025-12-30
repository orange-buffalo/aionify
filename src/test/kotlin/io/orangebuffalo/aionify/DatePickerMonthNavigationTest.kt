package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserSettings
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Tests for date picker month navigation.
 * Verifies that when navigating to different months, only the actual selected date is highlighted,
 * not the same day number in other months.
 */
@MicronautTest(transactional = false)
class DatePickerMonthNavigationTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    private lateinit var regularUser: User
    private lateinit var timeLogsPage: TimeLogsPageObject

    @BeforeEach
    fun setupTestData() {
        // Create test user with user settings
        regularUser = testUsers.createRegularUser("datePickerNavTestUser", "Date Picker Nav Test User")
        testDatabaseSupport.insert(UserSettings.create(userId = requireNotNull(regularUser.id)))
        timeLogsPage = TimeLogsPageObject(page)

        // Create a time entry on March 15, 2024 at 00:00 UTC
        // In Pacific/Auckland (UTC+13), this will be March 15, 13:00 local time
        testDatabaseSupport.insert(
            TimeLogEntry(
                startTime = Instant.parse("2024-03-15T00:00:00Z"),
                endTime = Instant.parse("2024-03-15T01:00:00Z"),
                title = "Test Entry",
                ownerId = requireNotNull(regularUser.id),
            ),
        )
    }

    @Test
    fun `should only highlight the actual selected date when navigating months`() {
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)

        // Click edit on the entry to open the edit form with date picker
        timeLogsPage.clickEditForEntry("Test Entry")

        // Click on the date picker to open the calendar
        page.locator("[data-testid='stopped-entry-edit-date-input']").click()

        // Wait for calendar to be visible
        val calendar = page.locator("[data-testid='calendar']")
        assertThat(calendar).isVisible()

        // The entry is on March 15, 2024, so we should see March 2024 in the calendar
        // and day 15 should be highlighted
        assertThat(page.locator("[data-testid='calendar']")).containsText("March 2024")

        // Check that day 15 in March has the selected styling (bg-primary class)
        // Get all buttons with text "15" in the calendar
        val day15Buttons = calendar.locator("button:has-text('15')")
        // There might be multiple (one for each week if day 15 appears), but at least one should be selected
        var foundSelected = false
        for (i in 0 until day15Buttons.count()) {
            val classAttr = day15Buttons.nth(i).getAttribute("class") ?: ""
            if (classAttr.contains("bg-primary")) {
                foundSelected = true
                break
            }
        }
        assertTrue(foundSelected, "Day 15 should be highlighted in March 2024")

        // Navigate to the next month (April 2024)
        page.locator("[data-testid='calendar'] button:has-text('›')").click()

        // Calendar should now show April 2024
        assertThat(page.locator("[data-testid='calendar']")).containsText("April 2024")

        // In April, day 15 should NOT be highlighted because the actual selected date is March 15
        // Check that no day 15 in April has the selected styling
        val day15InApril = calendar.locator("button:has-text('15')")
        var foundSelectedInApril = false
        for (i in 0 until day15InApril.count()) {
            val classAttr = day15InApril.nth(i).getAttribute("class") ?: ""
            if (classAttr.contains("bg-primary")) {
                foundSelectedInApril = true
                break
            }
        }
        // This should be false, but currently the bug causes it to be true
        assertFalse(foundSelectedInApril, "Day 15 should NOT be highlighted in April 2024")

        // Navigate to the previous month (back to March 2024)
        page.locator("[data-testid='calendar'] button:has-text('‹')").click()

        // Calendar should show March 2024 again
        assertThat(page.locator("[data-testid='calendar']")).containsText("March 2024")

        // Day 15 should be highlighted again in March
        val day15BackInMarch = calendar.locator("button:has-text('15')")
        var foundSelectedBackInMarch = false
        for (i in 0 until day15BackInMarch.count()) {
            val classAttr = day15BackInMarch.nth(i).getAttribute("class") ?: ""
            if (classAttr.contains("bg-primary")) {
                foundSelectedBackInMarch = true
                break
            }
        }
        assertTrue(foundSelectedBackInMarch, "Day 15 should be highlighted in March 2024 after navigating back")

        // Navigate two months back to January 2024
        page.locator("[data-testid='calendar'] button:has-text('‹')").click() // Feb
        page.locator("[data-testid='calendar'] button:has-text('‹')").click() // Jan

        // Calendar should show January 2024
        assertThat(page.locator("[data-testid='calendar']")).containsText("January 2024")

        // In January, day 15 should NOT be highlighted because the actual selected date is March 15
        val day15InJanuary = calendar.locator("button:has-text('15')")
        var foundSelectedInJanuary = false
        for (i in 0 until day15InJanuary.count()) {
            val classAttr = day15InJanuary.nth(i).getAttribute("class") ?: ""
            if (classAttr.contains("bg-primary")) {
                foundSelectedInJanuary = true
                break
            }
        }
        assertFalse(foundSelectedInJanuary, "Day 15 should NOT be highlighted in January 2024")
    }
}
