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

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

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
 * This is useful for tests where the date stays the same but time changes.
 *
 * @param localTime The local time in HH:mm format (e.g., "02:45")
 * @return A new Instant with the same local date but the specified local time
 *
 * Example:
 * ```
 * val baseTime = timeInTestTz("2024-03-16", "03:30")
 * val newTime = baseTime.withLocalTime("02:45") // Saturday, March 16, 2024 at 02:45:00 NZDT
 * ```
 */
fun Instant.withLocalTime(localTime: String): Instant {
    val zonedDateTime = this.atZone(TEST_TIMEZONE)
    val time = parseLocalTime(localTime)
    return zonedDateTime.with(time).toInstant()
}

/**
 * Creates a new Instant with a different date but the same local time in the test timezone.
 * This is useful for tests where the time stays the same but date changes.
 *
 * @param localDate The local date in yyyy-MM-dd format (e.g., "2024-03-15")
 * @return A new Instant with the specified local date but the same local time
 *
 * Example:
 * ```
 * val baseTime = timeInTestTz("2024-03-16", "03:30")
 * val yesterday = baseTime.withLocalDate("2024-03-15") // Friday, March 15, 2024 at 03:30:00 NZDT
 * ```
 */
fun Instant.withLocalDate(localDate: String): Instant {
    val zonedDateTime = this.atZone(TEST_TIMEZONE)
    val date = parseLocalDate(localDate)
    return zonedDateTime.with(date).toInstant()
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
    val date = parseLocalDate(localDate)
    val time = parseLocalTime(localTime)
    return ZonedDateTime.of(date, time, TEST_TIMEZONE).toInstant()
}

private fun parseLocalDate(localDate: String): LocalDate = LocalDate.parse(localDate, DATE_FORMATTER)

private fun parseLocalTime(localTime: String): LocalTime = LocalTime.parse(localTime, TIME_FORMATTER)
