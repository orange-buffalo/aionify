import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Play } from "lucide-react";
import { formatTime } from "@/lib/date-format";
import { calculateDuration, formatDuration } from "@/lib/time-utils";
import type { EntryOverlap } from "@/lib/overlap-detection";
import type { GroupedTimeLogEntry, TimeLogEntry } from "@/components/time-logs/types";
import { TimeEntry } from "./TimeEntry";

interface GroupedTimeEntryProps {
  groupedEntry: GroupedTimeLogEntry;
  locale: string;
  startOfWeek: number;
  editingEntryId: number | null;
  isSaving: boolean;
  onContinue: (entry: TimeLogEntry) => void;
  onDelete: (entry: TimeLogEntry) => void;
  onEdit: (entry: TimeLogEntry) => void;
  onSaveEdit: (entry: TimeLogEntry, title: string, startTime: string, endTime: string, tags: string[]) => Promise<void>;
  onCancelEdit: () => void;
  overlaps: Map<number, EntryOverlap>;
}

export function GroupedTimeEntry({
  groupedEntry,
  locale,
  startOfWeek,
  editingEntryId,
  isSaving,
  onContinue,
  onDelete,
  onEdit,
  onSaveEdit,
  onCancelEdit,
  overlaps,
}: GroupedTimeEntryProps) {
  const { t } = useTranslation();
  const [isExpanded, setIsExpanded] = useState(false);

  const endTimeDisplay = groupedEntry.endTime ? formatTime(groupedEntry.endTime, locale) : t("timeLogs.inProgress");

  // Use the first entry for the continue action
  const firstEntry = groupedEntry.entries[0];

  // Check if group contains an active entry
  const hasActiveEntry = groupedEntry.entries.some((e) => e.endTime == null);

  // Calculate total duration dynamically for groups with active entries
  const totalDuration = hasActiveEntry
    ? groupedEntry.entries.reduce((sum, entry) => {
        const duration = calculateDuration(entry.startTime, entry.endTime);
        return sum + duration;
      }, 0)
    : groupedEntry.totalDuration;

  return (
    <div data-testid="grouped-time-entry">
      {/* Main grouped entry row */}
      <div
        className="flex items-center justify-between p-3 border border-border rounded-md"
        data-testid="grouped-entry-header"
      >
        <div className="flex-1">
          <div className="flex items-center gap-2">
            {/* Count badge */}
            <button
              onClick={() => setIsExpanded(!isExpanded)}
              data-testid="entry-count-badge"
              title={isExpanded ? t("timeLogs.groupedEntry.collapse") : t("timeLogs.groupedEntry.expand")}
              className={`
                flex items-center justify-center
                w-6 h-6
                rounded
                text-xs font-semibold
                cursor-pointer
                transition-colors
                ${
                  isExpanded
                    ? "bg-primary/20 text-primary border border-primary/50"
                    : "bg-muted text-foreground border border-border hover:bg-muted/80"
                }
              `}
            >
              {groupedEntry.entries.length}
            </button>
            <p className="font-medium text-foreground" data-testid="entry-title">
              {groupedEntry.title}
            </p>
          </div>
          {groupedEntry.tags && groupedEntry.tags.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-1.5" data-testid="entry-tags">
              {groupedEntry.tags.map((tag, index) => (
                <Badge key={index} variant="default" className="text-[0.7rem]" data-testid={`entry-tag-${index}`}>
                  {tag}
                </Badge>
              ))}
            </div>
          )}
        </div>
        <div className="flex items-center gap-4 text-sm">
          <div className="flex items-center gap-2 text-muted-foreground" data-testid="entry-time-range">
            <span>
              {formatTime(groupedEntry.startTime, locale)} - {endTimeDisplay}
            </span>
          </div>
          <div className="font-mono font-semibold text-foreground min-w-[70px] text-right" data-testid="entry-duration">
            {formatDuration(totalDuration)}
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onContinue(firstEntry)}
              data-testid="continue-button"
              className="text-foreground"
              title={t("timeLogs.startFromEntry")}
            >
              <Play className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      {/* Expanded entries */}
      {isExpanded && (
        <div className="ml-8 mt-2 space-y-2" data-testid="grouped-entries-expanded">
          {groupedEntry.entries.map((entry) => {
            const overlap = overlaps.get(entry.id);
            return (
              <TimeEntry
                key={`${entry.id}-${entry.startTime}`}
                entry={entry}
                locale={locale}
                startOfWeek={startOfWeek}
                isEditing={editingEntryId === entry.id}
                isSaving={isSaving}
                onContinue={onContinue}
                onDelete={onDelete}
                onEdit={onEdit}
                onSaveEdit={onSaveEdit}
                onCancelEdit={onCancelEdit}
                hideTitle={false}
                hideTags={true}
                hideContinue={true}
                overlap={overlap}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
