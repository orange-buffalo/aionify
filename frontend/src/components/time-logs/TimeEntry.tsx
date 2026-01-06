import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Play, MoreVertical, Trash2, Pencil, AlertCircle } from "lucide-react";
import { formatTime, formatTimeWithWeekday, formatDate } from "@/lib/date-format";
import { calculateDuration, formatDuration, isDifferentDay } from "@/lib/time-utils";
import { EditEntryForm } from "./EditEntryForm";
import type { EntryOverlap } from "@/lib/overlap-detection";
import type { TimeLogEntry } from "./types";

interface TimeEntryProps {
  entry: TimeLogEntry;
  locale: string;
  startOfWeek: number;
  isEditing: boolean;
  isSaving: boolean;
  onContinue: (entry: TimeLogEntry) => void;
  onDelete: (entry: TimeLogEntry) => void;
  onEdit: (entry: TimeLogEntry) => void;
  onSaveEdit: (entry: TimeLogEntry, title: string, startTime: string, endTime: string, tags: string[]) => Promise<void>;
  onCancelEdit: (entryId: number) => void;
  hideTitle?: boolean;
  hideTags?: boolean;
  hideContinue?: boolean;
  overlap?: EntryOverlap;
}

export function TimeEntry({
  entry,
  locale,
  startOfWeek,
  isEditing,
  isSaving,
  onContinue,
  onDelete,
  onEdit,
  onSaveEdit,
  onCancelEdit,
  hideTitle = false,
  hideTags = false,
  hideContinue = false,
  overlap,
}: TimeEntryProps) {
  const { t } = useTranslation();
  const duration = calculateDuration(entry.startTime, entry.endTime);
  const [editTitle, setEditTitle] = useState(entry.title);
  const [editStartDateTime, setEditStartDateTime] = useState<Date>(new Date(entry.startTime));
  const [editEndDateTime, setEditEndDateTime] = useState<Date>(new Date(entry.endTime || new Date()));
  const [editTags, setEditTags] = useState<string[]>(entry.tags || []);

  const handleEditClick = () => {
    setEditTitle(entry.title);
    setEditStartDateTime(new Date(entry.startTime));
    setEditEndDateTime(new Date(entry.endTime || new Date()));
    setEditTags(entry.tags || []);
    onEdit(entry);
  };

  const handleSaveEdit = async () => {
    if (!editTitle.trim()) return;
    const startTimeISO = editStartDateTime.toISOString();
    const endTimeISO = editEndDateTime.toISOString();
    await onSaveEdit(entry, editTitle.trim(), startTimeISO, endTimeISO, editTags);
  };

  const handleCancelEdit = () => {
    setEditTitle(entry.title);
    setEditStartDateTime(new Date(entry.startTime));
    setEditEndDateTime(new Date(entry.endTime || new Date()));
    setEditTags(entry.tags || []);
    onCancelEdit(entry.id);
  };

  // Check if entry spans to a different day
  const spansDifferentDay = entry.endTime && isDifferentDay(entry.startTime, entry.endTime);
  const endTimeDisplay = entry.endTime
    ? spansDifferentDay
      ? formatTimeWithWeekday(entry.endTime, locale)
      : formatTime(entry.endTime, locale)
    : t("timeLogs.inProgress");

  if (isEditing) {
    return (
      <div className="p-3 border border-border rounded-md" data-testid="time-entry-edit">
        <EditEntryForm
          title={editTitle}
          startDateTime={editStartDateTime}
          endDateTime={editEndDateTime}
          locale={locale}
          startOfWeek={startOfWeek}
          isSaving={isSaving}
          tags={editTags}
          onTitleChange={setEditTitle}
          onStartDateTimeChange={setEditStartDateTime}
          onEndDateTimeChange={setEditEndDateTime}
          onTagsChange={setEditTags}
          onSave={handleSaveEdit}
          onCancel={handleCancelEdit}
          testIdPrefix="stopped-entry-edit"
        />
      </div>
    );
  }

  return (
    <div className="flex items-center justify-between p-3 border border-border rounded-md" data-testid="time-entry">
      <div className="flex-1">
        {!hideTitle && (
          <p className="font-medium text-foreground" data-testid="entry-title">
            {entry.title}
          </p>
        )}
        {!hideTags && entry.tags && entry.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-1.5" data-testid="entry-tags">
            {entry.tags.sort().map((tag, index) => (
              <Badge key={index} variant="default" className="text-[0.7rem]" data-testid={`entry-tag-${index}`}>
                {tag}
              </Badge>
            ))}
          </div>
        )}
      </div>
      <div className="flex items-center gap-4 text-sm">
        <div className="flex items-center gap-2 text-muted-foreground" data-testid="entry-time-range">
          {spansDifferentDay && (
            <Popover>
              <PopoverTrigger asChild>
                <AlertCircle className="h-4 w-4 text-yellow-500 cursor-pointer" data-testid="different-day-warning" />
              </PopoverTrigger>
              <PopoverContent className="dark text-sm" data-testid="different-day-tooltip">
                {t("timeLogs.differentDayWarning", { date: formatDate(entry.endTime!, locale) })}
              </PopoverContent>
            </Popover>
          )}
          {overlap && (
            <Popover>
              <PopoverTrigger asChild>
                <AlertCircle className="h-4 w-4 text-yellow-500 cursor-pointer" data-testid="overlap-warning" />
              </PopoverTrigger>
              <PopoverContent className="dark text-sm" data-testid="overlap-tooltip">
                {t("timeLogs.overlapWarning", { title: overlap.overlappingEntryTitle })}
              </PopoverContent>
            </Popover>
          )}
          <span>
            {formatTime(entry.startTime, locale)} - {endTimeDisplay}
          </span>
        </div>
        <div className="font-mono font-semibold text-foreground min-w-[70px] text-right" data-testid="entry-duration">
          {formatDuration(duration)}
        </div>
        <div className="flex items-center gap-2">
          {!hideContinue && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onContinue(entry)}
              data-testid="continue-button"
              className="text-foreground"
              title={t("timeLogs.startFromEntry")}
            >
              <Play className="h-4 w-4" />
            </Button>
          )}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="sm" data-testid="entry-menu-button" className="text-foreground">
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent className="dark" align="end">
              {entry.endTime && (
                <DropdownMenuItem onClick={handleEditClick} data-testid="edit-menu-item">
                  <Pencil className="h-4 w-4 mr-2" />
                  {t("timeLogs.edit")}
                </DropdownMenuItem>
              )}
              <DropdownMenuItem
                onClick={() => onDelete(entry)}
                data-testid="delete-menu-item"
                className="text-destructive focus:text-destructive"
              >
                <Trash2 className="h-4 w-4 mr-2" />
                {t("timeLogs.delete")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </div>
  );
}
