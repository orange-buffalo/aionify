import { useState, useEffect, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { apiGet } from "@/lib/api";
import { formatISODate, calculateDuration } from "@/lib/time-utils";
import { formatDate } from "@/lib/date-format";
import { useDocumentTitle } from "./useDocumentTitle";
import { useTimeLogEntryEvents } from "./useTimeLogEntryEvents";
import type { TimeEntry, TimeLogEntry, DayGroup } from "../components/time-logs/types";

/**
 * Hook for managing time log entries and related state.
 * This hook is responsible for fetching and managing time log data within a given time range.
 */
export function useTimeLogs() {
  const { t, i18n } = useTranslation();
  const [activeEntry, setActiveEntry] = useState<TimeEntry | null>(null);
  const [activeDuration, setActiveDuration] = useState<number>(0);
  const [dateRange, setDateRange] = useState<{ from: Date; to: Date } | null>(null);
  const [entries, setEntries] = useState<TimeEntry[]>([]);
  const [dayGroups, setDayGroups] = useState<DayGroup[]>([]);
  const [isInitializing, setIsInitializing] = useState(true);
  const [hasDataLoaded, setHasDataLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userLocale, setUserLocale] = useState<string | null>(null);

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

  // Group entries by day - always show on start day
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

  // Load time entries for the current date range
  async function loadTimeEntries() {
    if (!dateRange) return;

    // Only show loading state on initial load, not on reloads (e.g., after edits)
    const isInitialLoad = !hasDataLoaded;
    try {
      if (isInitialLoad) {
        setIsInitializing(true);
      }
      setError(null);

      const startTime = new Date(dateRange.from);
      startTime.setHours(0, 0, 0, 0);
      const endTime = new Date(dateRange.to);
      endTime.setHours(0, 0, 0, 0);

      const startTimeStr = startTime.toISOString();
      const endTimeStr = endTime.toISOString();

      const response = await apiGet<{ entries: TimeLogEntry[] }>(
        `/api-ui/time-log-entries?startTime=${encodeURIComponent(startTimeStr)}&endTime=${encodeURIComponent(endTimeStr)}`
      );

      setEntries(response.entries || []);
      setHasDataLoaded(true);
    } catch (err: any) {
      setError(err.message || t("common.error"));
    } finally {
      if (isInitialLoad) {
        setIsInitializing(false);
      }
    }
  }

  // Load active entry
  async function loadActiveEntry() {
    try {
      const response = await apiGet<{ entry?: TimeLogEntry | null }>("/api-ui/time-log-entries/active");
      setActiveEntry(response.entry ?? null);
    } catch (err: any) {
      const errorCode = err.errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || t("common.error"));
      }
    }
  }

  // Handle SSE events for time log entry changes
  const handleTimeLogEvent = useCallback(
    async (event: { type: "ENTRY_STARTED" | "ENTRY_STOPPED"; entryId: number; title: string }) => {
      console.log("[useTimeLogs] Received SSE event:", event);

      // Reload active entry to get the latest state
      await loadActiveEntry();

      // Reload time entries for the current date range to update the list
      await loadTimeEntries();
    },
    [dateRange] // Depend on dateRange so we reload the correct range
  );

  // Subscribe to SSE events for real-time updates
  useTimeLogEntryEvents(handleTimeLogEvent, true);

  // Reload both active entry and time entries data
  // This is called by components after they make changes (e.g., after deletion)
  async function reloadData() {
    await loadActiveEntry();
    await loadTimeEntries();
  }

  // Update the displayed data time range
  // This is the main callback that components use to change what data is displayed
  const updateDisplayedDataTimeRange = useCallback((from: Date, to: Date) => {
    setDateRange({ from, to });
    // Reset the loaded flag when range changes to show loading state
    setHasDataLoaded(false);
  }, []);

  // Load data when date range changes
  useEffect(() => {
    if (dateRange) {
      loadTimeEntries();
      loadActiveEntry();
    }
  }, [dateRange]);

  // Load user's locale on mount
  useEffect(() => {
    async function loadUserProfile() {
      const profile = await apiGet<{ locale: string; startOfWeek: string }>("/api-ui/users/profile");
      setUserLocale(profile.locale);
    }
    loadUserProfile();
  }, []);

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
    dayGroups,
    isInitializing,
    error,
    userLocale,
    updateDisplayedDataTimeRange,
    reloadData,
  };
}
