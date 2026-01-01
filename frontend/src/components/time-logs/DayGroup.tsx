import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDuration } from "@/lib/time-utils";
import { TimeEntry } from "./TimeEntry";
import { GroupedTimeEntry } from "./GroupedTimeEntry";
import { groupEntriesByTitleAndTags, isGroupedEntry } from "@/lib/entry-grouping";
import { detectOverlaps } from "@/lib/overlap-detection";
import type { DayGroup as DayGroupType, TimeLogEntry } from "./types";

interface DayGroupProps {
  group: DayGroupType;
  locale: string;
  startOfWeek: number;
  editingEntryId: number | null;
  isSaving: boolean;
  onContinue: (entry: TimeLogEntry) => void;
  onDelete: (entry: TimeLogEntry) => void;
  onEdit: (entry: TimeLogEntry) => void;
  onSaveEdit: (entry: TimeLogEntry, title: string, startTime: string, endTime: string, tags: string[]) => Promise<void>;
  onCancelEdit: () => void;
}

export function DayGroup({
  group,
  locale,
  startOfWeek,
  editingEntryId,
  isSaving,
  onContinue,
  onDelete,
  onEdit,
  onSaveEdit,
  onCancelEdit,
}: DayGroupProps) {
  const { t } = useTranslation();

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
            {t("timeLogs.totalDuration")}: {formatDuration(group.totalDuration)}
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
                  editingEntryId={editingEntryId}
                  isSaving={isSaving}
                  onContinue={onContinue}
                  onDelete={onDelete}
                  onEdit={onEdit}
                  onSaveEdit={onSaveEdit}
                  onCancelEdit={onCancelEdit}
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
                  isEditing={editingEntryId === item.id}
                  isSaving={isSaving}
                  onContinue={onContinue}
                  onDelete={onDelete}
                  onEdit={onEdit}
                  onSaveEdit={onSaveEdit}
                  onCancelEdit={onCancelEdit}
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
