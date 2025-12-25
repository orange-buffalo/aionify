import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Play, MoreVertical, Trash2 } from "lucide-react"
import { formatTime } from "@/lib/date-format"
import { calculateDuration, formatDuration } from "@/lib/time-utils"
import type { TimeLogEntry } from "./types"

interface TimeEntryProps {
  entry: TimeLogEntry
  locale: string
  onContinue: (entry: TimeLogEntry) => void
  onDelete: (entry: TimeLogEntry) => void
}

export function TimeEntry({ entry, locale, onContinue, onDelete }: TimeEntryProps) {
  const { t } = useTranslation()
  const duration = calculateDuration(entry.startTime, entry.endTime)

  return (
    <div
      className="flex items-center justify-between p-3 border border-border rounded-md"
      data-testid="time-entry"
    >
      <div className="flex-1">
        <p className="font-medium text-foreground" data-testid="entry-title">{entry.title}</p>
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
