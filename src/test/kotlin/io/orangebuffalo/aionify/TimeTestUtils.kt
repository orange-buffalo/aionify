package io.orangebuffalo.aionify

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Test timezone used for all time log entry tests.
 * This is Pacific/Auckland (NZDT, UTC+13 during daylight saving time).
 */
val TEST_TIMEZONE: ZoneId = ZoneId.of("Pacific/Auckland")

/**
 * Extension functions for time shifts in tests.
 * These make test code more readable than raw second calculations.
 */

fun Instant.minusHours(hours: Long): Instant = this.minus(hours, ChronoUnit.HOURS)

fun Instant.plusHours(hours: Long): Instant = this.plus(hours, ChronoUnit.HOURS)

fun Instant.minusMinutes(minutes: Long): Instant = this.minus(minutes, ChronoUnit.MINUTES)

fun Instant.plusMinutes(minutes: Long): Instant = this.plus(minutes, ChronoUnit.MINUTES)

fun Instant.minusDays(days: Long): Instant = this.minus(days, ChronoUnit.DAYS)

fun Instant.plusDays(days: Long): Instant = this.plus(days, ChronoUnit.DAYS)

/**
 * Creates a new Instant with the same date but different local time in the test timezone.
 * This is useful for inline time editing tests where the date stays the same but time changes.
 *
 * @param localTime The local time in HH:mm format (e.g., "02:45")
 * @return A new Instant with the same local date but the specified local time
 *
 * Example:
 * ```
 * val baseTime = FIXED_TEST_TIME // Saturday, March 16, 2024 at 03:30:00 NZDT
 * val newTime = baseTime.withLocalTime("02:45") // Saturday, March 16, 2024 at 02:45:00 NZDT
 * ```
 */
fun Instant.withLocalTime(localTime: String): Instant {
    val zonedDateTime = this.atZone(TEST_TIMEZONE)
    val time = LocalTime.parse(localTime, DateTimeFormatter.ofPattern("HH:mm"))
    return zonedDateTime.with(time).toInstant()
}

/**
 * Creates an Instant from a local date and local time in the test timezone.
 * This replaces complex UTC calculations in tests with readable local values.
 *
 * @param localDate The local date in yyyy-MM-dd format (e.g., "2024-03-16")
 * @param localTime The local time in HH:mm format (e.g., "02:45")
 * @return An Instant representing the given local date/time in the test timezone
 *
 * Example:
 * ```
 * // Instead of: Instant.parse("2024-03-15T13:45:00Z") with complex UTC calculations
 * // Use: timeInTestTz("2024-03-16", "02:45")
 * val expectedStartTime = timeInTestTz("2024-03-16", "02:45")
 * ```
 */
fun timeInTestTz(
    localDate: String,
    localTime: String,
): Instant {
    val date = LocalDate.parse(localDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val time = LocalTime.parse(localTime, DateTimeFormatter.ofPattern("HH:mm"))
    return ZonedDateTime.of(date, time, TEST_TIMEZONE).toInstant()
}
