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
