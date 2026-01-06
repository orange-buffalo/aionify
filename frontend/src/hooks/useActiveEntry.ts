import { useState, useEffect, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { calculateDuration } from "@/lib/time-utils";
import { useDocumentTitle } from "./useDocumentTitle";
import { useTimeLogEntryEvents } from "./useTimeLogEntryEvents";
import type { TimeEntry, TimeLogEntry } from "../components/time-logs/types";

/**
 * Hook for managing the active time log entry.
 * Self-contained with its own state and API calls.
 */
export function useActiveEntry() {
  const { t } = useTranslation();
  const [activeEntry, setActiveEntry] = useState<TimeEntry | null>(null);
  const [activeDuration, setActiveDuration] = useState<number>(0);
  const [isStarting, setIsStarting] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Update browser tab title based on active entry
  useDocumentTitle(activeEntry?.title || null);

  // Load active entry from API
  const loadActiveEntry = useCallback(async () => {
    try {
      const response = await apiGet<{ entry?: TimeLogEntry | null }>("/api-ui/time-log-entries/active");
      setActiveEntry(response.entry ?? null);
    } catch (err: any) {
      console.error("Failed to load active entry:", err);
    }
  }, []);

  // Handle SSE events for time log entry changes
  const handleTimeLogEvent = useCallback(
    async (event: { type: "ENTRY_STARTED" | "ENTRY_STOPPED"; entryId: number; title: string }) => {
      console.log("[useActiveEntry] Received SSE event:", event);
      // Reload active entry to get the latest state
      await loadActiveEntry();
    },
    [loadActiveEntry]
  );

  // Subscribe to SSE events for real-time updates
  useTimeLogEntryEvents(handleTimeLogEvent, true);

  // Start a new time entry
  const handleStart = useCallback(
    async (title: string, tags: string[] = []) => {
      try {
        setIsStarting(true);
        setError(null);

        const entry = await apiPost<TimeEntry>("/api-ui/time-log-entries", { title, tags });
        setActiveEntry(entry);
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
    },
    [t]
  );

  // Stop the active time entry
  const handleStop = useCallback(async () => {
    if (!activeEntry) return;

    try {
      setIsStopping(true);
      setError(null);

      await apiPut(`/api-ui/time-log-entries/${activeEntry.id}/stop`, {});
      setActiveEntry(null);
      setActiveDuration(0);
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
  }, [activeEntry, t]);

  // Save edited active entry
  const handleSaveEdit = useCallback(
    async (title: string, startTimeISO: string, tags: string[]) => {
      if (!activeEntry) return;

      try {
        setIsSaving(true);
        setError(null);

        const updatedEntry = await apiPut<TimeEntry>(`/api-ui/time-log-entries/${activeEntry.id}`, {
          title,
          startTime: startTimeISO,
          tags,
        });

        setActiveEntry(updatedEntry);
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
    [activeEntry, t]
  );

  // Load active entry on mount
  useEffect(() => {
    loadActiveEntry();
  }, [loadActiveEntry]);

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

  return {
    activeEntry,
    activeDuration,
    isStarting,
    isStopping,
    isSaving,
    error,
    handleStart,
    handleStop,
    handleSaveEdit,
  };
}
