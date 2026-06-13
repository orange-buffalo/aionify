package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton
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
            weeklyWorkingDays = settings.weeklyWorkingDays.sortedBy { it.ordinal },
        )
    }

    fun saveForUser(
        userId: Long,
        dailyEnabled: Boolean,
        dailyGoalMinutes: Int,
        breaks: List<DailyGoalBreakView>,
        weeklyEnabled: Boolean,
        weeklyGoalMinutes: Int,
        weeklyWorkingDays: Set<WeekDay>,
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
                        weeklyWorkingDays = weeklyWorkingDays,
                    ),
                )
            } else {
                goalsSettingsRepository.update(
                    settings.copy(
                        dailyEnabled = dailyEnabled,
                        dailyGoalMinutes = dailyGoalMinutes,
                        weeklyEnabled = weeklyEnabled,
                        weeklyGoalMinutes = weeklyGoalMinutes,
                        weeklyWorkingDays = weeklyWorkingDays,
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
}

data class GoalsSettingsView(
    val dailyEnabled: Boolean,
    val dailyGoalMinutes: Int,
    val breaks: List<DailyGoalBreakView>,
    val weeklyEnabled: Boolean,
    val weeklyGoalMinutes: Int,
    val weeklyWorkingDays: List<WeekDay>,
) {
    companion object {
        val defaultWeeklyWorkingDays =
            listOf(
                WeekDay.MONDAY,
                WeekDay.TUESDAY,
                WeekDay.WEDNESDAY,
                WeekDay.THURSDAY,
                WeekDay.FRIDAY,
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
