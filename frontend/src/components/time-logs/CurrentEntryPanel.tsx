import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Play, Square, Pencil } from "lucide-react";
import { formatDateTime } from "@/lib/date-format";
import { formatDuration, calculateDuration } from "@/lib/time-utils";
import { EditEntryForm } from "./EditEntryForm";
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
  const [activeDuration, setActiveDuration] = useState<number>(0);
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
  const {
    executeApiCall: executeEditCall,
    apiCallInProgress: isSaving,
    formMessage: editFormMessage,
  } = useApiExecutor("edit-entry");
  const [newEntryTitle, setNewEntryTitle] = useState("");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [isEditMode, setIsEditMode] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editDateTime, setEditDateTime] = useState<Date>(new Date());
  const [editTags, setEditTags] = useState<string[]>([]);

  // Auto-refresh active entry timer every second
  useEffect(() => {
    if (!activeEntry) {
      setActiveDuration(0);
      return;
    }

    setActiveDuration(calculateDuration(activeEntry.startTime, null));

    const interval = setInterval(() => {
      setActiveDuration(calculateDuration(activeEntry.startTime, null));
    }, 1000);

    return () => clearInterval(interval);
  }, [activeEntry?.id, activeEntry?.startTime]);

  // Cancel edit mode when editing a stopped entry
  if (isEditMode && isEditingStoppedEntry) {
    setIsEditMode(false);
  }

  const handleStart = async () => {
    await executeStartCall(async () => {
      await apiPost<TimeEntry>("/api-ui/time-log-entries", {
        title: newEntryTitle.trim(),
        tags: selectedTags,
      });
      await onDataChange();
      setNewEntryTitle("");
      setSelectedTags([]);
    });
  };

  const handleEditClick = () => {
    if (!activeEntry) return;
    setEditTitle(activeEntry.title);
    setEditDateTime(new Date(activeEntry.startTime));
    setEditTags(activeEntry.tags || []);
    setIsEditMode(true);
  };

  const handleSaveEdit = async () => {
    if (!activeEntry || !editTitle.trim()) return;
    await executeEditCall(async () => {
      const startTimeISO = editDateTime.toISOString();
      await apiPut<TimeEntry>(`/api-ui/time-log-entries/${activeEntry.id}`, {
        title: editTitle.trim(),
        startTime: startTimeISO,
        tags: editTags,
      });
      await onDataChange();
      setIsEditMode(false);
    });
  };

  const handleCancelEdit = () => {
    setIsEditMode(false);
    setEditTitle("");
    setEditDateTime(new Date());
    setEditTags([]);
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
        <CardTitle className="text-foreground">
          {isEditMode ? t("timeLogs.currentEntry.editTitle") : t("timeLogs.currentEntry.title")}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {startFormMessage}
        {stopFormMessage}
        {editFormMessage}
        {activeEntry ? (
          isEditMode ? (
            /* Edit Mode */
            <EditEntryForm
              title={editTitle}
              startDateTime={editDateTime}
              locale={locale}
              startOfWeek={startOfWeek}
              isSaving={isSaving}
              tags={editTags}
              onTitleChange={setEditTitle}
              onStartDateTimeChange={setEditDateTime}
              onTagsChange={setEditTags}
              onSave={handleSaveEdit}
              onCancel={handleCancelEdit}
              testIdPrefix="edit"
            />
          ) : (
            /* View Mode */
            <div className="flex items-center justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <p className="font-semibold text-foreground" data-testid="active-entry-title">{activeEntry.title}</p>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleEditClick}
                    data-testid="edit-entry-button"
                    className="text-foreground h-6 w-6 p-0"
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                </div>
                <div className="text-sm text-muted-foreground" data-testid="active-entry-started-at">
                  {t("timeLogs.startedAt")}: {formatDateTime(activeEntry.startTime, locale)}
                </div>
              </div>
              <div className="flex items-center gap-4">
                <div className="text-2xl font-mono font-bold text-foreground" data-testid="active-timer">
                  {formatDuration(activeDuration)}
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
          )
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
