package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Hidden
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal
import java.time.Instant

@Controller("/api-ui/time-log-entries")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Transactional
@Hidden
open class TimeLogEntryResource(
    private val timeLogEntryRepository: TimeLogEntryRepository,
    private val userRepository: UserRepository,
    private val timeLogEntryService: TimeLogEntryService,
    private val timeService: TimeService,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(TimeLogEntryResource::class.java)

    @Get
    open fun listEntries(
        @QueryValue startTime: Instant,
        @QueryValue endTime: Instant,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Listing time log entries for user: {}, startTime: {}, endTime: {}", currentUser.user.userName, startTime, endTime)

        val entries =
            timeLogEntryRepository.findByOwnerIdAndStartTimeGreaterThanEqualsAndStartTimeLessThanOrderByStartTimeDesc(
                currentUser.id,
                startTime,
                endTime,
            )

        log.trace("Found {} time log entries for user: {}", entries.size, currentUser.user.userName)

        return HttpResponse.ok(
            TimeLogEntriesResponse(
                entries = entries.map { it.toDto() },
            ),
        )
    }

    @Get("/active")
    open fun getActiveEntry(currentUser: UserWithId): HttpResponse<*> {
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(currentUser.id).orElse(null)

        if (activeEntry != null) {
            log.trace("Found active time log entry for user: {}, id: {}", currentUser.user.userName, activeEntry.id)
        } else {
            log.trace("No active time log entry for user: {}", currentUser.user.userName)
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
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Creating time log entry for user: {}, title: {}", currentUser.user.userName, request.title)

        // Check if there's already an active log entry
        if (!request.stopActiveEntry) {
            val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(currentUser.id).orElse(null)
            if (activeEntry != null) {
                log.debug("Create entry failed: active entry exists for user: {}", currentUser.user.userName)
                return HttpResponse.badRequest(
                    TimeLogEntryErrorResponse("Cannot start a new log entry while another is active", "ACTIVE_ENTRY_EXISTS"),
                )
            }
        }

        val newEntry = timeLogEntryService.startEntry(currentUser.id, request.title, request.tags.toTypedArray())

        return HttpResponse.created(newEntry.toDto())
    }

    @Put("/{id}/stop")
    open fun stopEntry(
        @PathVariable id: Long,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Stopping time log entry: {} for user: {}", id, currentUser.user.userName)

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, currentUser.id).orElse(null)
        if (entry == null) {
            log.debug("Stop entry failed: entry not found: {}", id)
            return HttpResponse
                .notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))
        }

        // Check if already stopped
        if (entry.endTime != null) {
            log.debug("Stop entry failed: entry already stopped: {}", id)
            return HttpResponse.badRequest(
                TimeLogEntryErrorResponse("Log entry is already stopped", "ENTRY_ALREADY_STOPPED"),
            )
        }

        val stoppedEntry =
            timeLogEntryRepository.update(
                entry.copy(endTime = timeService.now()),
            )

        log.info("Time log entry stopped: {} for user: {}", id, currentUser.user.userName)

        return HttpResponse.ok(stoppedEntry.toDto())
    }

    @Put("/{id}")
    open fun updateEntry(
        @PathVariable id: Long,
        @Valid @Body request: UpdateTimeLogEntryRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Updating time log entry: {} for user: {}", id, currentUser.user.userName)

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, currentUser.id).orElse(null)
        if (entry == null) {
            log.debug("Update entry failed: entry not found: {}", id)
            return HttpResponse
                .notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))
        }

        // For stopped entries, validate end time if provided
        val endTime =
            if (entry.endTime != null) {
                // This is a stopped entry
                val newEndTime = request.endTime
                if (newEndTime == null) {
                    log.debug("Update entry failed: end time required for stopped entry: {}", id)
                    return HttpResponse.badRequest(
                        TimeLogEntryErrorResponse("End time is required for stopped entries", "END_TIME_REQUIRED"),
                    )
                }

                // Validate end time is after start time
                if (newEndTime.isBefore(request.startTime) || newEndTime == request.startTime) {
                    log.debug("Update entry failed: end time before or equal to start time for entry: {}", id)
                    return HttpResponse.badRequest(
                        TimeLogEntryErrorResponse("End time must be after start time", "END_TIME_BEFORE_START_TIME"),
                    )
                }

                newEndTime
            } else {
                // This is an active entry, endTime should remain null
                null
            }

        val updatedEntry =
            timeLogEntryRepository.update(
                entry.copy(
                    title = request.title,
                    startTime = request.startTime,
                    endTime = endTime,
                    tags = request.tags.toTypedArray(),
                ),
            )

        log.info("Time log entry updated: {} for user: {}", id, currentUser.user.userName)

        return HttpResponse.ok(updatedEntry.toDto())
    }

    @Put("/bulk-update")
    open fun bulkUpdateEntries(
        @Valid @Body request: BulkUpdateTimeLogEntriesRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Bulk updating {} time log entries for user: {}", request.entryIds.size, currentUser.user.userName)

        if (request.entryIds.isEmpty()) {
            log.debug("Bulk update failed: no entry IDs provided")
            return HttpResponse.badRequest(
                TimeLogEntryErrorResponse("No entry IDs provided", "NO_ENTRY_IDS"),
            )
        }

        // Fetch all entries and verify ownership
        val entries =
            request.entryIds.mapNotNull { id ->
                timeLogEntryRepository.findByIdAndOwnerId(id, currentUser.id).orElse(null)
            }

        // Verify all entries were found
        if (entries.size != request.entryIds.size) {
            log.debug("Bulk update failed: some entries not found or not owned by user")
            return HttpResponse
                .notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("One or more time log entries not found", "ENTRIES_NOT_FOUND"))
        }

        // Update each entry - only title and tags, preserve start and end times
        val updatedEntries =
            entries.map { entry ->
                timeLogEntryRepository.update(
                    entry.copy(
                        title = request.title,
                        tags = request.tags.toTypedArray(),
                    ),
                )
            }

        log.info("Bulk updated {} time log entries for user: {}", updatedEntries.size, currentUser.user.userName)

        return HttpResponse.ok(
            BulkUpdateTimeLogEntriesResponse(
                updatedCount = updatedEntries.size,
                entries = updatedEntries.map { it.toDto() },
            ),
        )
    }

    @Patch("/{id}/title")
    open fun updateEntryTitle(
        @PathVariable id: Long,
        @Valid @Body request: UpdateTimeLogEntryTitleRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Updating time log entry title: {} for user: {}", id, currentUser.user.userName)

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, currentUser.id).orElse(null)
        if (entry == null) {
            log.debug("Update title failed: entry not found: {}", id)
            return HttpResponse
                .notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))
        }

        val updatedEntry =
            timeLogEntryRepository.update(
                entry.copy(title = request.title),
            )

        log.info("Time log entry title updated: {} for user: {}", id, currentUser.user.userName)

        return HttpResponse.ok(updatedEntry.toDto())
    }

    @Delete("/{id}")
    open fun deleteEntry(
        @PathVariable id: Long,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Deleting time log entry: {} for user: {}", id, currentUser.user.userName)

        // Find the log entry and verify ownership
        val entry = timeLogEntryRepository.findByIdAndOwnerId(id, currentUser.id).orElse(null)
        if (entry == null) {
            log.debug("Delete entry failed: entry not found: {}", id)
            return HttpResponse
                .notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("Time log entry not found", "ENTRY_NOT_FOUND"))
        }

        timeLogEntryRepository.delete(entry)

        log.info("Time log entry deleted: {} for user: {}", id, currentUser.user.userName)

        return HttpResponse.ok(DeleteLogEntryResponse("Time log entry deleted successfully"))
    }

    @Get("/autocomplete")
    open fun autocomplete(
        @QueryValue query: String,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Autocomplete search for user: {}, query: {}", currentUser.user.userName, query)

        // Split query into tokens and filter empty ones
        val searchTokens = query.trim()

        if (searchTokens.isEmpty()) {
            return HttpResponse.ok(AutocompleteResponse(entries = emptyList()))
        }

        val results = timeLogEntryRepository.searchByTitleTokens(currentUser.id, searchTokens)

        log.trace("Found {} autocomplete results for user: {}", results.size, currentUser.user.userName)

        return HttpResponse.ok(
            AutocompleteResponse(
                entries = results.map { it.toAutocompleteDto() },
            ),
        )
    }

    @Patch("/bulk-update-title")
    open fun bulkUpdateEntriesTitle(
        @Valid @Body request: BulkUpdateTimeLogEntriesTitleRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Bulk updating title for {} time log entries for user: {}", request.entryIds.size, currentUser.user.userName)

        if (request.entryIds.isEmpty()) {
            log.debug("Bulk update title failed: no entry IDs provided")
            return HttpResponse.badRequest(
                TimeLogEntryErrorResponse("No entry IDs provided", "NO_ENTRY_IDS"),
            )
        }

        // Fetch all entries and verify ownership
        val entries =
            request.entryIds.mapNotNull { id ->
                timeLogEntryRepository.findByIdAndOwnerId(id, currentUser.id).orElse(null)
            }

        // Verify all entries were found
        if (entries.size != request.entryIds.size) {
            log.debug("Bulk update title failed: some entries not found or not owned by user")
            return HttpResponse
                .notFound<TimeLogEntryErrorResponse>()
                .body(TimeLogEntryErrorResponse("One or more time log entries not found", "ENTRIES_NOT_FOUND"))
        }

        // Update each entry - only title, preserve everything else
        val updatedEntries =
            entries.map { entry ->
                timeLogEntryRepository.update(
                    entry.copy(title = request.title),
                )
            }

        log.info("Bulk updated title for {} time log entries for user: {}", updatedEntries.size, currentUser.user.userName)

        return HttpResponse.ok(
            BulkUpdateTimeLogEntriesResponse(
                updatedCount = updatedEntries.size,
                entries = updatedEntries.map { it.toDto() },
            ),
        )
    }

    private fun TimeLogEntry.toDto() =
        TimeLogEntryDto(
            id = requireNotNull(this.id) { "Log entry must have an ID" },
            startTime = this.startTime,
            endTime = this.endTime,
            title = this.title,
            ownerId = this.ownerId,
            tags = this.tags.toList(),
            metadata = this.metadata.toList(),
        )

    private fun TimeLogEntry.toAutocompleteDto() =
        AutocompleteEntryDto(
            title = this.title,
            tags = this.tags.toList(),
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
    val tags: List<String> = emptyList(),
    val metadata: List<String> = emptyList(),
)

@Serdeable
@Introspected
data class TimeLogEntriesResponse(
    val entries: List<TimeLogEntryDto>,
)

@Serdeable
@Introspected
data class ActiveLogEntryResponse(
    val entry: TimeLogEntryDto?,
)

@Serdeable
@Introspected
data class CreateTimeLogEntryRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String,
    val stopActiveEntry: Boolean = false,
    val tags: List<String> = emptyList(),
)

@Serdeable
@Introspected
data class UpdateTimeLogEntryRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String,
    val startTime: Instant,
    val endTime: Instant? = null,
    val tags: List<String> = emptyList(),
)

@Serdeable
@Introspected
data class DeleteLogEntryResponse(
    val message: String,
)

@Serdeable
@Introspected
data class TimeLogEntryErrorResponse(
    val error: String,
    val errorCode: String,
)

@Serdeable
@Introspected
data class AutocompleteResponse(
    val entries: List<AutocompleteEntryDto>,
)

@Serdeable
@Introspected
data class AutocompleteEntryDto(
    val title: String,
    val tags: List<String> = emptyList(),
)

@Serdeable
@Introspected
data class BulkUpdateTimeLogEntriesRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String,
    val tags: List<String> = emptyList(),
    val entryIds: List<Long>,
)

@Serdeable
@Introspected
data class BulkUpdateTimeLogEntriesResponse(
    val updatedCount: Int,
    val entries: List<TimeLogEntryDto>,
)

@Serdeable
@Introspected
data class UpdateTimeLogEntryTitleRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String,
)

@Serdeable
@Introspected
data class BulkUpdateTimeLogEntriesTitleRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    val title: String,
    val entryIds: List<Long>,
)
