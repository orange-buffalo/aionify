import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { DatePicker } from "@/components/ui/date-picker"
import { TimePicker } from "@/components/ui/time-picker"
import { Play, Square, Pencil } from "lucide-react"
import { formatDateTime } from "@/lib/date-format"
import { formatDuration } from "@/lib/time-utils"
import type { TimeEntry } from "./types"

interface CurrentEntryPanelProps {
  activeEntry: TimeEntry | null
  activeDuration: number
  locale: string
  isStarting: boolean
  isStopping: boolean
  isSaving: boolean
  onStart: (title: string) => Promise<void>
  onStop: () => Promise<void>
  onSaveEdit: (title: string, startTime: string) => Promise<void>
}

export function CurrentEntryPanel({
  activeEntry,
  activeDuration,
  locale,
  isStarting,
  isStopping,
  isSaving,
  onStart,
  onStop,
  onSaveEdit,
}: CurrentEntryPanelProps) {
  const { t } = useTranslation()
  const [newEntryTitle, setNewEntryTitle] = useState("")
  const [isEditMode, setIsEditMode] = useState(false)
  const [editTitle, setEditTitle] = useState("")
  const [editDateTime, setEditDateTime] = useState<Date>(new Date())

  const handleStart = async () => {
    if (!newEntryTitle.trim()) return
    await onStart(newEntryTitle.trim())
    setNewEntryTitle("")
  }

  const handleEditClick = () => {
    if (!activeEntry) return
    setEditTitle(activeEntry.title)
    setEditDateTime(new Date(activeEntry.startTime))
    setIsEditMode(true)
  }

  const handleSaveEdit = async () => {
    if (!activeEntry || !editTitle.trim()) return
    const startTimeISO = editDateTime.toISOString()
    await onSaveEdit(editTitle.trim(), startTimeISO)
    setIsEditMode(false)
  }

  const handleCancelEdit = () => {
    setIsEditMode(false)
    setEditTitle("")
    setEditDateTime(new Date())
  }

  return (
    <Card className="mb-6 border-none shadow-md" data-testid="current-entry-panel">
      <CardHeader>
        <CardTitle className="text-foreground">
          {isEditMode ? t('timeLogs.currentEntry.editTitle') : t('timeLogs.currentEntry.title')}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {activeEntry ? (
          isEditMode ? (
            /* Edit Mode */
            <div className="space-y-4">
              <div>
                <Label htmlFor="edit-title" className="text-foreground">
                  {t('timeLogs.currentEntry.titleLabel')}
                </Label>
                <Input
                  id="edit-title"
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  className="text-foreground mt-2"
                  data-testid="edit-title-input"
                  disabled={isSaving}
                />
              </div>
              <div>
                <Label className="text-foreground">
                  {t('timeLogs.currentEntry.startTimeLabel')}
                </Label>
                <div className="mt-2 flex items-center gap-2">
                  <DatePicker
                    value={editDateTime}
                    onChange={setEditDateTime}
                    disabled={isSaving}
                    locale={locale}
                    testIdPrefix="edit-date"
                  />
                  <TimePicker
                    value={editDateTime}
                    onChange={setEditDateTime}
                    disabled={isSaving}
                    locale={locale}
                    testIdPrefix="edit-time"
                  />
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  onClick={handleSaveEdit}
                  disabled={!editTitle.trim() || isSaving}
                  data-testid="save-edit-button"
                  className="bg-teal-600 hover:bg-teal-700"
                >
                  {t('timeLogs.currentEntry.save')}
                </Button>
                <Button
                  variant="ghost"
                  onClick={handleCancelEdit}
                  disabled={isSaving}
                  data-testid="cancel-edit-button"
                  className="text-foreground"
                >
                  {t('timeLogs.currentEntry.cancel')}
                </Button>
              </div>
            </div>
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
                <div className="text-sm text-muted-foreground" data-testid="active-entry-started-at">
                  {t('timeLogs.startedAt')}: {formatDateTime(activeEntry.startTime, locale)}
                </div>
              </div>
              <div className="flex items-center gap-4">
                <div className="text-2xl font-mono font-bold text-foreground" data-testid="active-timer">
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
          <div className="flex items-center gap-4">
            <Input
              value={newEntryTitle}
              onChange={(e) => setNewEntryTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && newEntryTitle.trim()) {
                  handleStart()
                }
              }}
              placeholder={t('timeLogs.currentEntry.placeholder')}
              className="flex-1 text-foreground"
              data-testid="new-entry-input"
              disabled={isStarting}
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
  )
}
