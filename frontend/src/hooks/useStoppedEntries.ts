import { useState, useEffect, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { apiGet, apiPost, apiPut, apiDelete } from "@/lib/api";
import { formatISODate, calculateDuration } from "@/lib/time-utils";
import { formatDate } from "@/lib/date-format";
import { useTimeLogEntryEvents } from "./useTimeLogEntryEvents";
import type { TimeEntry, TimeLogEntry, DayGroup } from "../components/time-logs/types";

/**
 * Hook for managing stopped time log entries (the list view).
 * Self-contained with its own state and API calls.
 */
export function useStoppedEntries(weekStart: Date, userLocale: string | null) {
  const { t, i18n } = useTranslation();
  const [entries, setEntries] = useState<TimeEntry[]>([]);
  const [dayGroups, setDayGroups] = useState<DayGroup[]>([]);
  const [weeklyTotal, setWeeklyTotal] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [editingEntryId, setEditingEntryId] = useState<number | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [entryToDelete, setEntryToDelete] = useState<TimeEntry | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Get day title (Today, Yesterday, or day of week + date)
  function getDayTitle(dateStr: string, locale: string): string {
    const [year, month, day] = dateStr.split("-").map(Number);
    const date = new Date(year, month - 1, day);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    today.setHours(0, 0, 0, 0);
    yesterday.setHours(0, 0, 0, 0);
    date.setHours(0, 0, 0, 0);

    if (date.getTime() === today.getTime()) {
      return t("timeLogs.today");
    } else if (date.getTime() === yesterday.getTime()) {
      return t("timeLogs.yesterday");
    } else {
      const dayName = date.toLocaleDateString(locale, { weekday: "long" });
      const formattedDate = formatDate(dateStr, locale);
      return `${dayName}, ${formattedDate}`;
    }
  }

  // Group entries by day
  function groupEntriesByDay(entries: TimeLogEntry[], locale: string): DayGroup[] {
    const groups: { [key: string]: TimeLogEntry[] } = {};

    entries.forEach((entry) => {
      const startDate = new Date(entry.startTime);
      const startDay = formatISODate(startDate);

      if (!groups[startDay]) groups[startDay] = [];
      groups[startDay].push(entry);
    });

    return Object.entries(groups)
      .map(([date, entries]) => {
        const totalDuration = entries.reduce(
          (sum, entry) => sum + calculateDuration(entry.startTime, entry.endTime),
          0
        );

        return {
          date,
          displayTitle: getDayTitle(date, locale),
          entries: entries.sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime()),
          totalDuration,
        };
      })
      .sort((a, b) => {
        const [yearA, monthA, dayA] = a.date.split("-").map(Number);
        const [yearB, monthB, dayB] = b.date.split("-").map(Number);
        const dateA = new Date(yearA, monthA - 1, dayA);
        const dateB = new Date(yearB, monthB - 1, dayB);
        return dateB.getTime() - dateA.getTime();
      });
  }

  // Calculate weekly total
  function calculateWeeklyTotal(entries: TimeLogEntry[]): number {
    return entries.reduce((sum, entry) => sum + calculateDuration(entry.startTime, entry.endTime), 0);
  }

  // Load time entries for the current week
  const loadTimeEntries = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const weekStartTime = new Date(weekStart);
      weekStartTime.setHours(0, 0, 0, 0);
      const weekEndTime = new Date(weekStart);
      weekEndTime.setDate(weekEndTime.getDate() + 7);
      weekEndTime.setHours(0, 0, 0, 0);

      const startTimeStr = weekStartTime.toISOString();
      const endTimeStr = weekEndTime.toISOString();

      const response = await apiGet<{ entries: TimeLogEntry[] }>(
        `/api-ui/time-log-entries?startTime=${encodeURIComponent(startTimeStr)}&endTime=${encodeURIComponent(endTimeStr)}`
      );

      setEntries(response.entries || []);
    } catch (err: any) {
      setError(err.message || t("common.error"));
    } finally {
      setLoading(false);
    }
  }, [weekStart, t]);

  // Handle SSE events for time log entry changes
  const handleTimeLogEvent = useCallback(
    async (event: { type: "ENTRY_STARTED" | "ENTRY_STOPPED"; entryId: number; title: string }) => {
      console.log("[useStoppedEntries] Received SSE event:", event);
      // Reload entries when an entry is stopped (it becomes visible in the list)
      if (event.type === "ENTRY_STOPPED") {
        await loadTimeEntries();
      }
    },
    [loadTimeEntries]
  );

  // Subscribe to SSE events
  useTimeLogEntryEvents(handleTimeLogEvent, true);

  // Continue with an existing entry's title
  const handleContinue = useCallback(
    async (entry: TimeEntry) => {
      try {
        setError(null);

        await apiPost<TimeEntry>("/api-ui/time-log-entries", {
          title: entry.title,
          tags: entry.tags,
          stopActiveEntry: true,
        });

        // Reload to reflect the changes
        await loadTimeEntries();
      } catch (err: any) {
        const errorCode = (err as any).errorCode;
        if (errorCode) {
          setError(t(`errorCodes.${errorCode}`));
        } else {
          setError(err.message || t("common.error"));
        }
      }
    },
    [t, loadTimeEntries]
  );

  // Open delete confirmation dialog
  const handleDeleteClick = useCallback((entry: TimeEntry) => {
    setEntryToDelete(entry);
    setDeleteDialogOpen(true);
  }, []);

  // Delete a time entry
  const handleDelete = useCallback(async () => {
    if (!entryToDelete) return;

    try {
      setIsDeleting(true);
      setError(null);

      await apiDelete(`/api-ui/time-log-entries/${entryToDelete.id}`);

      setIsDeleting(false);
      setDeleteDialogOpen(false);
      setEntryToDelete(null);
      await loadTimeEntries();
    } catch (err: any) {
      const errorCode = (err as any).errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || t("common.error"));
      }
    } finally {
      setIsDeleting(false);
    }
  }, [entryToDelete, t, loadTimeEntries]);

  // Start editing a stopped entry
  const handleEditEntry = useCallback((entry: TimeEntry) => {
    setEditingEntryId(entry.id);
  }, []);

  // Cancel editing stopped entry
  const handleCancelEditEntry = useCallback(() => {
    setEditingEntryId(null);
  }, []);

  // Save edited stopped entry
  const handleSaveStoppedEntry = useCallback(
    async (entry: TimeEntry, title: string, startTimeISO: string, endTimeISO: string, tags: string[]) => {
      try {
        setIsSaving(true);
        setError(null);

        await apiPut<TimeEntry>(`/api-ui/time-log-entries/${entry.id}`, {
          title,
          startTime: startTimeISO,
          endTime: endTimeISO,
          tags,
        });

        setEditingEntryId(null);
        await loadTimeEntries();
      } catch (err: any) {
        const errorCode = err.errorCode;
        if (errorCode) {
          setError(t(`errorCodes.${errorCode}`));
        } else {
          setError(err.message || t("common.error"));
        }
        throw err;
      } finally {
        setIsSaving(false);
      }
    },
    [t, loadTimeEntries]
  );

  // Save edited grouped entry
  const handleSaveGroupEdit = useCallback(
    async (entryIds: number[], title: string, tags: string[]) => {
      try {
        setIsSaving(true);
        setError(null);

        await apiPut(`/api-ui/time-log-entries/bulk-update`, {
          entryIds,
          title,
          tags,
        });

        await loadTimeEntries();
      } catch (err: any) {
        const errorCode = err.errorCode;
        if (errorCode) {
          setError(t(`errorCodes.${errorCode}`));
        } else {
          setError(err.message || t("common.error"));
        }
        throw err;
      } finally {
        setIsSaving(false);
      }
    },
    [t, loadTimeEntries]
  );

  // Load entries when week changes
  useEffect(() => {
    loadTimeEntries();
  }, [loadTimeEntries]);

  // Recalculate day groups when entries change
  useEffect(() => {
    const locale = userLocale || i18n.language || "en";
    const groups = groupEntriesByDay(entries, locale);
    setDayGroups(groups);
    setWeeklyTotal(calculateWeeklyTotal(entries));
  }, [entries, userLocale, i18n.language]);

  return {
    dayGroups,
    weeklyTotal,
    loading,
    editingEntryId,
    isSaving,
    deleteDialogOpen,
    entryToDelete,
    isDeleting,
    error,
    handleContinue,
    handleDeleteClick,
    handleDelete,
    handleEditEntry,
    handleCancelEditEntry,
    handleSaveStoppedEntry,
    handleSaveGroupEdit,
    setDeleteDialogOpen,
  };
}
