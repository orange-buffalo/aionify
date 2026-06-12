package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
import java.time.DayOfWeek
import java.time.LocalTime

@Singleton
class GoalsSettingsService(
    private val goalsSettingsRepository: GoalsSettingsRepository,
    private val dailyGoalBreakRepository: DailyGoalBreakRepository,
) {
    fun getForUser(userId: Long): GoalsSettingsView {
        val settings = goalsSettingsRepository.findByUserId(userId).orElse(null)
        if (settings == null) {
            return GoalsSettingsView.default()
        }

        val breaks =
            requireNotNull(settings.id)
                .let(dailyGoalBreakRepository::findByGoalsSettingsIdOrderBySortOrderAsc)
                .map { DailyGoalBreakView(it.fromTime, it.toTime) }

        return GoalsSettingsView(
            dailyEnabled = settings.dailyEnabled,
            dailyGoalMinutes = settings.dailyGoalMinutes,
            breaks = breaks,
            weeklyEnabled = settings.weeklyEnabled,
            weeklyGoalMinutes = settings.weeklyGoalMinutes,
            weeklyWorkingDays = parseWeeklyWorkingDays(settings.weeklyWorkingDays),
        )
    }

    fun saveForUser(
        userId: Long,
        dailyEnabled: Boolean,
        dailyGoalMinutes: Int,
        breaks: List<DailyGoalBreakView>,
        weeklyEnabled: Boolean,
        weeklyGoalMinutes: Int,
        weeklyWorkingDays: List<DayOfWeek>,
    ) {
        val settings = goalsSettingsRepository.findByUserId(userId).orElse(null)
        val persistedSettings =
            if (settings == null) {
                goalsSettingsRepository.save(
                    GoalsSettings(
                        userId = userId,
                        dailyEnabled = dailyEnabled,
                        dailyGoalMinutes = dailyGoalMinutes,
                        weeklyEnabled = weeklyEnabled,
                        weeklyGoalMinutes = weeklyGoalMinutes,
                        weeklyWorkingDays = formatWeeklyWorkingDays(weeklyWorkingDays),
                    ),
                )
            } else {
                goalsSettingsRepository.update(
                    settings.copy(
                        dailyEnabled = dailyEnabled,
                        dailyGoalMinutes = dailyGoalMinutes,
                        weeklyEnabled = weeklyEnabled,
                        weeklyGoalMinutes = weeklyGoalMinutes,
                        weeklyWorkingDays = formatWeeklyWorkingDays(weeklyWorkingDays),
                    ),
                )
            }

        val settingsId = requireNotNull(persistedSettings.id)
        dailyGoalBreakRepository.deleteByGoalsSettingsId(settingsId)
        breaks.forEachIndexed { index, goalBreak ->
            dailyGoalBreakRepository.save(
                DailyGoalBreak(
                    goalsSettingsId = settingsId,
                    sortOrder = index,
                    fromTime = goalBreak.fromTime,
                    toTime = goalBreak.toTime,
                ),
            )
        }
    }

    private fun parseWeeklyWorkingDays(value: String): List<DayOfWeek> =
        value
            .split(',')
            .filter { it.isNotBlank() }
            .map { DayOfWeek.valueOf(it) }

    private fun formatWeeklyWorkingDays(days: List<DayOfWeek>): String = days.joinToString(",") { it.name }
}

data class GoalsSettingsView(
    val dailyEnabled: Boolean,
    val dailyGoalMinutes: Int,
    val breaks: List<DailyGoalBreakView>,
    val weeklyEnabled: Boolean,
    val weeklyGoalMinutes: Int,
    val weeklyWorkingDays: List<DayOfWeek>,
) {
    companion object {
        val defaultWeeklyWorkingDays =
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            )

        fun default() =
            GoalsSettingsView(
                dailyEnabled = false,
                dailyGoalMinutes = 0,
                breaks = emptyList(),
                weeklyEnabled = false,
                weeklyGoalMinutes = 0,
                weeklyWorkingDays = defaultWeeklyWorkingDays,
            )
    }
}

data class DailyGoalBreakView(
    val fromTime: LocalTime,
    val toTime: LocalTime,
)
