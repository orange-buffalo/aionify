package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Hidden
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalTime

@Controller("/api-ui/users/goals-settings")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Transactional
@Hidden
open class GoalsSettingsResource(
    private val goalsSettingsService: GoalsSettingsService,
) {
    @Get
    open fun getGoalsSettings(currentUser: UserWithId): HttpResponse<GoalsSettingsResponse> {
        val settings = goalsSettingsService.getForUser(currentUser.id)
        return HttpResponse.ok(
            GoalsSettingsResponse(
                dailyGoal =
                    DailyGoalResponse(
                        enabled = settings.dailyEnabled,
                        goalMinutes = settings.dailyGoalMinutes,
                        typicalBreaks =
                            settings.breaks.map {
                                TypicalBreakResponse(
                                    from = it.fromTime.toString(),
                                    to = it.toTime.toString(),
                                )
                            },
                    ),
            ),
        )
    }

    @Put
    open fun updateGoalsSettings(
        @Valid @Body request: UpdateGoalsSettingsRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        val goalMinutes = request.dailyGoal.goalMinutes
        if (request.dailyGoal.enabled && goalMinutes <= 0) {
            return invalidDailyGoal()
        }

        val validatedBreaks = mutableListOf<DailyGoalBreakView>()
        request.dailyGoal.typicalBreaks.forEach { typicalBreak ->
            if (!typicalBreak.from.isBefore(typicalBreak.to)) {
                return invalidTypicalBreak()
            }
            validatedBreaks.add(DailyGoalBreakView(typicalBreak.from, typicalBreak.to))
        }

        val sortedBreaks = validatedBreaks.sortedBy { it.fromTime }
        sortedBreaks.zipWithNext().forEach { (currentBreak, nextBreak) ->
            if (!currentBreak.toTime.isBefore(nextBreak.fromTime)) {
                return invalidTypicalBreak()
            }
        }

        goalsSettingsService.saveForUser(
            userId = currentUser.id,
            dailyEnabled = request.dailyGoal.enabled,
            dailyGoalMinutes = goalMinutes,
            breaks = validatedBreaks,
        )

        return HttpResponse.ok(GoalsSettingsSuccessResponse("Goals settings updated successfully"))
    }

    private fun invalidDailyGoal() =
        HttpResponse.badRequest(
            GoalsSettingsErrorResponse("Invalid daily goal", "INVALID_DAILY_GOAL"),
        )

    private fun invalidTypicalBreak() =
        HttpResponse.badRequest(
            GoalsSettingsErrorResponse("Invalid typical break", "INVALID_TYPICAL_BREAK"),
        )
}

@Serdeable
@Introspected
data class GoalsSettingsResponse(
    val dailyGoal: DailyGoalResponse,
)

@Serdeable
@Introspected
data class DailyGoalResponse(
    val enabled: Boolean,
    val goalMinutes: Int,
    val typicalBreaks: List<TypicalBreakResponse>,
)

@Serdeable
@Introspected
data class TypicalBreakResponse(
    val from: String,
    val to: String,
)

@Serdeable
@Introspected
data class UpdateGoalsSettingsRequest(
    @field:NotNull
    val dailyGoal: UpdateDailyGoalRequest,
)

@Serdeable
@Introspected
data class UpdateDailyGoalRequest(
    val enabled: Boolean,
    @field:Min(0)
    val goalMinutes: Int,
    val typicalBreaks: List<UpdateTypicalBreakRequest> = emptyList(),
)

@Serdeable
@Introspected
data class UpdateTypicalBreakRequest(
    val from: LocalTime,
    val to: LocalTime,
)

@Serdeable
@Introspected
data class GoalsSettingsSuccessResponse(
    val message: String,
)

@Serdeable
@Introspected
data class GoalsSettingsErrorResponse(
    val error: String,
    val errorCode: String,
)
