import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDuration, calculateDuration } from "@/lib/time-utils";
import { TimeEntry } from "./TimeEntry";
import { GroupedTimeEntry } from "./GroupedTimeEntry";
import { groupEntriesByTitleAndTags, isGroupedEntry } from "@/lib/entry-grouping";
import { detectOverlaps } from "@/lib/overlap-detection";
import type { DayGroup as DayGroupType, TimeLogEntry } from "./types";

interface DayGroupProps {
  group: DayGroupType;
  locale: string;
  startOfWeek: number;
  onDataChange: () => Promise<void>;
}

export function DayGroup({ group, locale, startOfWeek, onDataChange }: DayGroupProps) {
  const { t } = useTranslation();
  const [totalDuration, setTotalDuration] = useState<number>(group.totalDuration);

  // Check if this group contains an active entry
  const hasActiveEntry = group.entries.some((e) => e.endTime == null);

  // Calculate total duration for all entries in this group
  function calculateGroupTotalDuration(): number {
    return group.entries.reduce((sum, entry) => {
      const duration = calculateDuration(entry.startTime, entry.endTime);
      return sum + duration;
    }, 0);
  }

  // Update total duration when entries change
  useEffect(() => {
    const total = calculateGroupTotalDuration();
    setTotalDuration(total);
  }, [group.entries]);

  // Update total duration every second only if there's an active entry
  useEffect(() => {
    if (!hasActiveEntry) return;

    const interval = setInterval(() => {
      const updatedTotal = calculateGroupTotalDuration();
      setTotalDuration(updatedTotal);
    }, 1000);

    return () => clearInterval(interval);
  }, [hasActiveEntry, group.entries]);

  // Detect overlaps within this day group
  const overlaps = detectOverlaps(group.entries);

  // Group entries by title and tags
  const groupedEntries = groupEntriesByTitleAndTags(group.entries);

  return (
    <Card className="border-none shadow-md" data-testid="day-group">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg text-foreground" data-testid="day-title">
            {group.displayTitle}
          </CardTitle>
          <div className="text-sm text-muted-foreground" data-testid="day-total-duration">
            {t("timeLogs.totalDuration")}: {formatDuration(totalDuration)}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {groupedEntries.map((item) => {
            if (isGroupedEntry(item)) {
              return (
                <GroupedTimeEntry
                  key={item.groupId}
                  groupedEntry={item}
                  locale={locale}
                  startOfWeek={startOfWeek}
                  onDataChange={onDataChange}
                  overlaps={overlaps}
                />
              );
            } else {
              const overlap = overlaps.get(item.id);
              return (
                <TimeEntry
                  key={`${item.id}-${item.startTime}`}
                  entry={item}
                  locale={locale}
                  startOfWeek={startOfWeek}
                  onDataChange={onDataChange}
                  overlap={overlap}
                />
              );
            }
          })}
        </div>
      </CardContent>
    </Card>
  );
}
