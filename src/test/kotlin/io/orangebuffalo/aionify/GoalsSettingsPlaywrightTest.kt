package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.DailyGoalBreakRepository
import io.orangebuffalo.aionify.domain.GoalsSettingsRepository
import io.orangebuffalo.aionify.domain.User
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

@MicronautTest(transactional = false)
class GoalsSettingsPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var goalsSettingsRepository: GoalsSettingsRepository

    @Inject
    lateinit var dailyGoalBreakRepository: DailyGoalBreakRepository

    private lateinit var regularUser: User

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser("goalsSettingsUser", "Goals Settings User")
    }

    private fun navigateToSettingsViaToken() {
        loginViaToken("/portal/settings", regularUser, testAuthSupport)
    }

    @Test
    fun `should display goals management panel and hide goal sections by default`() {
        navigateToSettingsViaToken()

        assertThat(page.locator("[data-testid='settings-page']")).isVisible()
        assertThat(page.locator("[data-testid='goals-title']")).isVisible()
        assertThat(page.locator("[data-testid='goals-description']")).isVisible()
        assertThat(page.locator("[data-testid='daily-goal-panel']")).isVisible()
        assertThat(page.locator("[data-testid='daily-goal-toggle']")).isVisible()
        assertThat(page.locator("[data-testid='daily-goal-toggle']")).hasAttribute("data-state", "unchecked")
        assertThat(page.locator("[data-testid='daily-goal-section']")).not().isVisible()
        assertThat(page.locator("[data-testid='weekly-goal-panel']")).isVisible()
        assertThat(page.locator("[data-testid='weekly-goal-toggle']")).isVisible()
        assertThat(page.locator("[data-testid='weekly-goal-toggle']")).hasAttribute("data-state", "unchecked")
        assertThat(page.locator("[data-testid='weekly-goal-section']")).not().isVisible()
        assertThat(page.locator("[data-testid='save-goals-button']")).isVisible()
        captureUiReviewScreenshot("goals-default")
    }

    @Test
    fun `should allow configuring and persisting goal settings`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='daily-goal-toggle']").click()
        assertThat(page.locator("[data-testid='daily-goal-section']")).isVisible()

        page.locator("[data-testid='daily-goal-hours-input']").fill("7")
        page.locator("[data-testid='daily-goal-minutes-input']").fill("45")

        page.locator("[data-testid='daily-goal-add-break-button']").click()
        page.locator("[data-testid='daily-goal-add-break-button']").click()
        page.locator("[data-testid='daily-goal-add-break-button']").click()

        page.locator("[data-testid='daily-goal-break-from-0-input']").fill("12:00")
        page.locator("[data-testid='daily-goal-break-to-0-input']").fill("12:30")
        page.locator("[data-testid='daily-goal-break-from-1-input']").fill("15:00")
        page.locator("[data-testid='daily-goal-break-to-1-input']").fill("15:15")
        page.locator("[data-testid='daily-goal-break-from-2-input']").fill("16:00")
        page.locator("[data-testid='daily-goal-break-to-2-input']").fill("16:10")
        page.locator("[data-testid='daily-goal-break-delete-1']").click()

        assertThat(page.locator("[data-testid='daily-goal-break-row-0']")).isVisible()
        assertThat(page.locator("[data-testid='daily-goal-break-row-1']")).isVisible()
        assertThat(page.locator("[data-testid='daily-goal-break-row-2']")).not().isVisible()

        page.locator("[data-testid='weekly-goal-toggle']").click()
        assertThat(page.locator("[data-testid='weekly-goal-section']")).isVisible()
        assertThat(page.locator("[data-testid='weekly-goal-working-day-MONDAY']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-FRIDAY']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-SATURDAY']")).hasAttribute("data-state", "unchecked")
        page.locator("[data-testid='weekly-goal-hours-input']").fill("38")
        page.locator("[data-testid='weekly-goal-minutes-input']").fill("15")
        page.locator("[data-testid='weekly-goal-working-day-MONDAY']").click()
        page.locator("[data-testid='weekly-goal-working-day-WEDNESDAY']").click()
        page.locator("[data-testid='weekly-goal-working-day-FRIDAY']").click()
        page.locator("[data-testid='weekly-goal-working-day-SATURDAY']").click()

        page.locator("[data-testid='save-goals-button']").click()

        val successMessage = page.locator("[data-testid='goals-settings-success']")
        assertThat(successMessage).isVisible()
        assertThat(successMessage).containsText("Goals settings updated successfully")
        captureUiReviewScreenshot("goals-configured")
        captureUiReviewScreenshot("goals-configured-mobile", viewportWidth = 390)

        testDatabaseSupport.inTransaction {
            val settings = goalsSettingsRepository.findByUserId(requireNotNull(regularUser.id)).orElseThrow()
            assertTrue(settings.dailyEnabled)
            assertEquals(465, settings.dailyGoalMinutes)
            assertTrue(settings.weeklyEnabled)
            assertEquals(2_295, settings.weeklyGoalMinutes)
            assertEquals("TUESDAY,THURSDAY,SATURDAY", settings.weeklyWorkingDays)

            val breaks = dailyGoalBreakRepository.findByGoalsSettingsIdOrderBySortOrderAsc(requireNotNull(settings.id))
            assertEquals(2, breaks.size)
            assertEquals(LocalTime.parse("12:00"), breaks[0].fromTime)
            assertEquals(LocalTime.parse("12:30"), breaks[0].toTime)
            assertEquals(LocalTime.parse("16:00"), breaks[1].fromTime)
            assertEquals(LocalTime.parse("16:10"), breaks[1].toTime)
        }

        page.navigate("$baseUrl/portal/settings")
        assertThat(page.locator("[data-testid='settings-page']")).isVisible()
        assertThat(page.locator("[data-testid='daily-goal-section']")).isVisible()
        assertThat(page.locator("[data-testid='daily-goal-toggle']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='daily-goal-hours-input']")).hasValue("7")
        assertThat(page.locator("[data-testid='daily-goal-minutes-input']")).hasValue("45")
        assertThat(page.locator("[data-testid='weekly-goal-section']")).isVisible()
        assertThat(page.locator("[data-testid='weekly-goal-toggle']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='weekly-goal-hours-input']")).hasValue("38")
        assertThat(page.locator("[data-testid='weekly-goal-minutes-input']")).hasValue("15")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-MONDAY']")).hasAttribute("data-state", "unchecked")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-TUESDAY']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-THURSDAY']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-SATURDAY']")).hasAttribute("data-state", "checked")
        assertTrue(page.locator("[data-testid='daily-goal-break-from-0-input']").inputValue().startsWith("12:00"))
        assertTrue(page.locator("[data-testid='daily-goal-break-to-0-input']").inputValue().startsWith("12:30"))
        assertTrue(page.locator("[data-testid='daily-goal-break-from-1-input']").inputValue().startsWith("16:00"))
        assertTrue(page.locator("[data-testid='daily-goal-break-to-1-input']").inputValue().startsWith("16:10"))
        assertThat(page.locator("[data-testid='daily-goal-break-row-2']")).not().isVisible()
    }

    @Test
    fun `should discard unsaved goals changes and reload persisted state`() {
        navigateToSettingsViaToken()

        page.locator("[data-testid='daily-goal-toggle']").click()
        assertThat(page.locator("[data-testid='daily-goal-section']")).isVisible()

        page.locator("[data-testid='daily-goal-hours-input']").fill("6")
        page.locator("[data-testid='daily-goal-minutes-input']").fill("30")
        page.locator("[data-testid='daily-goal-add-break-button']").click()
        page.locator("[data-testid='daily-goal-break-from-0-input']").fill("11:00")
        page.locator("[data-testid='daily-goal-break-to-0-input']").fill("11:15")
        page.locator("[data-testid='weekly-goal-toggle']").click()
        assertThat(page.locator("[data-testid='weekly-goal-section']")).isVisible()
        page.locator("[data-testid='weekly-goal-hours-input']").fill("30")
        page.locator("[data-testid='weekly-goal-minutes-input']").fill("0")
        page.locator("[data-testid='weekly-goal-working-day-FRIDAY']").click()
        page.locator("[data-testid='save-goals-button']").click()

        assertThat(page.locator("[data-testid='goals-settings-success']")).isVisible()

        page.locator("[data-testid='daily-goal-hours-input']").fill("9")
        page.locator("[data-testid='daily-goal-minutes-input']").fill("5")
        page.locator("[data-testid='daily-goal-break-from-0-input']").fill("13:00")
        page.locator("[data-testid='daily-goal-break-to-0-input']").fill("13:25")
        page.locator("[data-testid='daily-goal-add-break-button']").click()
        page.locator("[data-testid='daily-goal-break-from-1-input']").fill("17:00")
        page.locator("[data-testid='daily-goal-break-to-1-input']").fill("17:10")
        page.locator("[data-testid='weekly-goal-hours-input']").fill("12")
        page.locator("[data-testid='weekly-goal-minutes-input']").fill("45")
        page.locator("[data-testid='weekly-goal-working-day-MONDAY']").click()
        page.locator("[data-testid='weekly-goal-working-day-SATURDAY']").click()

        page.locator("[data-testid='discard-goals-button']").click()

        assertThat(page.locator("[data-testid='daily-goal-toggle']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='daily-goal-hours-input']")).hasValue("6")
        assertThat(page.locator("[data-testid='daily-goal-minutes-input']")).hasValue("30")
        assertTrue(page.locator("[data-testid='daily-goal-break-from-0-input']").inputValue().startsWith("11:00"))
        assertTrue(page.locator("[data-testid='daily-goal-break-to-0-input']").inputValue().startsWith("11:15"))
        assertThat(page.locator("[data-testid='daily-goal-break-row-1']")).not().isVisible()
        assertThat(page.locator("[data-testid='weekly-goal-toggle']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='weekly-goal-hours-input']")).hasValue("30")
        assertThat(page.locator("[data-testid='weekly-goal-minutes-input']")).hasValue("0")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-MONDAY']")).hasAttribute("data-state", "checked")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-FRIDAY']")).hasAttribute("data-state", "unchecked")
        assertThat(page.locator("[data-testid='weekly-goal-working-day-SATURDAY']")).hasAttribute("data-state", "unchecked")

        testDatabaseSupport.inTransaction {
            val settings = goalsSettingsRepository.findByUserId(requireNotNull(regularUser.id)).orElseThrow()
            assertTrue(settings.dailyEnabled)
            assertEquals(390, settings.dailyGoalMinutes)
            assertTrue(settings.weeklyEnabled)
            assertEquals(1_800, settings.weeklyGoalMinutes)
            assertEquals("MONDAY,TUESDAY,WEDNESDAY,THURSDAY", settings.weeklyWorkingDays)

            val breaks = dailyGoalBreakRepository.findByGoalsSettingsIdOrderBySortOrderAsc(requireNotNull(settings.id))
            assertEquals(1, breaks.size)
            assertEquals(LocalTime.parse("11:00"), breaks[0].fromTime)
            assertEquals(LocalTime.parse("11:15"), breaks[0].toTime)
        }
    }
}
