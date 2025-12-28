/**
 * Centralized date and time formatting utilities.
 *
 * This module provides consistent date/time formatting across the application,
 * respecting user locale preferences for 12/24-hour time format.
 */

/**
 * Determines if the given locale uses 12-hour time format.
 * This is determined by formatting a test time and checking for AM/PM indicators.
 */
export function uses12HourFormat(locale: string): boolean {
  const testDate = new Date(2000, 0, 1, 13, 0);
  const formatted = new Intl.DateTimeFormat(locale, {
    hour: "numeric",
    hour12: undefined, // Let locale decide
  }).format(testDate);
  return formatted.includes("PM") || formatted.includes("AM") || formatted.includes("pm") || formatted.includes("am");
}

/**
 * Formats time according to user's locale, respecting 12/24-hour preference.
 *
 * @param isoString - ISO 8601 timestamp string
 * @param locale - User's locale (e.g., 'en-US', 'uk', 'en-GB')
 * @returns Formatted time string (e.g., "2:30 PM" or "14:30")
 */
export function formatTime(isoString: string, locale: string): string {
  const date = new Date(isoString);
  const hour12 = uses12HourFormat(locale);

  return date.toLocaleTimeString(locale, {
    hour: "2-digit",
    minute: "2-digit",
    hour12,
  });
}

/**
 * Formats time with weekday prefix according to user's locale.
 *
 * @param isoString - ISO 8601 timestamp string
 * @param locale - User's locale
 * @returns Formatted time string with weekday (e.g., "Mon, 2:30 PM" or "Mon, 14:30")
 */
export function formatTimeWithWeekday(isoString: string, locale: string): string {
  const date = new Date(isoString);
  const hour12 = uses12HourFormat(locale);

  const weekday = date.toLocaleDateString(locale, { weekday: "short" });
  const time = date.toLocaleTimeString(locale, {
    hour: "2-digit",
    minute: "2-digit",
    hour12,
  });

  return `${weekday}, ${time}`;
}

/**
 * Formats date according to user's locale.
 *
 * @param isoString - ISO 8601 timestamp string
 * @param locale - User's locale
 * @returns Formatted date string (e.g., "Mar 16")
 */
export function formatDate(isoString: string, locale: string): string {
  const date = new Date(isoString);
  return date.toLocaleDateString(locale, { month: "short", day: "numeric" });
}

/**
 * Formats date and time according to user's locale, respecting 12/24-hour preference.
 *
 * @param isoString - ISO 8601 timestamp string
 * @param locale - User's locale
 * @returns Formatted date and time string (e.g., "Mar 16, 2:30 PM" or "Mar 16, 14:30")
 */
export function formatDateTime(isoString: string, locale: string): string {
  const date = new Date(isoString);
  const hour12 = uses12HourFormat(locale);

  return date.toLocaleString(locale, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12,
  });
}
