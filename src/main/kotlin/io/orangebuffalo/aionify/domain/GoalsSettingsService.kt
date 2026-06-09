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

        return GoalsSettingsView(settings.dailyEnabled, settings.dailyGoalMinutes, breaks)
    }

    fun saveForUser(
        userId: Long,
        dailyEnabled: Boolean,
        dailyGoalMinutes: Int,
        breaks: List<DailyGoalBreakView>,
    ) {
        val settings = goalsSettingsRepository.findByUserId(userId).orElse(null)
        val persistedSettings =
            if (settings == null) {
                goalsSettingsRepository.save(
                    GoalsSettings(userId = userId, dailyEnabled = dailyEnabled, dailyGoalMinutes = dailyGoalMinutes),
                )
            } else {
                goalsSettingsRepository.update(
                    settings.copy(dailyEnabled = dailyEnabled, dailyGoalMinutes = dailyGoalMinutes),
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
) {
    companion object {
        fun default() = GoalsSettingsView(dailyEnabled = false, dailyGoalMinutes = 0, breaks = emptyList())
    }
}

data class DailyGoalBreakView(
    val fromTime: LocalTime,
    val toTime: LocalTime,
)
