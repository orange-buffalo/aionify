import { useState, useEffect } from "react";
import { calculateDuration, formatDuration } from "@/lib/time-utils";

interface DurationDisplayProps {
  startTime: string;
  endTime: string | null;
  className?: string;
  testId?: string;
}

/**
 * A component that displays a duration and updates automatically for active entries.
 * This component re-renders every second for active entries (endTime === null),
 * while the parent component remains stable.
 */
export function DurationDisplay({ startTime, endTime, className, testId }: DurationDisplayProps) {
  const [duration, setDuration] = useState(() => calculateDuration(startTime, endTime));

  // Update duration when start/end times change
  useEffect(() => {
    setDuration(calculateDuration(startTime, endTime));
  }, [startTime, endTime]);

  // Update duration every second for active entries
  useEffect(() => {
    if (endTime !== null) return; // Only for active entries

    const interval = setInterval(() => {
      const newDuration = calculateDuration(startTime, endTime);
      setDuration(newDuration);
    }, 1000);

    return () => clearInterval(interval);
  }, [startTime, endTime]);

  return (
    <span className={className} data-testid={testId}>
      {formatDuration(duration)}
    </span>
  );
}
