import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { apiGet, apiPost, apiPut, apiDelete } from "@/lib/api";
import { getWeekStart, formatISODate, calculateDuration } from "@/lib/time-utils";
import { formatDate } from "@/lib/date-format";
import { useDocumentTitle } from "./useDocumentTitle";
import type { TimeEntry, TimeLogEntry, DayGroup } from "../components/time-logs/types";

/**
 * Hook for managing time log entries and related state.
 */
export function useTimeLogs() {
  const { t, i18n } = useTranslation();
  const [activeEntry, setActiveEntry] = useState<TimeEntry | null>(null);
  const [activeDuration, setActiveDuration] = useState<number>(0);
  const [weekStart, setWeekStart] = useState<Date>(getWeekStart(new Date()));
  const [entries, setEntries] = useState<TimeEntry[]>([]);
  const [dayGroups, setDayGroups] = useState<DayGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [isStarting, setIsStarting] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [entryToDelete, setEntryToDelete] = useState<TimeEntry | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [userLocale, setUserLocale] = useState<string | null>(null);
  const [editingEntryId, setEditingEntryId] = useState<number | null>(null);
  const [isEditingActive, setIsEditingActive] = useState(false);

  // Update browser tab title based on active entry
  useDocumentTitle(activeEntry?.title || null);

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

  // Group entries by day and handle entries spanning midnight
  function groupEntriesByDay(entries: TimeLogEntry[], locale: string): DayGroup[] {
    const groups: { [key: string]: TimeLogEntry[] } = {};

    entries.forEach((entry) => {
      const startDate = new Date(entry.startTime);
      const endDate = entry.endTime ? new Date(entry.endTime) : new Date();

      const startDay = formatISODate(startDate);
      const endDay = formatISODate(endDate);

      if (startDay === endDay) {
        if (!groups[startDay]) groups[startDay] = [];
        groups[startDay].push(entry);
      } else {
        // Entry spans midnight - split it
        const startDayEnd = new Date(startDate);
        startDayEnd.setHours(23, 59, 59, 999);

        if (!groups[startDay]) groups[startDay] = [];
        groups[startDay].push({
          ...entry,
          endTime: startDayEnd.toISOString(),
        });

        const endDayStart = new Date(endDate);
        endDayStart.setHours(0, 0, 0, 0);

        if (!groups[endDay]) groups[endDay] = [];
        groups[endDay].push({
          ...entry,
          startTime: endDayStart.toISOString(),
        });
      }
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

  // Load time entries for the current week
  async function loadTimeEntries() {
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
        `/api/time-log-entries?startTime=${encodeURIComponent(startTimeStr)}&endTime=${encodeURIComponent(endTimeStr)}`
      );

      setEntries(response.entries || []);
    } catch (err: any) {
      setError(err.message || t("common.error"));
    } finally {
      setLoading(false);
    }
  }

  // Load active entry
  async function loadActiveEntry() {
    try {
      const response = await apiGet<{ entry: TimeLogEntry | null }>("/api/time-log-entries/active");
      setActiveEntry(response.entry);
    } catch (err: any) {
      console.error("Failed to load active entry:", err);
    }
  }

  // Start a new time entry
  async function handleStart(title: string, tags: string[] = []) {
    try {
      setIsStarting(true);
      setError(null);

      const entry = await apiPost<TimeEntry>("/api/time-log-entries", { title, tags });

      setActiveEntry(entry);
      setSuccess(t("timeLogs.success.started"));
      await loadTimeEntries();
    } catch (err: any) {
      const errorCode = (err as any).errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || t("common.error"));
      }
      throw err;
    } finally {
      setIsStarting(false);
    }
  }

  // Stop the active time entry
  async function handleStop() {
    if (!activeEntry) return;

    try {
      setIsStopping(true);
      setError(null);

      await apiPut(`/api/time-log-entries/${activeEntry.id}/stop`, {});

      setActiveEntry(null);
      setActiveDuration(0);
      await loadTimeEntries();
    } catch (err: any) {
      const errorCode = (err as any).errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || t("common.error"));
      }
    } finally {
      setIsStopping(false);
    }
  }

  // Continue with an existing entry's title
  async function handleContinue(entry: TimeEntry) {
    try {
      setIsStarting(true);
      setError(null);

      const newEntry = await apiPost<TimeEntry>("/api/time-log-entries", {
        title: entry.title,
        stopActiveEntry: true,
      });

      setActiveEntry(newEntry);
      await loadTimeEntries();
    } catch (err: any) {
      const errorCode = (err as any).errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || t("common.error"));
      }
    } finally {
      setIsStarting(false);
    }
  }

  // Open delete confirmation dialog
  function handleDeleteClick(entry: TimeEntry) {
    setEntryToDelete(entry);
    setDeleteDialogOpen(true);
  }

  // Delete a time entry
  async function handleDelete() {
    if (!entryToDelete) return;

    try {
      setIsDeleting(true);
      setError(null);

      await apiDelete(`/api/time-log-entries/${entryToDelete.id}`);

      setIsDeleting(false);
      setDeleteDialogOpen(false);
      setEntryToDelete(null);
      await loadTimeEntries();

      if (activeEntry && activeEntry.id === entryToDelete.id) {
        setActiveEntry(null);
      }
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
  }

  // Save edited entry
  async function handleSaveEdit(title: string, startTimeISO: string) {
    if (!activeEntry) return;

    try {
      setIsSaving(true);
      setError(null);

      const updatedEntry = await apiPut<TimeEntry>(`/api/time-log-entries/${activeEntry.id}`, {
        title,
        startTime: startTimeISO,
      });

      setActiveEntry(updatedEntry);
      setIsEditingActive(false);
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
  }

  // Start editing active entry
  function handleEditActiveEntry() {
    setIsEditingActive(true);
    setEditingEntryId(null);
  }

  // Start editing a stopped entry
  function handleEditEntry(entry: TimeEntry) {
    setEditingEntryId(entry.id);
    setIsEditingActive(false);
  }

  // Cancel editing stopped entry
  function handleCancelEditEntry() {
    setEditingEntryId(null);
  }

  // Save edited stopped entry
  async function handleSaveStoppedEntry(entry: TimeEntry, title: string, startTimeISO: string, endTimeISO: string) {
    try {
      setIsSaving(true);
      setError(null);

      await apiPut<TimeEntry>(`/api/time-log-entries/${entry.id}`, {
        title,
        startTime: startTimeISO,
        endTime: endTimeISO,
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
  }

  // Navigate to previous week
  function handlePreviousWeek() {
    const newWeekStart = new Date(weekStart);
    newWeekStart.setDate(newWeekStart.getDate() - 7);
    setWeekStart(newWeekStart);
  }

  // Navigate to next week
  function handleNextWeek() {
    const newWeekStart = new Date(weekStart);
    newWeekStart.setDate(newWeekStart.getDate() + 7);
    setWeekStart(newWeekStart);
  }

  // Get week range display
  function getWeekRangeDisplay(): string {
    const locale = userLocale || i18n.language || "en";
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekEnd.getDate() + 6);

    const startStr = weekStart.toLocaleDateString(locale, { month: "short", day: "numeric" });
    const endStr = weekEnd.toLocaleDateString(locale, { month: "short", day: "numeric" });

    return `${startStr} - ${endStr}`;
  }

  // Load data on mount and when week changes
  useEffect(() => {
    loadTimeEntries();
    loadActiveEntry();
  }, [weekStart]);

  // Load user's locale preference on mount
  useEffect(() => {
    async function loadUserLocale() {
      try {
        const profile = await apiGet<{ locale: string }>("/api/users/profile");
        setUserLocale(profile.locale);
      } catch (err) {
        console.error("Failed to load user locale:", err);
      }
    }
    loadUserLocale();
  }, []);

  // Check for date changes periodically (every minute) to auto-update week
  useEffect(() => {
    const interval = setInterval(() => {
      const currentWeekStart = getWeekStart(new Date());
      if (currentWeekStart.getTime() !== weekStart.getTime()) {
        setWeekStart(currentWeekStart);
      }
    }, 60000);

    return () => clearInterval(interval);
  }, [weekStart]);

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

  // Recalculate day groups when entries change or when there's an active entry
  useEffect(() => {
    const locale = userLocale || i18n.language || "en";
    const groups = groupEntriesByDay(entries, locale);
    setDayGroups(groups);

    if (!activeEntry) return;

    const interval = setInterval(() => {
      const updatedGroups = groupEntriesByDay(entries, locale);
      setDayGroups(updatedGroups);
    }, 1000);

    return () => clearInterval(interval);
  }, [entries, activeEntry, i18n.language, userLocale]);

  return {
    activeEntry,
    activeDuration,
    weekStart,
    dayGroups,
    loading,
    isStarting,
    isStopping,
    error,
    success,
    deleteDialogOpen,
    entryToDelete,
    isDeleting,
    isSaving,
    userLocale,
    editingEntryId,
    isEditingActive,
    handleStart,
    handleStop,
    handleContinue,
    handleDeleteClick,
    handleDelete,
    handleSaveEdit,
    handleEditActiveEntry,
    handleEditEntry,
    handleCancelEditEntry,
    handleSaveStoppedEntry,
    handlePreviousWeek,
    handleNextWeek,
    getWeekRangeDisplay,
    setDeleteDialogOpen,
  };
}
