import { useTranslation } from "react-i18next"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { formatDuration } from "@/lib/time-utils"
import { TimeEntry } from "./TimeEntry"
import type { DayGroup as DayGroupType, TimeLogEntry } from "./types"

interface DayGroupProps {
  group: DayGroupType
  locale: string
  editingEntryId: number | null
  isSaving: boolean
  onContinue: (entry: TimeLogEntry) => void
  onDelete: (entry: TimeLogEntry) => void
  onEdit: (entry: TimeLogEntry) => void
  onSaveEdit: (entry: TimeLogEntry, title: string, startTime: string, endTime: string) => Promise<void>
  onCancelEdit: () => void
}

export function DayGroup({ 
  group, 
  locale, 
  editingEntryId,
  isSaving,
  onContinue, 
  onDelete,
  onEdit,
  onSaveEdit,
  onCancelEdit
}: DayGroupProps) {
  const { t } = useTranslation()

  return (
    <Card className="border-none shadow-md" data-testid="day-group">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg text-foreground" data-testid="day-title">
            {group.displayTitle}
          </CardTitle>
          <div className="text-sm text-muted-foreground" data-testid="day-total-duration">
            {t('timeLogs.totalDuration')}: {formatDuration(group.totalDuration)}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {group.entries.map((entry) => (
            <TimeEntry
              key={`${entry.id}-${entry.startTime}`}
              entry={entry}
              locale={locale}
              isEditing={editingEntryId === entry.id}
              isSaving={isSaving}
              onContinue={onContinue}
              onDelete={onDelete}
              onEdit={onEdit}
              onSaveEdit={onSaveEdit}
              onCancelEdit={onCancelEdit}
            />
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
