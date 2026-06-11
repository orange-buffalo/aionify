import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { calculateDailyGoalProgress } from "@/lib/daily-goal-progress";
import type { DailyGoalSettings, TimeLogEntry } from "./types";

interface DailyGoalProgressBarProps {
  entries: TimeLogEntry[];
  dailyGoal: DailyGoalSettings;
  locale: string;
}

export function DailyGoalProgressBar({ entries, dailyGoal, locale }: DailyGoalProgressBarProps) {
  const { t } = useTranslation();
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    setNow(new Date());
    const interval = setInterval(() => setNow(new Date()), 60 * 1000);
    return () => clearInterval(interval);
  }, [entries, dailyGoal]);

  if (!dailyGoal.enabled) return null;

  const progress = calculateDailyGoalProgress(entries, dailyGoal.goalMinutes, dailyGoal.typicalBreaks, now);
  if (!progress) return null;

  const estimatedCompletionTime = new Intl.DateTimeFormat(locale, {
    hour: "2-digit",
    minute: "2-digit",
  }).format(progress.estimatedCompletionTime);

  return (
    <TooltipProvider delayDuration={0}>
      <Tooltip>
        <TooltipTrigger asChild>
          <div
            className="h-2 w-[clamp(50px,35vw,300px)] rounded-full bg-accent"
            data-testid="daily-goal-progress"
            role="progressbar"
            tabIndex={0}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={progress.progressPercent}
          >
            <div
              className="h-full rounded-full bg-accent-foreground transition-all"
              style={{ width: `${progress.progressPercent}%` }}
              data-testid="daily-goal-progress-fill"
            />
          </div>
        </TooltipTrigger>
        <TooltipContent data-testid="daily-goal-progress-tooltip">
          <div className="space-y-1">
            <div>{t("timeLogs.dailyGoal.achievement", { percent: progress.progressPercent })}</div>
            {progress.progressPercent >= 100 ? (
              <div>{t("timeLogs.dailyGoal.met")}</div>
            ) : (
              <div>{t("timeLogs.dailyGoal.estimatedCompletion", { time: estimatedCompletionTime })}</div>
            )}
          </div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
