/**
 * Shared types for time log components.
 */

export interface TimeEntry {
  id: number;
  startTime: string;
  endTime: string | null;
  title: string;
  ownerId: number;
  tags: string[];
}

export interface TimeLogEntry extends TimeEntry {}

export interface DayGroup {
  date: string;
  displayTitle: string;
  entries: TimeLogEntry[];
  totalDuration: number;
}

/**
 * Represents a group of time log entries with the same title and tags
 */
export interface GroupedTimeLogEntry {
  /** Unique identifier for this group */
  groupId: string;
  /** Common title for all entries in this group */
  title: string;
  /** Common tags for all entries in this group (sorted) */
  tags: string[];
  /** All entries in this group */
  entries: TimeLogEntry[];
  /** Earliest start time among all entries */
  startTime: string;
  /** Latest end time among all entries (null if any entry is active) */
  endTime: string | null;
  /** Sum of durations of all entries in seconds */
  totalDuration: number;
}
