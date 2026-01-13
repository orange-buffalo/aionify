import { useState, useEffect, useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { PortalLayout } from "@/components/layout/PortalLayout";
import { FormMessage } from "@/components/ui/form-message";
import { CurrentEntryPanel } from "@/components/time-logs/CurrentEntryPanel";
import { WeekNavigation } from "@/components/time-logs/WeekNavigation";
import { DayGroups } from "@/components/time-logs/DayGroups";
import { apiGet } from "@/lib/api";
import { weekDayToNumber } from "@/lib/time-utils";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useTimeLogEntryEvents } from "@/hooks/useTimeLogEntryEvents";
import type { TimeEntry, TimeLogEntry } from "@/components/time-logs/types";

export function TimeLogsPage() {
  const { t, i18n } = useTranslation();
  const [activeEntry, setActiveEntry] = useState<TimeEntry | null>(null);
  const [dateRange, setDateRange] = useState<{ from: Date; to: Date } | null>(null);
  const [entries, setEntries] = useState<TimeEntry[]>([]);
  const [isInitializing, setIsInitializing] = useState(true);
  const [hasDataLoaded, setHasDataLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [userLocale, setUserLocale] = useState<string | null>(null);
  const [startOfWeek, setStartOfWeek] = useState<number>(1); // Default to Monday
  const abortControllerRef = useRef<AbortController | null>(null);

  // Update browser tab title based on active entry
  useDocumentTitle(activeEntry?.title || null);

  // Load both time entries and active entry for the current date range
  async function loadData() {
    if (!dateRange) return;

    // Cancel any pending reload request
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // Create a new AbortController for this request
    const abortController = new AbortController();
    abortControllerRef.current = abortController;

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

      // Execute both API calls in parallel under the same error handling
      const [entriesResponse, activeEntryResponse] = await Promise.all([
        apiGet<{ entries: TimeLogEntry[] }>(
          `/api-ui/time-log-entries?startTime=${encodeURIComponent(startTimeStr)}&endTime=${encodeURIComponent(endTimeStr)}`,
          abortController.signal
        ),
        apiGet<{ entry?: TimeLogEntry | null }>("/api-ui/time-log-entries/active", abortController.signal),
      ]);

      // Only update state if this request wasn't cancelled
      if (!abortController.signal.aborted) {
        setEntries(entriesResponse.entries || []);
        setActiveEntry(activeEntryResponse.entry ?? null);
        setHasDataLoaded(true);
        abortControllerRef.current = null;
      }
    } catch (err: any) {
      // Ignore abort errors - they're expected when a new request cancels the old one
      if (err.name === "AbortError") {
        return;
      }

      const errorCode = err.errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || t("common.error"));
      }
    } finally {
      if (isInitialLoad) {
        setIsInitializing(false);
      }
    }
  }

  // Handle SSE events for time log entry changes
  const handleTimeLogEvent = useCallback(
    async (event: { type: "ENTRY_STARTED" | "ENTRY_STOPPED"; entryId: number; title: string }) => {
      console.log("[TimeLogsPage] Received SSE event:", event);

      // Reload both active entry and time entries to get the latest state
      await loadData();
    },
    [dateRange] // Depend on dateRange so we reload the correct range
  );

  // Subscribe to SSE events for real-time updates
  useTimeLogEntryEvents(handleTimeLogEvent, true);

  // Reload data (this is called by components after they make changes)
  async function reloadData() {
    await loadData();
  }

  // Update the displayed data time range
  const updateDisplayedDataTimeRange = useCallback((from: Date, to: Date) => {
    setDateRange({ from, to });
    // Reset the loaded flag when range changes to show loading state
    setHasDataLoaded(false);
  }, []);

  // Load data when date range changes
  useEffect(() => {
    if (dateRange) {
      loadData();
    }
  }, [dateRange]);

  // Load user's locale and start of week preference on mount
  useEffect(() => {
    async function loadUserProfile() {
      const profile = await apiGet<{ locale: string; startOfWeek: string }>("/api-ui/users/profile");
      setUserLocale(profile.locale);
      const startOfWeekNum = weekDayToNumber(profile.startOfWeek);
      setStartOfWeek(startOfWeekNum);
    }
    loadUserProfile();
  }, []);

  // Don't render page elements until we have the user locale
  if (!userLocale) {
    return (
      <PortalLayout testId="time-logs-page">
        <div className="p-8">
          <div className="max-w-6xl mx-auto">
            <div className="text-center py-8 text-foreground">{t("common.loading")}</div>
          </div>
        </div>
      </PortalLayout>
    );
  }

  const locale = userLocale || i18n.language || "en";
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <PortalLayout testId="time-logs-page">
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground mb-2" data-testid="time-logs-title">
              {t("timeLogs.title")}
            </h1>
            <div className="text-muted-foreground">{t("timeLogs.subtitle")}</div>
          </div>

          {/* Error Message */}
          {error && <FormMessage type="error" message={error} testId="time-logs-error" />}

          {/* Current Entry Panel */}
          <CurrentEntryPanel
            activeEntry={activeEntry}
            locale={locale}
            startOfWeek={startOfWeek}
            isEditingStoppedEntry={false}
            onDataChange={reloadData}
          />

          {/* Week Navigation */}
          <WeekNavigation
            entries={entries}
            activeEntry={activeEntry}
            locale={locale}
            startOfWeek={startOfWeek}
            onTimeRangeChange={updateDisplayedDataTimeRange}
          />

          {/* Time Entries List */}
          {isInitializing ? (
            <div className="text-center py-8 text-foreground">{t("common.loading")}</div>
          ) : (
            <DayGroups
              entries={entries}
              activeEntry={activeEntry}
              locale={locale}
              startOfWeek={startOfWeek}
              onDataChange={reloadData}
            />
          )}

          {/* Timezone Hint */}
          <div className="mt-8 text-right text-xs text-muted-foreground">
            {t("timeLogs.timezoneHint", { timezone: timeZone })}
          </div>
        </div>
      </div>
    </PortalLayout>
  );
}
