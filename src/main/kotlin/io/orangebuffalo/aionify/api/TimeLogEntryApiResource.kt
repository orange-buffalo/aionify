package io.orangebuffalo.aionify.api

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.serde.annotation.Serdeable
import io.orangebuffalo.aionify.domain.TimeLogEntry
import io.orangebuffalo.aionify.domain.TimeLogEntryService
import io.orangebuffalo.aionify.domain.UserWithId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Public API controller for time log entries.
 * All operations are scoped to the authenticated user's entries.
 */
@Controller("/api/time-log-entries")
@Tag(name = "Public API", description = "Public API endpoints")
@Transactional
open class TimeLogEntryApiResource(
    private val timeLogEntryService: TimeLogEntryService,
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
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug("Starting time log entry for user: {}, title: {}", currentUser.user.userName, request.title)

        val metadata = request.metadata?.toTypedArray() ?: emptyArray()
        val newEntry =
            timeLogEntryService.startEntry(
                userId = currentUser.id,
                title = request.title,
                metadata = metadata,
            )

        return HttpResponse.ok(
            StartTimeLogEntryResponse(
                title = newEntry.title,
                metadata = newEntry.metadata.toList(),
            ),
        )
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
    open fun stopEntry(currentUser: UserWithId): HttpResponse<*> {
        log.debug("Stopping active time log entry for user: {}", currentUser.user.userName)

        val stoppedEntry = timeLogEntryService.stopActiveEntry(currentUser.id)

        return if (stoppedEntry != null) {
            HttpResponse.ok(StopTimeLogEntryResponse(message = "Entry stopped", stopped = true))
        } else {
            HttpResponse.ok(StopTimeLogEntryResponse(message = "No active entry", stopped = false))
        }
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
    open fun getActiveEntry(currentUser: UserWithId): HttpResponse<*> {
        log.debug("Getting active time log entry for user: {}", currentUser.user.userName)

        val activeEntry = timeLogEntryService.getActiveEntry(currentUser.id)

        return if (activeEntry != null) {
            HttpResponse.ok(
                ActiveTimeLogEntryResponse(
                    entry = activeEntry.toApiDto(),
                ),
            )
        } else {
            HttpResponse
                .notFound<TimeLogEntryApiErrorResponse>()
                .body(TimeLogEntryApiErrorResponse(error = "No active time log entry", errorCode = "NO_ACTIVE_ENTRY"))
        }
    }

    @Get
    @Operation(
        summary = "List time log entries",
        description = """
            Returns a paginated list of time log entries within the specified time range.
            All operations are scoped to the current authenticated user.
            Results are ordered by start time descending (newest first).
        """,
        security = [SecurityRequirement(name = "BearerAuth")],
    )
    @ApiResponse(
        responseCode = "200",
        description = "Time log entries retrieved successfully",
        content = [Content(schema = Schema(implementation = ListTimeLogEntriesResponse::class))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - Invalid query parameters (e.g., invalid date range, invalid page size)",
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
    open fun listEntries(
        @QueryValue
        @Schema(
            description = "Start of time range (inclusive) in ISO 8601 format",
            example = "2024-01-01T00:00:00Z",
            required = true,
        )
        startTimeFrom: Instant,
        @QueryValue
        @Schema(
            description = "End of time range (exclusive) in ISO 8601 format",
            example = "2024-01-31T23:59:59Z",
            required = true,
        )
        startTimeTo: Instant,
        @QueryValue(defaultValue = "0")
        @Min(0)
        @Schema(
            description = "Page number (zero-based)",
            example = "0",
            defaultValue = "0",
            minimum = "0",
        )
        page: Int,
        @QueryValue(defaultValue = "100")
        @Min(1)
        @Max(500)
        @Schema(
            description = "Number of entries per page",
            example = "100",
            defaultValue = "100",
            minimum = "1",
            maximum = "500",
        )
        size: Int,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        log.debug(
            "Listing time log entries for user: {}, startTimeFrom: {}, startTimeTo: {}, page: {}, size: {}",
            currentUser.user.userName,
            startTimeFrom,
            startTimeTo,
            page,
            size,
        )

        // Validate time range
        // Note: We reject equal start and end times as this would result in an empty range
        // (startTimeFrom is inclusive, startTimeTo is exclusive)
        if (startTimeFrom.isAfter(startTimeTo) || startTimeFrom == startTimeTo) {
            return HttpResponse.badRequest(
                TimeLogEntryApiErrorResponse(
                    error = "Start time must be before end time",
                    errorCode = "INVALID_TIME_RANGE",
                ),
            )
        }

        val (entries, totalCount) =
            timeLogEntryService.getEntriesInRangePaginated(
                userId = currentUser.id,
                startTimeFrom = startTimeFrom,
                startTimeTo = startTimeTo,
                page = page,
                size = size,
            )

        // Calculate pagination info
        // Size is always >= 1 due to validation, so we can safely calculate total pages
        val totalPages = ((totalCount + size - 1) / size).toInt()

        return HttpResponse.ok(
            ListTimeLogEntriesResponse(
                entries = entries.map { it.toApiDto() },
                page = page,
                size = size,
                totalElements = totalCount.toInt(),
                totalPages = totalPages,
            ),
        )
    }

    private fun TimeLogEntry.toApiDto() =
        TimeLogEntryApiDto(
            startTime = this.startTime,
            endTime = this.endTime,
            title = this.title,
            tags = this.tags.toList(),
            metadata = this.metadata.toList(),
        )
}

@Serdeable
@Introspected
@Schema(description = "Time log entry for public API")
data class TimeLogEntryApiDto(
    @field:Schema(
        description = "Start time of the entry in ISO 8601 format",
        example = "2024-01-15T10:30:00Z",
        required = true,
    )
    val startTime: Instant,
    @field:Schema(
        description = "End time of the entry in ISO 8601 format (null if entry is still active)",
        example = "2024-01-15T12:30:00Z",
        required = false,
        nullable = true,
    )
    val endTime: Instant?,
    @field:Schema(description = "Title of the time log entry", example = "Working on feature X")
    val title: String,
    @field:Schema(description = "Tags associated with the entry", example = "[\"frontend\", \"bug-fix\"]")
    val tags: List<String>,
    @field:Schema(description = "Metadata of the time log entry", example = "[\"project:aionify\", \"task:API-123\"]")
    val metadata: List<String>,
)

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
    @field:Schema(
        description = "Optional metadata for the time log entry",
        example = "[\"project:aionify\", \"task:API-123\"]",
        required = false,
    )
    val metadata: List<String>? = null,
)

@Serdeable
@Introspected
@Schema(description = "Response after starting a time log entry")
data class StartTimeLogEntryResponse(
    @field:Schema(description = "Title of the started time log entry", example = "Working on feature X")
    val title: String,
    @field:Schema(description = "Metadata of the started time log entry", example = "[\"project:aionify\", \"task:API-123\"]")
    val metadata: List<String>,
)

@Serdeable
@Introspected
@Schema(description = "Response after stopping a time log entry")
data class StopTimeLogEntryResponse(
    @field:Schema(description = "Message describing the result", example = "Entry stopped")
    val message: String,
    @field:Schema(description = "Whether an entry was stopped", example = "true")
    val stopped: Boolean,
)

@Serdeable
@Introspected
@Schema(description = "Response containing the active time log entry")
data class ActiveTimeLogEntryResponse(
    @field:Schema(
        description = "Active time log entry. This field is always present in successful (200) responses. " +
            "A 404 response is returned when there is no active entry.",
        required = true,
    )
    val entry: TimeLogEntryApiDto?,
)

@Serdeable
@Introspected
@Schema(description = "Paginated response containing time log entries")
data class ListTimeLogEntriesResponse(
    @field:Schema(description = "List of time log entries for the current page")
    val entries: List<TimeLogEntryApiDto>,
    @field:Schema(description = "Current page number (zero-based)", example = "0")
    val page: Int,
    @field:Schema(description = "Page size", example = "100")
    val size: Int,
    @field:Schema(description = "Total number of entries matching the query", example = "250")
    val totalElements: Int,
    @field:Schema(description = "Total number of pages", example = "3")
    val totalPages: Int,
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
