/**
 * Utility functions for detecting overlapping time log entries.
 */

import type { TimeLogEntry } from "@/components/time-logs/types";

/**
 * Represents an overlap between two time log entries.
 */
export interface EntryOverlap {
  /** The title of the entry that this entry overlaps with */
  overlappingEntryTitle: string;
}

/**
 * Checks if two time ranges overlap by more than the allowed threshold (1 second).
 * Boundary overlaps (0-1 second) are considered acceptable and return false.
 *
 * @param start1 Start time of first entry (ISO string)
 * @param end1 End time of first entry (ISO string or null for active entries)
 * @param start2 Start time of second entry (ISO string)
 * @param end2 End time of second entry (ISO string or null for active entries)
 * @returns true if the entries overlap by more than 1 second
 */
function hasSignificantOverlap(start1: string, end1: string | null, start2: string, end2: string | null): boolean {
  // Can't overlap with active entries in a meaningful way for this check
  if (!end1 || !end2) {
    return false;
  }

  const s1 = new Date(start1).getTime();
  const e1 = new Date(end1).getTime();
  const s2 = new Date(start2).getTime();
  const e2 = new Date(end2).getTime();

  // Calculate overlap in milliseconds
  const overlapStart = Math.max(s1, s2);
  const overlapEnd = Math.min(e1, e2);
  const overlapMs = overlapEnd - overlapStart;

  // Overlap is significant if it's more than 1 second (1000ms)
  return overlapMs > 1000;
}

/**
 * Detects overlaps for entries within a day group.
 * Returns a map of entry IDs to their overlaps.
 *
 * @param entries All entries in the day group
 * @returns Map from entry ID to overlap information
 */
export function detectOverlaps(entries: TimeLogEntry[]): Map<number, EntryOverlap> {
  const overlaps = new Map<number, EntryOverlap>();

  // Only check stopped entries for overlaps
  const stoppedEntries = entries.filter((e) => e.endTime !== null);

  for (let i = 0; i < stoppedEntries.length; i++) {
    for (let j = i + 1; j < stoppedEntries.length; j++) {
      const entry1 = stoppedEntries[i];
      const entry2 = stoppedEntries[j];

      if (hasSignificantOverlap(entry1.startTime, entry1.endTime, entry2.startTime, entry2.endTime)) {
        // Store overlap for entry1
        if (!overlaps.has(entry1.id)) {
          overlaps.set(entry1.id, { overlappingEntryTitle: entry2.title });
        }

        // Store overlap for entry2
        if (!overlaps.has(entry2.id)) {
          overlaps.set(entry2.id, { overlappingEntryTitle: entry1.title });
        }
      }
    }
  }

  return overlaps;
}
