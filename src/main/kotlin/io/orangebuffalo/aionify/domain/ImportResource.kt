package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import jakarta.transaction.Transactional
import java.security.Principal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Controller("/api/import")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Transactional
open class ImportResource(
    private val timeLogEntryRepository: TimeLogEntryRepository,
    private val userRepository: UserRepository,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(ImportResource::class.java)

    @Post("/toggl")
    @Consumes(MediaType.TEXT_PLAIN)
    open fun importTogglCsv(
        @Body csvContent: String,
        @io.micronaut.http.annotation.Header("X-Timezone") timezone: String?,
        principal: Principal?,
    ): HttpResponse<*> {
        val userName = principal?.name
        if (userName == null) {
            log.debug("Import failed: user not authenticated")
            return HttpResponse
                .unauthorized<ImportErrorResponse>()
                .body(ImportErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))
        }

        val user = userRepository.findByUserName(userName).orElse(null)
        if (user == null) {
            log.debug("Import failed: user not found: {}", userName)
            return HttpResponse
                .notFound<ImportErrorResponse>()
                .body(ImportErrorResponse("User not found", "USER_NOT_FOUND"))
        }

        val userId = requireNotNull(user.id) { "User must have an ID" }

        log.debug("Starting Toggl import for user: {}", userName)

        try {
            // Parse CSV and validate format
            val entries = parseTogglCsv(csvContent)

            // Use browser timezone if provided, otherwise fallback to UTC
            val userZoneId =
                try {
                    ZoneId.of(timezone ?: "UTC")
                } catch (e: Exception) {
                    log.warn("Invalid timezone provided: {}, using UTC", timezone)
                    ZoneId.of("UTC")
                }

            var importedCount = 0
            var duplicateCount = 0

            // Import each entry
            entries.forEach { togglEntry ->
                // Convert to Instant using user's timezone
                val startInstant = togglEntry.startDateTime.atZone(userZoneId).toInstant()
                val endInstant = togglEntry.endDateTime.atZone(userZoneId).toInstant()

                // Check for duplicates by description and start time (epoch millis comparison)
                val existing =
                    timeLogEntryRepository.findByOwnerIdAndTitleAndStartTime(
                        userId,
                        togglEntry.description,
                        startInstant,
                    )

                if (existing.isEmpty) {
                    // Create new entry
                    timeLogEntryRepository.save(
                        TimeLogEntry(
                            startTime = startInstant,
                            endTime = endInstant,
                            title = togglEntry.description,
                            ownerId = userId,
                            tags = togglEntry.tags.toTypedArray(),
                        ),
                    )
                    importedCount++
                    log.trace("Imported entry: {}", togglEntry.description)
                } else {
                    duplicateCount++
                    log.trace("Skipped duplicate entry: {}", togglEntry.description)
                }
            }

            log.info("Toggl import completed for user: {}, imported: {}, duplicates: {}", userName, importedCount, duplicateCount)

            return HttpResponse.ok(
                ImportSuccessResponse(
                    imported = importedCount,
                    duplicates = duplicateCount,
                ),
            )
        } catch (e: InvalidCsvFormatException) {
            log.debug("Import failed: invalid CSV format for user: {}", userName, e)
            return HttpResponse.badRequest(
                ImportErrorResponse("Invalid CSV format", "INVALID_CSV_FORMAT"),
            )
        } catch (e: Exception) {
            log.error("Import failed unexpectedly for user: {}", userName, e)
            return HttpResponse.serverError(
                ImportErrorResponse("Import failed", "IMPORT_FAILED"),
            )
        }
    }

    private fun parseTogglCsv(csvContent: String): List<TogglEntry> {
        val lines = csvContent.trim().lines()

        if (lines.isEmpty()) {
            throw InvalidCsvFormatException("CSV is empty")
        }

        // Parse header
        val header = parseCsvLine(lines[0])
        if (header.size != 6 ||
            header[0] != "Description" ||
            header[1] != "Tags" ||
            header[2] != "Start date" ||
            header[3] != "Start time" ||
            header[4] != "Stop date" ||
            header[5] != "Stop time"
        ) {
            throw InvalidCsvFormatException("Invalid CSV header. Expected: Description,Tags,Start date,Start time,Stop date,Stop time")
        }

        val entries = mutableListOf<TogglEntry>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        // Parse data rows
        for (i in 1 until lines.size) {
            if (lines[i].trim().isEmpty()) {
                continue // Skip empty lines
            }

            try {
                val columns = parseCsvLine(lines[i])
                if (columns.size != 6) {
                    throw InvalidCsvFormatException("Row ${i + 1} has ${columns.size} columns, expected 6")
                }

                val description = columns[0]
                val tags =
                    if (columns[1].isBlank()) {
                        emptyList()
                    } else {
                        columns[1].split(",").map { it.trim() }
                    }
                val startDate = LocalDate.parse(columns[2], dateFormatter)
                val startTime = LocalTime.parse(columns[3], timeFormatter)
                val endDate = LocalDate.parse(columns[4], dateFormatter)
                val endTime = LocalTime.parse(columns[5], timeFormatter)

                entries.add(
                    TogglEntry(
                        description = description,
                        tags = tags,
                        startDateTime = startDate.atTime(startTime),
                        endDateTime = endDate.atTime(endTime),
                    ),
                )
            } catch (e: Exception) {
                throw InvalidCsvFormatException("Failed to parse row ${i + 1}: ${e.message}", e)
            }
        }

        return entries
    }

    /**
     * Parse a CSV line handling quoted values.
     * Simple CSV parser that handles quotes and commas within quotes.
     */
    private fun parseCsvLine(line: String): List<String> {
        val columns = mutableListOf<String>()
        var currentColumn = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val char = line[i]

            when {
                char == '"' -> {
                    // Toggle quote state
                    inQuotes = !inQuotes
                }

                char == ',' && !inQuotes -> {
                    // End of column
                    columns.add(currentColumn.toString())
                    currentColumn = StringBuilder()
                }

                else -> {
                    currentColumn.append(char)
                }
            }
        }

        // Add last column
        columns.add(currentColumn.toString())

        return columns
    }
}

data class TogglEntry(
    val description: String,
    val tags: List<String>,
    val startDateTime: java.time.LocalDateTime,
    val endDateTime: java.time.LocalDateTime,
)

class InvalidCsvFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serdeable
@Introspected
data class ImportSuccessResponse(
    val imported: Int,
    val duplicates: Int,
)

@Serdeable
@Introspected
data class ImportErrorResponse(
    val error: String,
    val errorCode: String,
)
