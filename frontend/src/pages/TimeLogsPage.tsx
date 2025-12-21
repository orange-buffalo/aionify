import { useState, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { PortalLayout } from "@/components/layout/PortalLayout"
import { FormMessage } from "@/components/ui/form-message"
import { apiGet, apiPost, apiPut, apiDelete } from "@/lib/api"
import { ChevronLeft, ChevronRight, Play, Square, MoreVertical, Copy, Trash2 } from "lucide-react"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"

interface TimeEntry {
  id: number
  startTime: string
  endTime: string | null
  title: string
  ownerId: number
}

interface DayGroup {
  date: string
  displayTitle: string
  entries: TimeEntry[]
  totalDuration: number
}

export function TimeLogsPage() {
  const { t, i18n } = useTranslation()
  const [activeEntry, setActiveEntry] = useState<TimeEntry | null>(null)
  const [activeDuration, setActiveDuration] = useState<number>(0)
  const [weekStart, setWeekStart] = useState<Date>(getWeekStart(new Date()))
  const [entries, setEntries] = useState<TimeEntry[]>([])
  const [dayGroups, setDayGroups] = useState<DayGroup[]>([])
  const [loading, setLoading] = useState(true)
  const [newEntryTitle, setNewEntryTitle] = useState("")
  const [isStarting, setIsStarting] = useState(false)
  const [isStopping, setIsStopping] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [entryToDelete, setEntryToDelete] = useState<TimeEntry | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  // Get the start of the week (Monday)
  function getWeekStart(date: Date): Date {
    const d = new Date(date)
    const day = d.getDay()
    const diff = d.getDate() - day + (day === 0 ? -6 : 1) // Adjust when day is Sunday
    d.setDate(diff)
    d.setHours(0, 0, 0, 0)
    return d
  }

  // Format date as ISO date string (YYYY-MM-DD)
  function formatISODate(date: Date): string {
    return date.toISOString().split('T')[0]
  }

  // Format time according to user's locale
  function formatTime(isoString: string, locale: string): string {
    const date = new Date(isoString)
    return date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit', hour12: false })
  }

  // Format date according to user's locale
  function formatDate(isoString: string, locale: string): string {
    const date = new Date(isoString)
    return date.toLocaleDateString(locale, { month: 'short', day: 'numeric' })
  }

  // Calculate duration in milliseconds
  function calculateDuration(startTime: string, endTime: string | null): number {
    const start = new Date(startTime).getTime()
    const end = endTime ? new Date(endTime).getTime() : Date.now()
    return end - start
  }

  // Format duration in HH:MM:SS
  function formatDuration(ms: number): string {
    const seconds = Math.floor(ms / 1000)
    const hours = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60
    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }

  // Get day title (Today, Yesterday, or day of week + date)
  function getDayTitle(dateStr: string, locale: string): string {
    const date = new Date(dateStr)
    const today = new Date()
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)
    
    today.setHours(0, 0, 0, 0)
    yesterday.setHours(0, 0, 0, 0)
    date.setHours(0, 0, 0, 0)
    
    if (date.getTime() === today.getTime()) {
      return t('timeLogs.today')
    } else if (date.getTime() === yesterday.getTime()) {
      return t('timeLogs.yesterday')
    } else {
      const dayName = date.toLocaleDateString(locale, { weekday: 'long' })
      const formattedDate = formatDate(dateStr, locale)
      return `${dayName}, ${formattedDate}`
    }
  }

  // Group entries by day and handle entries spanning midnight
  function groupEntriesByDay(entries: TimeEntry[], locale: string): DayGroup[] {
    const groups: { [key: string]: TimeEntry[] } = {}
    
    entries.forEach(entry => {
      const startDate = new Date(entry.startTime)
      const endDate = entry.endTime ? new Date(entry.endTime) : new Date()
      
      const startDay = formatISODate(startDate)
      const endDay = formatISODate(endDate)
      
      if (startDay === endDay) {
        // Entry doesn't span midnight
        if (!groups[startDay]) groups[startDay] = []
        groups[startDay].push(entry)
      } else {
        // Entry spans midnight - split it
        // First part: from start to end of day
        const startDayEnd = new Date(startDate)
        startDayEnd.setHours(23, 59, 59, 999)
        
        if (!groups[startDay]) groups[startDay] = []
        groups[startDay].push({
          ...entry,
          endTime: startDayEnd.toISOString()
        })
        
        // Second part: from start of day to end
        const endDayStart = new Date(endDate)
        endDayStart.setHours(0, 0, 0, 0)
        
        if (!groups[endDay]) groups[endDay] = []
        groups[endDay].push({
          ...entry,
          startTime: endDayStart.toISOString()
        })
      }
    })
    
    // Convert to array and sort by date descending
    return Object.entries(groups)
      .map(([date, entries]) => {
        const totalDuration = entries.reduce((sum, entry) => 
          sum + calculateDuration(entry.startTime, entry.endTime), 0
        )
        
        return {
          date,
          displayTitle: getDayTitle(date, locale),
          entries: entries.sort((a, b) => 
            new Date(b.startTime).getTime() - new Date(a.startTime).getTime()
          ),
          totalDuration
        }
      })
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
  }

  // Load time entries for the current week
  async function loadTimeEntries() {
    try {
      setLoading(true)
      setError(null)
      
      // Calculate week boundaries as ISO timestamps
      const weekStartTime = new Date(weekStart)
      weekStartTime.setHours(0, 0, 0, 0)
      const weekEndTime = new Date(weekStart)
      weekEndTime.setDate(weekEndTime.getDate() + 7)
      weekEndTime.setHours(0, 0, 0, 0)
      
      const startTimeStr = weekStartTime.toISOString()
      const endTimeStr = weekEndTime.toISOString()
      
      const response = await apiGet<{ entries: TimeEntry[] }>(
        `/api/time-entries?startTime=${encodeURIComponent(startTimeStr)}&endTime=${encodeURIComponent(endTimeStr)}`
      )
      
      setEntries(response.entries || [])
    } catch (err: any) {
      setError(err.message || t('common.error'))
    } finally {
      setLoading(false)
    }
  }

  // Load active entry
  async function loadActiveEntry() {
    try {
      const response = await apiGet<{ entry: TimeEntry | null }>('/api/time-entries/active')
      setActiveEntry(response.entry)
    } catch (err: any) {
      console.error('Failed to load active entry:', err)
    }
  }

  // Start a new time entry
  async function handleStart() {
    if (!newEntryTitle.trim()) {
      setError(t('timeLogs.errors.titleRequired'))
      return
    }
    
    try {
      setIsStarting(true)
      setError(null)
      
      const entry = await apiPost<TimeEntry>('/api/time-entries', {
        title: newEntryTitle.trim()
      })
      
      setActiveEntry(entry)
      setNewEntryTitle("")
      setSuccess(t('timeLogs.success.started'))
      await loadTimeEntries()
    } catch (err: any) {
      const errorCode = (err as any).errorCode
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`))
      } else {
        setError(err.message || t('common.error'))
      }
    } finally {
      setIsStarting(false)
    }
  }

  // Stop the active time entry
  async function handleStop() {
    if (!activeEntry) return
    
    try {
      setIsStopping(true)
      setError(null)
      
      await apiPut(`/api/time-entries/${activeEntry.id}/stop`, {})
      
      setActiveEntry(null)
      setActiveDuration(0)
      await loadTimeEntries()
    } catch (err: any) {
      const errorCode = (err as any).errorCode
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`))
      } else {
        setError(err.message || t('common.error'))
      }
    } finally {
      setIsStopping(false)
    }
  }

  // Continue with an existing entry's title
  async function handleContinue(entry: TimeEntry) {
    setNewEntryTitle(entry.title)
    
    // Start the entry right away
    try {
      setIsStarting(true)
      setError(null)
      
      const newEntry = await apiPost<TimeEntry>('/api/time-entries', {
        title: entry.title
      })
      
      setActiveEntry(newEntry)
      setNewEntryTitle("")
      await loadTimeEntries()
    } catch (err: any) {
      const errorCode = (err as any).errorCode
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`))
      } else {
        setError(err.message || t('common.error'))
      }
    } finally {
      setIsStarting(false)
    }
  }

  // Open delete confirmation dialog
  function handleDeleteClick(entry: TimeEntry) {
    setEntryToDelete(entry)
    setDeleteDialogOpen(true)
  }

  // Delete a time entry
  async function handleDelete() {
    if (!entryToDelete) return
    
    try {
      setIsDeleting(true)
      setError(null)
      
      await apiDelete(`/api/time-entries/${entryToDelete.id}`)
      
      setIsDeleting(false)
      setDeleteDialogOpen(false)
      setEntryToDelete(null)
      await loadTimeEntries()
      
      // If deleting active entry, clear it
      if (activeEntry && activeEntry.id === entryToDelete.id) {
        setActiveEntry(null)
      }
    } catch (err: any) {
      const errorCode = (err as any).errorCode
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`))
      } else {
        setError(err.message || t('common.error'))
      }
    } finally {
      setIsDeleting(false)
    }
  }

  // Navigate to previous week
  function handlePreviousWeek() {
    const newWeekStart = new Date(weekStart)
    newWeekStart.setDate(newWeekStart.getDate() - 7)
    setWeekStart(newWeekStart)
  }

  // Navigate to next week
  function handleNextWeek() {
    const newWeekStart = new Date(weekStart)
    newWeekStart.setDate(newWeekStart.getDate() + 7)
    setWeekStart(newWeekStart)
  }

  // Get week range display
  function getWeekRangeDisplay(): string {
    const locale = i18n.language || 'en'
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekEnd.getDate() + 6)
    
    const startStr = weekStart.toLocaleDateString(locale, { month: 'short', day: 'numeric' })
    const endStr = weekEnd.toLocaleDateString(locale, { month: 'short', day: 'numeric' })
    
    return `${startStr} - ${endStr}`
  }

  // Load data on mount and when week changes
  useEffect(() => {
    loadTimeEntries()
    loadActiveEntry()
  }, [weekStart])

  // Check for date changes periodically (every minute) to auto-update week
  useEffect(() => {
    const interval = setInterval(() => {
      const currentWeekStart = getWeekStart(new Date())
      if (currentWeekStart.getTime() !== weekStart.getTime()) {
        setWeekStart(currentWeekStart)
      }
    }, 60000) // Check every minute
    
    return () => clearInterval(interval)
  }, [weekStart])

  // Auto-refresh active entry timer every second
  useEffect(() => {
    if (!activeEntry) {
      setActiveDuration(0)
      return
    }
    
    // Update duration immediately
    setActiveDuration(calculateDuration(activeEntry.startTime, null))
    
    // Then update every second
    const interval = setInterval(() => {
      setActiveDuration(calculateDuration(activeEntry.startTime, null))
    }, 1000)
    
    return () => clearInterval(interval)
  }, [activeEntry?.id, activeEntry?.startTime])

  // Recalculate day groups when entries change or when there's an active entry (to update durations)
  useEffect(() => {
    const locale = i18n.language || 'en'
    const groups = groupEntriesByDay(entries, locale)
    setDayGroups(groups)

    // If there's an active entry, update day groups every second to reflect changing duration
    if (!activeEntry) return

    const interval = setInterval(() => {
      const updatedGroups = groupEntriesByDay(entries, locale)
      setDayGroups(updatedGroups)
    }, 1000)

    return () => clearInterval(interval)
  }, [entries, activeEntry, i18n.language])

  const locale = i18n.language || 'en'
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone

  return (
    <PortalLayout testId="time-logs-page">
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground mb-2" data-testid="time-logs-title">
              {t('timeLogs.title')}
            </h1>
            <div className="text-muted-foreground">{t('timeLogs.subtitle')}</div>
          </div>

          {/* Error Message */}
          {error && (
            <FormMessage
              type="error"
              message={error}
              testId="time-logs-error"
            />
          )}

          {/* Current Entry Panel */}
          <Card className="mb-6 bg-card/90" data-testid="current-entry-panel">
            <CardHeader>
              <CardTitle className="text-foreground">{t('timeLogs.currentEntry.title')}</CardTitle>
            </CardHeader>
            <CardContent>
              {activeEntry ? (
                <div className="flex items-center justify-between">
                  <div className="flex-1">
                    <p className="font-semibold text-foreground mb-1">{activeEntry.title}</p>
                    <div className="text-sm text-muted-foreground">
                      {t('timeLogs.startedAt')}: {formatTime(activeEntry.startTime, locale)}
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
                    >
                      <Square className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
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
                    placeholder={t('timeLogs.enterTask')}
                    className="flex-1 text-foreground"
                    data-testid="new-entry-input"
                    disabled={isStarting}
                  />
                  <Button
                    onClick={handleStart}
                    disabled={!newEntryTitle.trim() || isStarting}
                    data-testid="start-button"
                  >
                    <Play className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Week Navigation */}
          <div className="flex items-center justify-between mb-6">
            <Button
              variant="ghost"
              onClick={handlePreviousWeek}
              data-testid="previous-week-button"
              className="text-foreground"
            >
              <ChevronLeft className="h-4 w-4 mr-2" />
              {t('timeLogs.previousWeek')}
            </Button>
            <h2 className="text-xl font-semibold text-foreground" data-testid="week-range">
              {getWeekRangeDisplay()}
            </h2>
            <Button
              variant="ghost"
              onClick={handleNextWeek}
              data-testid="next-week-button"
              className="text-foreground"
            >
              {t('timeLogs.nextWeek')}
              <ChevronRight className="h-4 w-4 ml-2" />
            </Button>
          </div>


          {/* Time Entries List */}
          {loading ? (
            <div className="text-center py-8 text-foreground">{t('common.loading')}</div>
          ) : dayGroups.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground" data-testid="no-entries">
              {t('timeLogs.noEntries')}
            </div>
          ) : (
            <div className="space-y-6">
              {dayGroups.map((group) => (
                <Card key={group.date} className="bg-card/90" data-testid="day-group">
                  <CardHeader className="pb-3">
                    <div className="flex items-center justify-between">
                      <CardTitle className="text-lg text-foreground" data-testid="day-title">{group.displayTitle}</CardTitle>
                      <div className="text-sm text-muted-foreground" data-testid="day-total-duration">
                        {t('timeLogs.totalDuration')}: {formatDuration(group.totalDuration)}
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-3">
                      {group.entries.map((entry) => {
                        const duration = calculateDuration(entry.startTime, entry.endTime)
                        const isSpanning = entry.startTime.split('T')[0] !== (entry.endTime || '').split('T')[0]
                        
                        return (
                          <div
                            key={`${entry.id}-${entry.startTime}`}
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
                                  onClick={() => handleContinue(entry)}
                                  data-testid="continue-button"
                                  className="text-foreground"
                                >
                                  <Copy className="h-4 w-4" />
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
                                      onClick={() => handleDeleteClick(entry)}
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
                      })}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}

          {/* Timezone Hint */}
          <div className="mt-8 text-right text-xs text-muted-foreground">
            {t('timeLogs.timezoneHint', { timezone: timeZone })}
          </div>
        </div>
      </div>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent className="dark">
          <DialogHeader>
            <DialogTitle className="text-foreground">{t('timeLogs.deleteDialog.title')}</DialogTitle>
            <DialogDescription className="text-foreground">
              {t('timeLogs.deleteDialog.message')}
            </DialogDescription>
          </DialogHeader>
          {entryToDelete && (
            <div className="mt-2 p-2 bg-muted rounded text-foreground">
              <div className="font-semibold">{entryToDelete.title}</div>
              <div className="text-sm">
                {formatTime(entryToDelete.startTime, locale)} - {entryToDelete.endTime ? formatTime(entryToDelete.endTime, locale) : t('timeLogs.inProgress')}
              </div>
              <div className="text-sm">
                {t('timeLogs.duration')}: {formatDuration(calculateDuration(entryToDelete.startTime, entryToDelete.endTime))}
              </div>
            </div>
          )}
          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={isDeleting}
              className="text-foreground"
            >
              {t('timeLogs.deleteDialog.cancel')}
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={isDeleting}
              data-testid="confirm-delete-button"
            >
              {isDeleting ? t('timeLogs.deleting') : t('timeLogs.deleteDialog.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PortalLayout>
  )
}
