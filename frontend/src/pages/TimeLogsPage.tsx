import { useTranslation } from "react-i18next";
import { PortalLayout } from "@/components/layout/PortalLayout";
import { FormMessage } from "@/components/ui/form-message";
import { CurrentEntryPanel } from "@/components/time-logs/CurrentEntryPanel";
import { WeekNavigation } from "@/components/time-logs/WeekNavigation";
import { DayGroup } from "@/components/time-logs/DayGroup";
import { useTimeLogs } from "@/hooks/useTimeLogs";

export function TimeLogsPage() {
  const { t, i18n } = useTranslation();
  const {
    activeEntry,
    activeDuration,
    dayGroups,
    weeklyTotal,
    isInitializing,
    error,
    userLocale,
    startOfWeek,
    handlePreviousWeek,
    handleNextWeek,
    getWeekRangeDisplay,
    reloadData,
  } = useTimeLogs();

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
            activeDuration={activeDuration}
            locale={locale}
            startOfWeek={startOfWeek}
            isEditingStoppedEntry={false}
            onDataChange={reloadData}
          />

          {/* Week Navigation */}
          <WeekNavigation
            weekRange={getWeekRangeDisplay()}
            weeklyTotal={weeklyTotal}
            locale={locale}
            onPreviousWeek={handlePreviousWeek}
            onNextWeek={handleNextWeek}
          />

          {/* Time Entries List */}
          {isInitializing ? (
            <div className="text-center py-8 text-foreground">{t("common.loading")}</div>
          ) : dayGroups.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground" data-testid="no-entries">
              {t("timeLogs.noEntries")}
            </div>
          ) : (
            <div className="space-y-6">
              {dayGroups.map((group) => (
                <DayGroup
                  key={group.date}
                  group={group}
                  locale={locale}
                  startOfWeek={startOfWeek}
                  onDataChange={reloadData}
                />
              ))}
            </div>
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
