package io.orangebuffalo.aionify.api

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.authentication.Authentication
import io.micronaut.serde.annotation.Serdeable
import io.orangebuffalo.aionify.domain.CurrentUserService
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryRepository
import io.orangebuffalo.aionify.domain.TimeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory

/**
 * Public API controller for time log entries.
 * All operations are scoped to the authenticated user's entries.
 */
@Controller("/api/time-log-entries")
@Tag(name = "Time Log Entries", description = "API for managing time log entries")
@Transactional
open class TimeLogEntryApiResource(
    private val timeLogEntryRepository: TimeLogEntryRepository,
    private val timeService: TimeService,
    private val currentUserService: CurrentUserService,
) {
    private val log = LoggerFactory.getLogger(TimeLogEntryApiResource::class.java)

    @Post("/start")
    @Operation(
        summary = "Start a new time log entry",
        description = """
            Starts a new time log entry with the given title.
            If there is already an active entry, it will be automatically stopped before starting the new one.
            All operations are scoped to the current authenticated user.
        """,
        security = [SecurityRequirement(name = "BearerAuth")],
    )
    @ApiResponse(
        responseCode = "200",
        description = "Time log entry started successfully",
        content = [Content(schema = Schema(implementation = StartTimeLogEntryResponse::class))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - Invalid input (e.g., blank title or title too long)",
        content = [Content(schema = Schema(implementation = TimeLogEntryApiErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized - Invalid or missing API token",
    )
    @ApiResponse(
        responseCode = "429",
        description = "Too Many Requests - IP blocked due to too many failed auth attempts",
    )
    open fun startEntry(
        @Valid @Body request: StartTimeLogEntryRequest,
        httpRequest: HttpRequest<*>,
    ): HttpResponse<*> {
        val authentication = httpRequest.getAttribute("micronaut.security.AUTHENTICATION", Authentication::class.java).orElseThrow()
        val principal = java.security.Principal { authentication.name }
        val currentUser = currentUserService.resolveUser(principal)
        log.debug("Starting time log entry for user: {}, title: {}", currentUser.user.userName, request.title)

        // Stop any active entry first
        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(currentUser.id).orElse(null)
        if (activeEntry != null) {
            log.debug("Stopping active entry before starting new one for user: {}", currentUser.user.userName)
            timeLogEntryRepository.update(
                activeEntry.copy(endTime = timeService.now()),
            )
        }

        // Create new entry
        val newEntry =
            timeLogEntryRepository.save(
                TimeLogEntry(
                    startTime = timeService.now(),
                    endTime = null,
                    title = request.title,
                    ownerId = currentUser.id,
                ),
            )

        log.info("Time log entry started for user: {}, id: {}", currentUser.user.userName, newEntry.id)

        return HttpResponse.ok(StartTimeLogEntryResponse(title = newEntry.title))
    }

    @Post("/stop")
    @Operation(
        summary = "Stop the active time log entry",
        description = """
            Stops the currently active time log entry for the authenticated user.
            If there is no active entry, the operation does nothing and returns success.
            All operations are scoped to the current authenticated user.
        """,
        security = [SecurityRequirement(name = "BearerAuth")],
    )
    @ApiResponse(
        responseCode = "200",
        description = "Time log entry stopped successfully (or no active entry to stop)",
        content = [Content(schema = Schema(implementation = StopTimeLogEntryResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized - Invalid or missing API token",
    )
    @ApiResponse(
        responseCode = "429",
        description = "Too Many Requests - IP blocked due to too many failed auth attempts",
    )
    open fun stopEntry(httpRequest: HttpRequest<*>): HttpResponse<*> {
        val authentication = httpRequest.getAttribute("micronaut.security.AUTHENTICATION", Authentication::class.java).orElseThrow()
        val principal = java.security.Principal { authentication.name }
        val currentUser = currentUserService.resolveUser(principal)
        log.debug("Stopping active time log entry for user: {}", currentUser.user.userName)

        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(currentUser.id).orElse(null)

        if (activeEntry != null) {
            timeLogEntryRepository.update(
                activeEntry.copy(endTime = timeService.now()),
            )
            log.info("Time log entry stopped for user: {}, id: {}", currentUser.user.userName, activeEntry.id)
        } else {
            log.debug("No active time log entry to stop for user: {}", currentUser.user.userName)
        }

        return HttpResponse.ok(StopTimeLogEntryResponse(message = "Success"))
    }

    @Get("/active")
    @Operation(
        summary = "Get the active time log entry",
        description = """
            Returns the currently active time log entry for the authenticated user.
            Returns 404 if there is no active entry.
            All operations are scoped to the current authenticated user.
        """,
        security = [SecurityRequirement(name = "BearerAuth")],
    )
    @ApiResponse(
        responseCode = "200",
        description = "Active time log entry found",
        content = [Content(schema = Schema(implementation = ActiveTimeLogEntryResponse::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "No active time log entry found",
        content = [Content(schema = Schema(implementation = TimeLogEntryApiErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized - Invalid or missing API token",
    )
    @ApiResponse(
        responseCode = "429",
        description = "Too Many Requests - IP blocked due to too many failed auth attempts",
    )
    open fun getActiveEntry(httpRequest: HttpRequest<*>): HttpResponse<*> {
        val authentication = httpRequest.getAttribute("micronaut.security.AUTHENTICATION", Authentication::class.java).orElseThrow()
        val principal = java.security.Principal { authentication.name }
        val currentUser = currentUserService.resolveUser(principal)
        log.debug("Getting active time log entry for user: {}", currentUser.user.userName)

        val activeEntry = timeLogEntryRepository.findByOwnerIdAndEndTimeIsNull(currentUser.id).orElse(null)

        return if (activeEntry != null) {
            log.trace("Found active time log entry for user: {}, id: {}", currentUser.user.userName, activeEntry.id)
            HttpResponse.ok(ActiveTimeLogEntryResponse(title = activeEntry.title))
        } else {
            log.trace("No active time log entry for user: {}", currentUser.user.userName)
            HttpResponse
                .notFound<TimeLogEntryApiErrorResponse>()
                .body(TimeLogEntryApiErrorResponse(error = "No active time log entry", errorCode = "NO_ACTIVE_ENTRY"))
        }
    }
}

@Serdeable
@Introspected
@Schema(description = "Request to start a new time log entry")
data class StartTimeLogEntryRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 1000, message = "Title cannot exceed 1000 characters")
    @field:Schema(
        description = "Title of the time log entry",
        example = "Working on feature X",
        maxLength = 1000,
        required = true,
    )
    val title: String,
)

@Serdeable
@Introspected
@Schema(description = "Response after starting a time log entry")
data class StartTimeLogEntryResponse(
    @field:Schema(description = "Title of the started time log entry", example = "Working on feature X")
    val title: String,
)

@Serdeable
@Introspected
@Schema(description = "Response after stopping a time log entry")
data class StopTimeLogEntryResponse(
    @field:Schema(description = "Success message", example = "Success")
    val message: String,
)

@Serdeable
@Introspected
@Schema(description = "Response containing the active time log entry")
data class ActiveTimeLogEntryResponse(
    @field:Schema(description = "Title of the active time log entry", example = "Working on feature X")
    val title: String,
)

@Serdeable
@Introspected
@Schema(description = "Error response for time log entry API")
data class TimeLogEntryApiErrorResponse(
    @field:Schema(description = "Error message", example = "No active time log entry")
    val error: String,
    @field:Schema(description = "Error code for internationalization", example = "NO_ACTIVE_ENTRY")
    val errorCode: String,
)
