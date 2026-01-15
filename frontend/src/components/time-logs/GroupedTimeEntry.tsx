import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Play } from "lucide-react";
import { formatTime } from "@/lib/date-format";
import { apiPost, apiPatch } from "@/lib/api";
import { useApiExecutor } from "@/hooks/useApiExecutor";
import { InlineTitleEdit } from "./InlineTitleEdit";
import { InlineTagsEdit } from "./InlineTagsEdit";
import { TotalDurationDisplay } from "./TotalDurationDisplay";
import type { EntryOverlap } from "@/lib/overlap-detection";
import type { GroupedTimeLogEntry, TimeLogEntry, TimeEntry } from "@/components/time-logs/types";
import { TimeEntry as TimeEntryComponent } from "./TimeEntry";

interface GroupedTimeEntryProps {
  groupedEntry: GroupedTimeLogEntry;
  locale: string;
  startOfWeek: number;
  onDataChange: () => Promise<void>;
  overlaps: Map<number, EntryOverlap>;
}

export function GroupedTimeEntry({ groupedEntry, locale, startOfWeek, onDataChange, overlaps }: GroupedTimeEntryProps) {
  const { t } = useTranslation();
  const { executeApiCall: executeContinueCall, apiCallInProgress: isContinuing } = useApiExecutor("continue-entry");
  const [isExpanded, setIsExpanded] = useState(false);

  const endTimeDisplay = groupedEntry.endTime ? formatTime(groupedEntry.endTime, locale) : t("timeLogs.inProgress");

  // Use the first entry for the continue action
  const firstEntry = groupedEntry.entries[0];

  const handleContinue = async () => {
    await executeContinueCall(async () => {
      await apiPost<TimeEntry>("/api-ui/time-log-entries", {
        title: firstEntry.title,
        tags: firstEntry.tags,
        stopActiveEntry: true,
      });
      await onDataChange();
    });
  };

  const handleInlineTitleUpdate = async (newTitle: string) => {
    const entryIds = groupedEntry.entries.map((e) => e.id);
    await apiPatch(`/api-ui/time-log-entries/bulk-update-title`, {
      entryIds,
      title: newTitle,
    });
    await onDataChange();
  };

  const handleInlineTagsUpdate = async (newTags: string[]) => {
    const entryIds = groupedEntry.entries.map((e) => e.id);
    await apiPatch(`/api-ui/time-log-entries/bulk-update-tags`, {
      entryIds,
      tags: newTags,
    });
    await onDataChange();
  };

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
            <div data-testid="entry-title">
              <InlineTitleEdit
                currentTitle={groupedEntry.title}
                onSave={handleInlineTitleUpdate}
                testIdPrefix="grouped-entry-inline-title"
              />
            </div>
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
              {formatTime(groupedEntry.earliestStartTime, locale)} - {endTimeDisplay}
            </span>
          </div>
          <div className="font-mono font-semibold text-foreground min-w-[70px] text-right" data-testid="entry-duration">
            <TotalDurationDisplay entries={groupedEntry.entries} />
          </div>
          <div className="flex items-center gap-2">
            <InlineTagsEdit
              currentTags={groupedEntry.tags || []}
              onSave={handleInlineTagsUpdate}
              testIdPrefix="grouped-entry-inline-tags"
            />
            <Button
              variant="ghost"
              size="sm"
              onClick={handleContinue}
              disabled={isContinuing}
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
              <TimeEntryComponent
                key={`${entry.id}-${entry.startTime}`}
                entry={entry}
                locale={locale}
                startOfWeek={startOfWeek}
                onDataChange={onDataChange}
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
