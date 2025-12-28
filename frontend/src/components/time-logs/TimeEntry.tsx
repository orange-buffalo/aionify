import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Play, MoreVertical, Trash2, Pencil } from "lucide-react"
import { formatTime } from "@/lib/date-format"
import { calculateDuration, formatDuration } from "@/lib/time-utils"
import { EditEntryForm } from "./EditEntryForm"
import type { TimeLogEntry } from "./types"

interface TimeEntryProps {
  entry: TimeLogEntry
  locale: string
  isEditing: boolean
  isSaving: boolean
  onContinue: (entry: TimeLogEntry) => void
  onDelete: (entry: TimeLogEntry) => void
  onEdit: (entry: TimeLogEntry) => void
  onSaveEdit: (entry: TimeLogEntry, title: string, startTime: string, endTime: string) => Promise<void>
  onCancelEdit: () => void
}

export function TimeEntry({
  entry,
  locale,
  isEditing,
  isSaving,
  onContinue,
  onDelete,
  onEdit,
  onSaveEdit,
  onCancelEdit
}: TimeEntryProps) {
  const { t } = useTranslation()
  const duration = calculateDuration(entry.startTime, entry.endTime)
  const [editTitle, setEditTitle] = useState(entry.title)
  const [editStartDateTime, setEditStartDateTime] = useState<Date>(new Date(entry.startTime))
  const [editEndDateTime, setEditEndDateTime] = useState<Date>(new Date(entry.endTime || new Date()))

  const handleEditClick = () => {
    setEditTitle(entry.title)
    setEditStartDateTime(new Date(entry.startTime))
    setEditEndDateTime(new Date(entry.endTime || new Date()))
    onEdit(entry)
  }

  const handleSaveEdit = async () => {
    if (!editTitle.trim()) return
    const startTimeISO = editStartDateTime.toISOString()
    const endTimeISO = editEndDateTime.toISOString()
    await onSaveEdit(entry, editTitle.trim(), startTimeISO, endTimeISO)
  }

  const handleCancelEdit = () => {
    setEditTitle(entry.title)
    setEditStartDateTime(new Date(entry.startTime))
    setEditEndDateTime(new Date(entry.endTime || new Date()))
    onCancelEdit()
  }

  if (isEditing) {
    return (
      <div
        className="p-3 border border-border rounded-md"
        data-testid="time-entry-edit"
      >
        <EditEntryForm
          title={editTitle}
          startDateTime={editStartDateTime}
          endDateTime={editEndDateTime}
          locale={locale}
          isSaving={isSaving}
          onTitleChange={setEditTitle}
          onStartDateTimeChange={setEditStartDateTime}
          onEndDateTimeChange={setEditEndDateTime}
          onSave={handleSaveEdit}
          onCancel={handleCancelEdit}
          testIdPrefix="stopped-entry-edit"
        />
      </div>
    )
  }

  return (
    <div
      className="flex items-center justify-between p-3 border border-border rounded-md"
      data-testid="time-entry"
    >
      <div className="flex-1">
        <p className="font-medium text-foreground" data-testid="entry-title">{entry.title}</p>
        {entry.tags && entry.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-1.5" data-testid="entry-tags">
            {entry.tags.sort().map((tag, index) => (
              <Badge key={index} variant="outline" className="text-[0.7rem]" data-testid={`entry-tag-${index}`}>
                {tag}
              </Badge>
            ))}
          </div>
        )}
      </div>
      <div className="flex items-center gap-4 text-sm">
        <div className="text-muted-foreground" data-testid="entry-time-range">
          {formatTime(entry.startTime, locale)} - {entry.endTime ? formatTime(entry.endTime, locale) : t('timeLogs.inProgress')}
        </div>
        <div className="font-mono font-semibold text-foreground min-w-[70px] text-right" data-testid="entry-duration">
          {formatDuration(duration)}
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onContinue(entry)}
            data-testid="continue-button"
            className="text-foreground"
            title={t('timeLogs.startFromEntry')}
          >
            <Play className="h-4 w-4" />
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="sm"
                data-testid="entry-menu-button"
                className="text-foreground"
              >
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent className="dark" align="end">
              {entry.endTime && (
                <DropdownMenuItem
                  onClick={handleEditClick}
                  data-testid="edit-menu-item"
                >
                  <Pencil className="h-4 w-4 mr-2" />
                  {t('timeLogs.edit')}
                </DropdownMenuItem>
              )}
              <DropdownMenuItem
                onClick={() => onDelete(entry)}
                data-testid="delete-menu-item"
                className="text-destructive focus:text-destructive"
              >
                <Trash2 className="h-4 w-4 mr-2" />
                {t('timeLogs.delete')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </div>
  )
}
