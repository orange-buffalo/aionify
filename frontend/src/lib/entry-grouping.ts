import type { TimeLogEntry } from "@/components/time-logs/types";

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
  /** Latest start time among all entries (used for group positioning) */
  startTime: string;
  /** Earliest start time among all entries (used for display) */
  earliestStartTime: string;
  /** Latest end time among all entries (null if any entry is active) */
  endTime: string | null;
  /** Sum of durations of all entries in milliseconds */
  totalDuration: number;
}

/**
 * Creates a grouping key from title and tags
 * Tags are sorted to ensure order-independent matching
 */
function createGroupKey(title: string, tags: string[]): string {
  const sortedTags = [...tags].sort().join(",");
  return `${title}|||${sortedTags}`;
}

/**
 * Groups time log entries by title and tags within a day
 * Entries with the same title and exact tag list (order-independent) are grouped together
 *
 * @param entries Array of time log entries to group
 * @returns Array of grouped entries or single entries
 */
export function groupEntriesByTitleAndTags(entries: TimeLogEntry[]): (TimeLogEntry | GroupedTimeLogEntry)[] {
  // Group entries by title and tags
  const groups = new Map<string, TimeLogEntry[]>();

  for (const entry of entries) {
    const key = createGroupKey(entry.title, entry.tags || []);
    const existing = groups.get(key);
    if (existing) {
      existing.push(entry);
    } else {
      groups.set(key, [entry]);
    }
  }

  // Convert groups to output format
  const result: (TimeLogEntry | GroupedTimeLogEntry)[] = [];

  for (const [key, groupEntries] of groups.entries()) {
    if (groupEntries.length === 1) {
      // Single entry - return as is
      result.push(groupEntries[0]);
    } else {
      // Multiple entries - create grouped entry
      const sortedEntries = [...groupEntries].sort(
        (a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime()
      );

      // Find latest start time for group positioning
      const startTime = sortedEntries.reduce((latest, entry) => {
        return new Date(entry.startTime) > new Date(latest) ? entry.startTime : latest;
      }, sortedEntries[0].startTime);

      // Find earliest start time for display
      const earliestStartTime = sortedEntries.reduce((earliest, entry) => {
        return new Date(entry.startTime) < new Date(earliest) ? entry.startTime : earliest;
      }, sortedEntries[0].startTime);

      // Find latest end time (null if any entry is active)
      const hasActiveEntry = sortedEntries.some((e) => e.endTime == null); // Using == to match both null and undefined
      const endTime = hasActiveEntry
        ? null
        : sortedEntries.reduce(
            (latest, entry) => {
              if (entry.endTime == null) return latest; // Using == to match both null and undefined
              if (!latest) return entry.endTime;
              return new Date(entry.endTime) > new Date(latest) ? entry.endTime : latest;
            },
            null as string | null
          );

      // Calculate total duration in milliseconds - ONLY for completed entries
      // Active entries are excluded from the total as their duration is still growing
      const totalDuration = sortedEntries.reduce((sum, entry) => {
        if (entry.endTime == null) {
          // Skip active entries
          return sum;
        }
        const duration = new Date(entry.endTime).getTime() - new Date(entry.startTime).getTime();
        return sum + duration;
      }, 0);

      result.push({
        groupId: key,
        title: sortedEntries[0].title,
        tags: [...(sortedEntries[0].tags || [])].sort(),
        entries: sortedEntries,
        startTime,
        earliestStartTime,
        endTime,
        totalDuration,
      });
    }
  }

  // Sort result by start time (most recent first)
  return result.sort((a, b) => {
    const timeA = "startTime" in a && typeof a.startTime === "string" ? a.startTime : a.startTime;
    const timeB = "startTime" in b && typeof b.startTime === "string" ? b.startTime : b.startTime;
    return new Date(timeB).getTime() - new Date(timeA).getTime();
  });
}

/**
 * Type guard to check if an entry is a grouped entry
 */
export function isGroupedEntry(entry: TimeLogEntry | GroupedTimeLogEntry): entry is GroupedTimeLogEntry {
  return "groupId" in entry && "entries" in entry;
}
