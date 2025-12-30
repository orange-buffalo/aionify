import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Play, ChevronDown, ChevronUp } from "lucide-react";
import { formatTime } from "@/lib/date-format";
import { formatDuration } from "@/lib/time-utils";
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
}: GroupedTimeEntryProps) {
  const { t } = useTranslation();
  const [isExpanded, setIsExpanded] = useState(false);

  const endTimeDisplay = groupedEntry.endTime ? formatTime(groupedEntry.endTime, locale) : t("timeLogs.inProgress");

  // Use the first entry for the continue action
  const firstEntry = groupedEntry.entries[0];

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
            <Badge
              variant="outline"
              className="text-foreground border-border cursor-pointer hover:bg-muted"
              onClick={() => setIsExpanded(!isExpanded)}
              data-testid="entry-count-badge"
              title={isExpanded ? t("timeLogs.groupedEntry.collapse") : t("timeLogs.groupedEntry.expand")}
            >
              {isExpanded ? <ChevronUp className="h-3 w-3 mr-1" /> : <ChevronDown className="h-3 w-3 mr-1" />}
              {groupedEntry.entries.length}
            </Badge>
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
            {formatDuration(groupedEntry.totalDuration)}
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
          {groupedEntry.entries.map((entry) => (
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
              hideTitle={true}
              hideTags={true}
              hideContinue={true}
            />
          ))}
        </div>
      )}
    </div>
  );
}
