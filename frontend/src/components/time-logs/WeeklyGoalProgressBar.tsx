import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { calculateWeeklyGoalProgress } from "@/lib/weekly-goal-progress";
import { getWeekStart } from "@/lib/time-utils";
import type { WeeklyGoalEstimate } from "@/lib/weekly-goal-progress";
import type { DailyGoalSettings, TimeLogEntry, WeekDay, WeeklyGoalSettings } from "./types";

interface WeeklyGoalProgressBarProps {
  entries: TimeLogEntry[];
  weeklyGoal: WeeklyGoalSettings;
  dailyGoal: DailyGoalSettings | null;
  weekStart: Date;
  startOfWeek: number;
}

export function WeeklyGoalProgressBar({
  entries,
  weeklyGoal,
  dailyGoal,
  weekStart,
  startOfWeek,
}: WeeklyGoalProgressBarProps) {
  const { t } = useTranslation();
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    setNow(new Date());
    const interval = setInterval(() => setNow(new Date()), 60 * 1000);
    return () => clearInterval(interval);
  }, [entries, weeklyGoal, dailyGoal, weekStart]);

  if (getWeekStart(now, startOfWeek).getTime() !== weekStart.getTime()) return null;

  const progress = calculateWeeklyGoalProgress({ entries, weeklyGoal, dailyGoal, weekStart, now });
  if (!progress) return null;

  return (
    <TooltipProvider delayDuration={0}>
      <Tooltip>
        <TooltipTrigger asChild>
          <div
            className="h-2 w-[clamp(90px,30vw,260px)] rounded-full bg-accent"
            data-testid="weekly-goal-progress"
            role="progressbar"
            tabIndex={0}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={progress.progressPercent}
          >
            <div
              className="h-full rounded-full bg-accent-foreground transition-all"
              style={{ width: `${progress.progressPercent}%` }}
              data-testid="weekly-goal-progress-fill"
            />
          </div>
        </TooltipTrigger>
        <TooltipContent data-testid="weekly-goal-progress-tooltip">
          <div className="space-y-1">
            {progress.progressPercent >= 100 && <div>{t("timeLogs.weeklyGoal.met")}</div>}
            {progress.progressPercent < 100 && (
              <>
                <div>{t("timeLogs.weeklyGoal.achievement", { percent: progress.progressPercent })}</div>
              </>
            )}
            {renderEstimate(progress.estimate, t)}
          </div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function renderEstimate(estimate: WeeklyGoalEstimate, t: ReturnType<typeof useTranslation>["t"]) {
  if (estimate.type === "none") return null;
  if (estimate.type === "actualOvertime") {
    return (
      <div>{t("timeLogs.weeklyGoal.actualOvertime", { duration: formatDurationForTooltip(estimate.overtimeMs) })}</div>
    );
  }
  if (estimate.type === "completion") {
    return <div>{t("timeLogs.weeklyGoal.estimatedCompletion", { day: dayLabel(estimate.day, t) })}</div>;
  }
  if (estimate.type === "overtime") {
    return (
      <div>
        {t("timeLogs.weeklyGoal.estimatedOvertime", {
          duration: formatDurationForTooltip(estimate.overtimeMs),
          day: dayLabel(estimate.day, t),
        })}
      </div>
    );
  }
  return <div>{t("timeLogs.weeklyGoal.insufficient", { duration: formatDurationForTooltip(estimate.missingMs) })}</div>;
}

function dayLabel(day: WeekDay, t: ReturnType<typeof useTranslation>["t"]): string {
  return t(`settings.preferences.weekDays.${day}`);
}

function formatDurationForTooltip(ms: number): string {
  const totalMinutes = Math.floor(ms / (60 * 1000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (minutes === 0) return `${hours}h`;
  if (hours === 0) return `${minutes}m`;
  return `${hours}h ${minutes}m`;
}
