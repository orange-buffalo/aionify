import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Play, Square } from "lucide-react";
import { formatDateTime } from "@/lib/date-format";
import { DurationDisplay } from "./DurationDisplay";
import { TagSelector } from "./TagSelector";
import { EntryAutocomplete } from "./EntryAutocomplete";
import { apiPost, apiPut } from "@/lib/api";
import { useApiExecutor } from "@/hooks/useApiExecutor";
import type { TimeEntry } from "./types";

interface CurrentEntryPanelProps {
  activeEntry: TimeEntry | null;
  locale: string;
  startOfWeek: number;
  isEditingStoppedEntry: boolean;
  onDataChange: () => Promise<void>;
}

export function CurrentEntryPanel({
  activeEntry,
  locale,
  startOfWeek,
  isEditingStoppedEntry,
  onDataChange,
}: CurrentEntryPanelProps) {
  const { t } = useTranslation();
  const {
    executeApiCall: executeStartCall,
    apiCallInProgress: isStarting,
    formMessage: startFormMessage,
  } = useApiExecutor("start-entry");
  const {
    executeApiCall: executeStopCall,
    apiCallInProgress: isStopping,
    formMessage: stopFormMessage,
  } = useApiExecutor("stop-entry");
  const [newEntryTitle, setNewEntryTitle] = useState("");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  const handleStart = async () => {
    await executeStartCall(async () => {
      // Auto-populate title with default if empty
      const titleToSend = newEntryTitle.trim() || "New Entry";
      await apiPost<TimeEntry>("/api-ui/time-log-entries", {
        title: titleToSend,
        tags: selectedTags,
      });
      await onDataChange();
      setNewEntryTitle("");
      setSelectedTags([]);
    });
  };

  const handleStop = async () => {
    if (!activeEntry) return;
    await executeStopCall(async () => {
      await apiPut(`/api-ui/time-log-entries/${activeEntry.id}/stop`, {});
      await onDataChange();
    });
  };

  return (
    <Card className="mb-6 border-none shadow-md" data-testid="current-entry-panel">
      <CardHeader>
        <CardTitle className="text-foreground">{t("timeLogs.currentEntry.title")}</CardTitle>
      </CardHeader>
      <CardContent>
        {startFormMessage}
        {stopFormMessage}
        {activeEntry ? (
          /* View Mode */
          <div className="flex items-center justify-between">
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-1">
                <p className="font-semibold text-foreground">{activeEntry.title}</p>
              </div>
              <div className="text-sm text-muted-foreground" data-testid="active-entry-started-at">
                {t("timeLogs.startedAt")}: {formatDateTime(activeEntry.startTime, locale)}
              </div>
            </div>
            <div className="flex items-center gap-4">
              <div className="text-2xl font-mono font-bold text-foreground" data-testid="active-timer">
                <DurationDisplay startTime={activeEntry.startTime} endTime={null} />
              </div>
              <Button
                onClick={handleStop}
                disabled={isStopping}
                data-testid="stop-button"
                className="bg-teal-600 hover:bg-teal-700"
              >
                <Square className="h-4 w-4" />
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <EntryAutocomplete
              value={newEntryTitle}
              onChange={setNewEntryTitle}
              onSelect={(entry) => {
                setNewEntryTitle(entry.title);
                setSelectedTags(entry.tags);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  handleStart();
                }
              }}
              placeholder={t("timeLogs.currentEntry.placeholder")}
              disabled={isStarting}
              testId="new-entry-input"
            />
            <TagSelector
              selectedTags={selectedTags}
              onTagsChange={setSelectedTags}
              disabled={isStarting}
              testIdPrefix="new-entry-tags"
            />
            <Button
              onClick={handleStart}
              className="bg-teal-600 hover:bg-teal-700"
              disabled={isStarting}
              data-testid="start-button"
            >
              <Play className="h-4 w-4" />
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
