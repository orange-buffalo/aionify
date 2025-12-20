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

@Controller("/api/time-entries")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Transactional
open class TimeEntryResource(
    private val timeEntryRepository: TimeEntryRepository,
    private val userRepository: UserRepository
) {

    @Get
    open fun listEntries(
        @QueryValue startTime: Instant,
        @QueryValue endTime: Instant,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        val entries = timeEntryRepository.findByOwnerIdAndStartTimeGreaterThanEqualsAndStartTimeLessThanOrderByStartTimeDesc(
            userId,
            startTime,
            endTime
        )

        return HttpResponse.ok(
            TimeEntriesResponse(
                entries = entries.map { it.toDto() }
            )
        )
    }

    @Get("/active")
    open fun getActiveEntry(principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        val activeEntry = timeEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)

        return if (activeEntry != null) {
            HttpResponse.ok(ActiveEntryResponse(entry = activeEntry.toDto()))
        } else {
            HttpResponse.ok(ActiveEntryResponse(entry = null))
        }
    }

    @Post
    open fun createEntry(
        @Valid @Body request: CreateTimeEntryRequest,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        // Check if there's already an active entry
        val activeEntry = timeEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)
        if (activeEntry != null) {
            return HttpResponse.badRequest(
                TimeEntryErrorResponse("Cannot start a new entry while another is active", "ACTIVE_ENTRY_EXISTS")
            )
        }

        val newEntry = timeEntryRepository.save(
            TimeEntry(
                startTime = Instant.now(),
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
            ?: return HttpResponse.unauthorized<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        // Find the entry and verify ownership
        val entry = timeEntryRepository.findByIdAndOwnerId(id, userId).orElse(null)
            ?: return HttpResponse.notFound<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("Time entry not found", "ENTRY_NOT_FOUND"))

        // Check if already stopped
        if (entry.endTime != null) {
            return HttpResponse.badRequest(
                TimeEntryErrorResponse("Entry is already stopped", "ENTRY_ALREADY_STOPPED")
            )
        }

        val stoppedEntry = timeEntryRepository.update(
            entry.copy(endTime = Instant.now())
        )

        return HttpResponse.ok(stoppedEntry.toDto())
    }

    @Delete("/{id}")
    open fun deleteEntry(
        @PathVariable id: Long,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("User not found", "USER_NOT_FOUND"))

        val userId = requireNotNull(user.id) { "User must have an ID" }

        // Find the entry and verify ownership
        val entry = timeEntryRepository.findByIdAndOwnerId(id, userId).orElse(null)
            ?: return HttpResponse.notFound<TimeEntryErrorResponse>()
                .body(TimeEntryErrorResponse("Time entry not found", "ENTRY_NOT_FOUND"))

        timeEntryRepository.delete(entry)

        return HttpResponse.ok(DeleteEntryResponse("Time entry deleted successfully"))
    }

    private fun TimeEntry.toDto() = TimeEntryDto(
        id = requireNotNull(this.id) { "Entry must have an ID" },
        startTime = this.startTime,
        endTime = this.endTime,
        title = this.title,
        ownerId = this.ownerId
    )
}

@Serdeable
@Introspected
data class TimeEntryDto(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val title: String,
    val ownerId: Long
)

@Serdeable
@Introspected
data class TimeEntriesResponse(
    val entries: List<TimeEntryDto>
)

@Serdeable
@Introspected
data class ActiveEntryResponse(
    val entry: TimeEntryDto?
)

@Serdeable
@Introspected
data class CreateTimeEntryRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String
)

@Serdeable
@Introspected
data class DeleteEntryResponse(
    val message: String
)

@Serdeable
@Introspected
data class TimeEntryErrorResponse(
    val error: String,
    val errorCode: String
)
