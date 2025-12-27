import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Play, Square, Pencil } from "lucide-react";
import { formatDateTime } from "@/lib/date-format";
import { formatDuration } from "@/lib/time-utils";
import { EditEntryForm } from "./EditEntryForm";
import { TagSelector } from "./TagSelector";
import type { TimeEntry } from "./types";

interface CurrentEntryPanelProps {
  activeEntry: TimeEntry | null;
  activeDuration: number;
  locale: string;
  isStarting: boolean;
  isStopping: boolean;
  isSaving: boolean;
  isEditingStoppedEntry: boolean;
  onStart: (title: string, tags?: string[]) => Promise<void>;
  onStop: () => Promise<void>;
  onSaveEdit: (title: string, startTime: string) => Promise<void>;
  onEditStart: () => void;
}

export function CurrentEntryPanel({
  activeEntry,
  activeDuration,
  locale,
  isStarting,
  isStopping,
  isSaving,
  isEditingStoppedEntry,
  onStart,
  onStop,
  onSaveEdit,
  onEditStart,
}: CurrentEntryPanelProps) {
  const { t } = useTranslation();
  const [newEntryTitle, setNewEntryTitle] = useState("");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [isEditMode, setIsEditMode] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editDateTime, setEditDateTime] = useState<Date>(new Date());

  // Cancel edit mode when editing a stopped entry
  if (isEditMode && isEditingStoppedEntry) {
    setIsEditMode(false);
  }

  const handleStart = async () => {
    if (!newEntryTitle.trim()) return;
    await onStart(newEntryTitle.trim(), selectedTags);
    setNewEntryTitle("");
    setSelectedTags([]);
  };

  const handleEditClick = () => {
    if (!activeEntry) return;
    setEditTitle(activeEntry.title);
    setEditDateTime(new Date(activeEntry.startTime));
    setIsEditMode(true);
    onEditStart();
  };

  const handleSaveEdit = async () => {
    if (!activeEntry || !editTitle.trim()) return;
    const startTimeISO = editDateTime.toISOString();
    await onSaveEdit(editTitle.trim(), startTimeISO);
    setIsEditMode(false);
  };

  const handleCancelEdit = () => {
    setIsEditMode(false);
    setEditTitle("");
    setEditDateTime(new Date());
  };

  return (
    <Card className="mb-6 border-none shadow-md" data-testid="current-entry-panel">
      <CardHeader>
        <CardTitle className="text-foreground">
          {isEditMode ? t("timeLogs.currentEntry.editTitle") : t("timeLogs.currentEntry.title")}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {activeEntry ? (
          isEditMode ? (
            /* Edit Mode */
            <EditEntryForm
              title={editTitle}
              startDateTime={editDateTime}
              locale={locale}
              isSaving={isSaving}
              onTitleChange={setEditTitle}
              onStartDateTimeChange={setEditDateTime}
              onSave={handleSaveEdit}
              onCancel={handleCancelEdit}
              testIdPrefix="edit"
            />
          ) : (
            /* View Mode */
            <div className="flex items-center justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <p className="font-semibold text-foreground">{activeEntry.title}</p>
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
                <div
                  className="text-sm text-muted-foreground"
                  data-testid="active-entry-started-at"
                >
                  {t("timeLogs.startedAt")}: {formatDateTime(activeEntry.startTime, locale)}
                </div>
              </div>
              <div className="flex items-center gap-4">
                <div
                  className="text-2xl font-mono font-bold text-foreground"
                  data-testid="active-timer"
                >
                  {formatDuration(activeDuration)}
                </div>
                <Button
                  onClick={onStop}
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
            <Input
              value={newEntryTitle}
              onChange={(e) => setNewEntryTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && newEntryTitle.trim()) {
                  handleStart();
                }
              }}
              placeholder={t("timeLogs.currentEntry.placeholder")}
              className="flex-1 text-foreground"
              data-testid="new-entry-input"
              disabled={isStarting}
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
              disabled={!newEntryTitle.trim() || isStarting}
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
