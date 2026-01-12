import { useState, useEffect } from "react";
import { calculateDuration, formatDuration } from "@/lib/time-utils";
import type { TimeLogEntry } from "./types";

interface TotalDurationDisplayProps {
  entries: TimeLogEntry[];
  className?: string;
  testId?: string;
}

/**
 * A component that displays the total duration for a group of entries.
 * Updates automatically every second if any entry is active.
 */
export function TotalDurationDisplay({ entries, className, testId }: TotalDurationDisplayProps) {
  const hasActiveEntry = entries.some((e) => e.endTime == null);

  const calculateTotal = () => {
    return entries.reduce((sum, entry) => {
      return sum + calculateDuration(entry.startTime, entry.endTime);
    }, 0);
  };

  const [totalDuration, setTotalDuration] = useState(() => calculateTotal());

  // Update total when entries change
  useEffect(() => {
    setTotalDuration(calculateTotal());
  }, [entries]);

  // Update total every second if there's an active entry
  useEffect(() => {
    if (!hasActiveEntry) return;

    const interval = setInterval(() => {
      setTotalDuration(calculateTotal());
    }, 1000);

    return () => clearInterval(interval);
  }, [hasActiveEntry, entries]);

  return (
    <span className={className} data-testid={testId}>
      {formatDuration(totalDuration)}
    </span>
  );
}
