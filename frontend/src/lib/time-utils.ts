/**
 * Utility functions for time-related calculations and formatting.
 */

/**
 * Get the start of the week for a given date.
 * @param date The date to get the week start for
 * @param startOfWeek The day of the week that should be considered the first day (0 = Sunday, 1 = Monday, etc.)
 */
export function getWeekStart(date: Date, startOfWeek: number = 1): Date {
  const d = new Date(date);
  const day = d.getDay();
  const diff = (day - startOfWeek + 7) % 7;
  d.setDate(d.getDate() - diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

/**
 * Convert WeekDay string from API to JavaScript day number (0 = Sunday, 1 = Monday, etc.)
 */
export function weekDayToNumber(weekDay: string): number {
  const mapping: { [key: string]: number } = {
    SUNDAY: 0,
    MONDAY: 1,
    TUESDAY: 2,
    WEDNESDAY: 3,
    THURSDAY: 4,
    FRIDAY: 5,
    SATURDAY: 6,
  };
  return mapping[weekDay] ?? 1; // Default to Monday if invalid
}

/**
 * Format date as ISO date string (YYYY-MM-DD) in local timezone.
 */
export function formatISODate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Check if two ISO date strings represent different calendar days.
 */
export function isDifferentDay(startTime: string, endTime: string): boolean {
  const startDay = formatISODate(new Date(startTime));
  const endDay = formatISODate(new Date(endTime));
  return startDay !== endDay;
}

/**
 * Calculate duration in milliseconds between start and end times.
 */
export function calculateDuration(startTime: string, endTime: string | null): number {
  const start = new Date(startTime).getTime();
  const end = endTime ? new Date(endTime).getTime() : Date.now();
  return end - start;
}

/**
 * Format duration in HH:MM:SS.
 */
export function formatDuration(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  return `${hours.toString().padStart(2, "0")}:${minutes.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
}
