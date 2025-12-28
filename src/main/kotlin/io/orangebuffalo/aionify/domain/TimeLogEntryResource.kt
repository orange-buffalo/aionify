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

    private val log = org.slf4j.LoggerFactory.getLogger(TimeLogEntryResource::class.java)

    @Get
    open fun listEntries(
        @QueryValue startTime: Instant,
        @QueryValue endTime: Instant,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("List entries failed: user not authenticated")
            return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("List entries failed: user not found: {}", userName)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        log.debug("Listing time log entries for user: {}, startTime: {}, endTime: {}", userName, startTime, endTime)
        
        val entries = timeLogEntryRepository.findByOwnerIdAndStartTimeGreaterThanEqualsAndStartTimeLessThanOrderByStartTimeDesc(
            userId,
            startTime,
            endTime
        )

        log.trace("Found {} time log entries for user: {}", entries.size, userName)

        return HttpResponse.ok(
            TimeLogEntriesResponse(
                entries = entries.map { it.toDto() }
            )
        )
    }

    @Get("/active")
    open fun getActiveEntry(principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Get active entry failed: user not authenticated")
            return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Get active entry failed: user not found: {}", userName)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)

        if (activeEntry != null) {
            log.trace("Found active time log entry for user: {}, id: {}", userName, activeEntry.id)
        } else {
            log.trace("No active time log entry for user: {}", userName)
        }

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
        if (userName == null) {
            log.debug("Create entry failed: user not authenticated")
            return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Create entry failed: user not found: {}", userName)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        log.debug("Creating time log entry for user: {}, title: {}", userName, request.title)

        // Check if there's already an active log entry
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(userId).orElse(null)
        if (activeEntry != null) {
            // If stopActiveEntry is true, stop the active entry first
            if (request.stopActiveEntry) {
                log.debug("Stopping active entry before creating new one for user: {}", userName)
                timeLogEntryRepository.update(
                    activeEntry.copy(endTime = timeService.now())
                )
            } else {
                log.debug("Create entry failed: active entry exists for user: {}", userName)
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
                ownerId = userId,
                tags = request.tags.toTypedArray()
            )
        )

        log.info("Time log entry created for user: {}, id: {}", userName, newEntry.id)

        return HttpResponse.created(newEntry.toDto())
    }

    @Put("/{id}/stop")
    open fun stopEntry(
        @PathVariable id: Long,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Stop entry failed: user not authenticated")
            return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Stop entry failed: user not found: {}", userName)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        log.debug("Stopping time log entry: {} for user: {}", id, userName)

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, userId).orElse(null)
        if (entry == null) {
            log.debug("Stop entry failed: entry not found: {}", id)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))
        }

        // Check if already stopped
        if (entry.endTime != null) {
            log.debug("Stop entry failed: entry already stopped: {}", id)
            return HttpResponse.badRequest(
                TimeLogEntryErrorResponse("Log entry is already stopped", "ENTRY_ALREADY_STOPPED")
            )
        }

        val stoppedEntry = timeLogEntryRepository.update(
            entry.copy(endTime = timeService.now())
        )

        log.info("Time log entry stopped: {} for user: {}", id, userName)

        return HttpResponse.ok(stoppedEntry.toDto())
    }

    @Put("/{id}")
    open fun updateEntry(
        @PathVariable id: Long,
        @Valid @Body request: UpdateTimeLogEntryRequest,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Update entry failed: user not authenticated")
            return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Update entry failed: user not found: {}", userName)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        log.debug("Updating time log entry: {} for user: {}", id, userName)

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, userId).orElse(null)
        if (entry == null) {
            log.debug("Update entry failed: entry not found: {}", id)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))
        }

        // Validate that start time is not in the future
        if (request.startTime.isAfter(timeService.now())) {
            log.debug("Update entry failed: start time in future for entry: {}", id)
            return HttpResponse.badRequest(
                TimeLogEntryErrorResponse("Start time cannot be in the future", "START_TIME_IN_FUTURE")
            )
        }

        // For stopped entries, validate end time if provided
        val endTime = if (entry.endTime != null) {
            // This is a stopped entry
            val newEndTime = request.endTime
            if (newEndTime == null) {
                log.debug("Update entry failed: end time required for stopped entry: {}", id)
                return HttpResponse.badRequest(
                    TimeLogEntryErrorResponse("End time is required for stopped entries", "END_TIME_REQUIRED")
                )
            }
            
            // Validate end time is not in the future
            if (newEndTime.isAfter(timeService.now())) {
                log.debug("Update entry failed: end time in future for entry: {}", id)
                return HttpResponse.badRequest(
                    TimeLogEntryErrorResponse("End time cannot be in the future", "END_TIME_IN_FUTURE")
                )
            }
            
            // Validate end time is after start time
            if (newEndTime.isBefore(request.startTime) || newEndTime == request.startTime) {
                log.debug("Update entry failed: end time before or equal to start time for entry: {}", id)
                return HttpResponse.badRequest(
                    TimeLogEntryErrorResponse("End time must be after start time", "END_TIME_BEFORE_START_TIME")
                )
            }
            
            newEndTime
        } else {
            // This is an active entry, endTime should remain null
            null
        }

        val updatedEntry = timeLogEntryRepository.update(
            entry.copy(
                title = request.title,
                startTime = request.startTime,
                endTime = endTime,
                tags = request.tags.toTypedArray()
            )
        )

        log.info("Time log entry updated: {} for user: {}", id, userName)

        return HttpResponse.ok(updatedEntry.toDto())
    }

    @Delete("/{id}")
    open fun deleteEntry(
        @PathVariable id: Long,
        principal: Principal?
    ): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Delete entry failed: user not authenticated")
            return HttpResponse.unauthorized<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Delete entry failed: user not found: {}", userName)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        log.debug("Deleting time log entry: {} for user: {}", id, userName)

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, userId).orElse(null)
        if (entry == null) {
            log.debug("Delete entry failed: entry not found: {}", id)
            return HttpResponse.notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))
        }

        timeLogEntryRepository.delete(entry)

        log.info("Time log entry deleted: {} for user: {}", id, userName)

        return HttpResponse.ok(DeleteLogEntryResponse("Time log entry deleted successfully"))
    }

    private fun TimeLogEntry.toDto() = TimeLogEntryDto(
        id = requireNotNull(this.id) { "Log entry must have an ID" },
        startTime = this.startTime,
        endTime = this.endTime,
        title = this.title,
        ownerId = this.ownerId,
        tags = this.tags.toList()
    )
}

@Serdeable
@Introspected
data class TimeLogEntryDto(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val title: String,
    val ownerId: Long,
    val tags: List<String> = emptyList()
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
    val stopActiveEntry: Boolean = false,
    val tags: List<String> = emptyList()
)

@Serdeable
@Introspected
data class UpdateTimeLogEntryRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String,
    val startTime: Instant,
    val endTime: Instant? = null,
    val tags: List<String> = emptyList()
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
