package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal
import java.time.Instant

@Controller("/api/time-log-entries")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Transactional
open class TimeLogEntryResource(
    private val timeLogEntryRepository: TimeLogEntryRepository,
    private val userRepository: UserRepository,
    private val timeService: TimeService
) {

    @Get
    open fun listEntries(
        @QueryValue startTime: Instant,
        @QueryValue endTime: Instant,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        val entries = timeLogEntryRepository.findByOwnerIdAndStartTimeGreaterThanEqualsAndStartTimeLessThanOrderByStartTimeDesc(
            userId,
            startTime,
            endTime
        )

        return HttpResponse.ok(
            TimeLogEntriesResponse(
                entries = entries.map { it.toDto() }
            )
        )
    }

    @Get("/active")
    open fun getActiveEntry(principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)

        return if (activeEntry != null) {
            HttpResponse.ok(ActiveLogEntryResponse(entry = activeEntry.toDto()))
        } else {
            HttpResponse.ok(ActiveLogEntryResponse(entry = null))
        }
    }

    @Post
    open fun createEntry(
        @Valid @Body request: CreateTimeLogEntryRequest,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        // Check if there's already an active log entry
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)
        if (activeEntry != null) {
            // If stopActiveEntry is true, stop the active entry first
            if (request.stopActiveEntry) {
                timeLogEntryRepository.update(
                    activeEntry.copy(endTime = timeService.now())
                )
            } else {
                return HttpResponse.badRequest(
                    TimeLogEntryErrorResponse("Cannot start a new log entry while another is active", "ACTIVE_ENTRY_EXISTS")
                )
            }
        }

        val newEntry = timeLogEntryRepository.save(
            TimeLogEntry(
                startTime = timeService.now(),
                endTime = null,
                title = request.title,
                ownerId = userId
            )
        )

        return HttpResponse.created(newEntry.toDto())
    }

    @Put("/{id}/stop")
    open fun stopEntry(
        @PathVariable id: Long,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, userId).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))

        // Check if already stopped
        if (entry.endTime != null) {
            return HttpResponse.badRequest(
                TimeLogEntryErrorResponse("Log entry is already stopped", "ENTRY_ALREADY_STOPPED")
            )
        }

        val stoppedEntry = timeLogEntryRepository.update(
            entry.copy(endTime = timeService.now())
        )

        return HttpResponse.ok(stoppedEntry.toDto())
    }

    @Put("/{id}")
    open fun updateEntry(
        @PathVariable id: Long,
        @Valid @Body request: UpdateTimeLogEntryRequest,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, userId).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))

        // Only active log entries can be updated
        if (entry.endTime != null) {
            return HttpResponse.badRequest(
                TimeLogEntryErrorResponse("Cannot update a stopped log entry", "ENTRY_ALREADY_STOPPED")
            )
        }

        // Validate that start time is not in the future
        if (request.startTime.isAfter(timeService.now())) {
            return HttpResponse.badRequest(
                TimeLogEntryErrorResponse("Start time cannot be in the future", "START_TIME_IN_FUTURE")
            )
        }

        val updatedEntry = timeLogEntryRepository.update(
            entry.copy(
                title = request.title,
                startTime = request.startTime
            )
        )

        return HttpResponse.ok(updatedEntry.toDto())
    }

    @Delete("/{id}")
    open fun deleteEntry(
        @PathVariable id: Long,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, userId).orElse(null)
            ?: return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))

        timeLogEntryRepository.delete(entry)

        return HttpResponse.ok(DeleteLogEntryResponse("Time log entry deleted successfully"))
    }

    private fun TimeLogEntry.toDto() = TimeLogEntryDto(
        id = requireNotNull(this.id) { "Log entry must have an ID" },
        startTime = this.startTime,
        endTime = this.endTime,
        title = this.title,
        ownerId = this.ownerId
    )
}

@Serdeable
@Introspected
data class TimeLogEntryDto(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val title: String,
    val ownerId: Long
)

@Serdeable
@Introspected
data class TimeLogEntriesResponse(
    val entries: List<TimeLogEntryDto>
)

@Serdeable
@Introspected
data class ActiveLogEntryResponse(
    val entry: TimeLogEntryDto?
)

@Serdeable
@Introspected
data class CreateTimeLogEntryRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String,
    val stopActiveEntry: Boolean = false
)

@Serdeable
@Introspected
data class UpdateTimeLogEntryRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String,
    val startTime: Instant
)

@Serdeable
@Introspected
data class DeleteLogEntryResponse(
    val message: String
)

@Serdeable
@Introspected
data class TimeLogEntryErrorResponse(
    val error: String,
    val errorCode: String
)
