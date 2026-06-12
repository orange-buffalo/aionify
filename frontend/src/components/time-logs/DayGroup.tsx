import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { TimeEntry } from "./TimeEntry";
import { GroupedTimeEntry } from "./GroupedTimeEntry";
import { TotalDurationDisplay } from "./TotalDurationDisplay";
import { DailyGoalProgressBar } from "./DailyGoalProgressBar";
import { groupEntriesByTitleAndTags, isGroupedEntry } from "@/lib/entry-grouping";
import { detectOverlaps } from "@/lib/overlap-detection";
import type { DailyGoalSettings, DayGroup as DayGroupType } from "./types";

interface DayGroupProps {
  group: DayGroupType;
  locale: string;
  startOfWeek: number;
  dailyGoal: DailyGoalSettings | null;
  onDataChange: () => Promise<void>;
}

export function DayGroup({ group, locale, startOfWeek, dailyGoal, onDataChange }: DayGroupProps) {
  const { t } = useTranslation();

  // Detect overlaps within this day group
  const overlaps = detectOverlaps(group.entries);

  // Group entries by title and tags
  const groupedEntries = groupEntriesByTitleAndTags(group.entries);

  return (
    <Card className="border-none shadow-md" data-testid="day-group">
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <CardTitle className="text-lg text-foreground" data-testid="day-title">
            {group.displayTitle}
          </CardTitle>
          {group.isToday && dailyGoal?.enabled && (
            <DailyGoalProgressBar entries={group.entries} dailyGoal={dailyGoal} locale={locale} />
          )}
          <div className="text-sm text-muted-foreground" data-testid="day-total-duration">
            {t("timeLogs.totalDuration")}: <TotalDurationDisplay entries={group.entries} />
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {groupedEntries.map((item) => {
            if (isGroupedEntry(item)) {
              return (
                <GroupedTimeEntry
                  key={item.entries
                    .map((e) => e.id)
                    .sort((a, b) => a - b)
                    .join(",")}
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
