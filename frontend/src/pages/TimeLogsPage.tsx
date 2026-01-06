import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { PortalLayout } from "@/components/layout/PortalLayout";
import { CurrentEntryPanelContainer } from "@/components/time-logs/CurrentEntryPanelContainer";
import { WeekNavigation } from "@/components/time-logs/WeekNavigation";
import { StoppedEntriesList } from "@/components/time-logs/StoppedEntriesList";
import { apiGet } from "@/lib/api";
import { getWeekStart, weekDayToNumber } from "@/lib/time-utils";

/**
 * Time Logs Page - Refactored for optimal rendering performance.
 *
 * This component only manages shared state (user settings, week navigation).
 * Child components manage their own state independently:
 * - CurrentEntryPanelContainer: manages active entry and its operations
 * - StoppedEntriesList: manages stopped entries list and its operations
 *
 * This ensures that:
 * - Stopping active entry only re-renders CurrentEntryPanelContainer
 * - Editing stopped entry only re-renders StoppedEntriesList
 * - Scroll position is preserved during unrelated operations
 */
export function TimeLogsPage() {
  const { t, i18n } = useTranslation();
  const [userLocale, setUserLocale] = useState<string | null>(null);
  const [startOfWeek, setStartOfWeek] = useState<number>(1); // Default to Monday
  const [weekStart, setWeekStart] = useState<Date>(getWeekStart(new Date()));
  const [editingEntryId, setEditingEntryId] = useState<number | null>(null);

  // Load user's locale and settings on mount
  useEffect(() => {
    async function loadUserProfile() {
      const profile = await apiGet<{ locale: string; startOfWeek: string }>("/api-ui/users/profile");
      setUserLocale(profile.locale);
      const startOfWeekNum = weekDayToNumber(profile.startOfWeek);
      setStartOfWeek(startOfWeekNum);
      // Update week start with the user's preference
      setWeekStart(getWeekStart(new Date(), startOfWeekNum));
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

  // Navigate to previous week
  const handlePreviousWeek = () => {
    const newWeekStart = new Date(weekStart);
    newWeekStart.setDate(newWeekStart.getDate() - 7);
    setWeekStart(newWeekStart);
  };

  // Navigate to next week
  const handleNextWeek = () => {
    const newWeekStart = new Date(weekStart);
    newWeekStart.setDate(newWeekStart.getDate() + 7);
    setWeekStart(newWeekStart);
  };

  // Get week range display
  const getWeekRangeDisplay = (): string => {
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekEnd.getDate() + 6);

    const startStr = weekStart.toLocaleDateString(locale, { month: "short", day: "numeric" });
    const endStr = weekEnd.toLocaleDateString(locale, { month: "short", day: "numeric" });

    return `${startStr} - ${endStr}`;
  };

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

          {/* Current Entry Panel - Self-contained */}
          <CurrentEntryPanelContainer
            locale={locale}
            startOfWeek={startOfWeek}
            isEditingStoppedEntry={editingEntryId !== null}
          />

          {/* Stopped Entries List - Self-contained, includes week navigation */}
          <StoppedEntriesList
            weekStart={weekStart}
            locale={locale}
            startOfWeek={startOfWeek}
            userLocale={userLocale}
            weekRange={getWeekRangeDisplay()}
            onPreviousWeek={handlePreviousWeek}
            onNextWeek={handleNextWeek}
          />

          {/* Timezone Hint */}
          <div className="mt-8 text-right text-xs text-muted-foreground">
            {t("timeLogs.timezoneHint", { timezone: timeZone })}
          </div>
        </div>
      </div>
    </PortalLayout>
  );
}
