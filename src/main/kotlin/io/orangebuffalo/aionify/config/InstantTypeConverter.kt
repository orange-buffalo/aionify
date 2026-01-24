package io.orangebuffalo.aionify.config

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import jakarta.inject.Singleton
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

/**
 * Type converter for converting String to Instant.
 * Handles both standard ISO-8601 formats (e.g., "2024-01-01T00:00:00Z")
 * and formats with timezone offsets (e.g., "2024-01-01T00:00:00+11:00").
 */
@Singleton
class InstantTypeConverter : TypeConverter<String, Instant> {
    override fun convert(
        value: String,
        targetType: Class<Instant>,
        context: ConversionContext,
    ): Optional<Instant> =
        try {
            // Try parsing as standard ISO-8601 instant (Z format) for backward compatibility
            // If that fails, try parsing as ISO-8601 with timezone offset (e.g., +11:00, -05:00)
            try {
                Optional.of(Instant.parse(value))
            } catch (e: DateTimeParseException) {
                // Parse as ZonedDateTime/OffsetDateTime and convert to Instant
                val instant = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value, Instant::from)
                Optional.of(instant)
            }
        } catch (e: Exception) {
            Optional.empty()
        }
}
