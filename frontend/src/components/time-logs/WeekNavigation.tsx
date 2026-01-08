import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { formatDuration, getWeekStart } from "@/lib/time-utils";
import type { DayGroup } from "./types";

interface WeekNavigationProps {
  dayGroups: DayGroup[];
  locale: string;
  startOfWeek: number;
  onTimeRangeChange: (from: Date, to: Date) => void;
}

export function WeekNavigation({ dayGroups, locale, startOfWeek, onTimeRangeChange }: WeekNavigationProps) {
  const { t } = useTranslation();
  const [weekStart, setWeekStart] = useState<Date | null>(null);

  // Initialize week start when startOfWeek is available
  useEffect(() => {
    if (startOfWeek !== undefined && !weekStart) {
      const initialWeekStart = getWeekStart(new Date(), startOfWeek);
      setWeekStart(initialWeekStart);
    }
  }, [startOfWeek]);

  // Notify parent when week changes
  useEffect(() => {
    if (weekStart) {
      const weekEnd = new Date(weekStart);
      weekEnd.setDate(weekEnd.getDate() + 7);
      onTimeRangeChange(weekStart, weekEnd);
    }
  }, [weekStart, onTimeRangeChange]);

  // Navigate to previous week
  function handlePreviousWeek() {
    if (!weekStart) return;
    const newWeekStart = new Date(weekStart);
    newWeekStart.setDate(newWeekStart.getDate() - 7);
    setWeekStart(newWeekStart);
  }

  // Navigate to next week
  function handleNextWeek() {
    if (!weekStart) return;
    const newWeekStart = new Date(weekStart);
    newWeekStart.setDate(newWeekStart.getDate() + 7);
    setWeekStart(newWeekStart);
  }

  // Get week range display
  function getWeekRangeDisplay(): string {
    if (!weekStart) return "";
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekEnd.getDate() + 6);

    const startStr = weekStart.toLocaleDateString(locale, { month: "short", day: "numeric" });
    const endStr = weekEnd.toLocaleDateString(locale, { month: "short", day: "numeric" });

    return `${startStr} - ${endStr}`;
  }

  // Calculate weekly total from day groups
  function calculateWeeklyTotal(): number {
    return dayGroups.reduce((sum, group) => sum + group.totalDuration, 0);
  }

  // Don't render until weekStart is initialized
  if (!weekStart) {
    return null;
  }

  const weekRange = getWeekRangeDisplay();
  const weeklyTotal = calculateWeeklyTotal();

  return (
    <Card className="border-none shadow-md mb-6">
      <CardContent className="flex items-center justify-between p-4">
        <Button
          variant="ghost"
          onClick={handlePreviousWeek}
          data-testid="previous-week-button"
          className="text-foreground"
        >
          <ChevronLeft className="h-4 w-4 mr-2" />
          {t("timeLogs.previousWeek")}
        </Button>
        <div className="flex flex-col items-center">
          <h2 className="text-xl font-semibold text-foreground" data-testid="week-range">
            {weekRange}
          </h2>
          <div className="text-sm text-muted-foreground mt-1" data-testid="weekly-total">
            {t("timeLogs.weeklyTotal")}: {formatDuration(weeklyTotal)}
          </div>
        </div>
        <Button variant="ghost" onClick={handleNextWeek} data-testid="next-week-button" className="text-foreground">
          {t("timeLogs.nextWeek")}
          <ChevronRight className="h-4 w-4 ml-2" />
        </Button>
      </CardContent>
    </Card>
  );
}
