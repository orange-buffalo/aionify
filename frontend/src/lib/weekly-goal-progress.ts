import { formatISODate, weekDayToNumber } from "./time-utils";
import type { DailyGoalSettings, TimeLogEntry, WeekDay, WeeklyGoalSettings } from "../components/time-logs/types";

export type WeeklyGoalEstimate =
  | { type: "none" }
  | { type: "actualOvertime"; overtimeMs: number }
  | { type: "completion"; day: WeekDay }
  | { type: "overtime"; day: WeekDay; overtimeMs: number }
  | { type: "insufficient"; missingMs: number };

export interface WeeklyGoalProgress {
  totalMs: number;
  progressPercent: number;
  estimate: WeeklyGoalEstimate;
}

export interface CalculateWeeklyGoalProgressArgs {
  entries: TimeLogEntry[];
  weeklyGoal: WeeklyGoalSettings;
  dailyGoal: DailyGoalSettings | null;
  weekStart: Date;
  now: Date;
}

const MINUTE_MS = 60 * 1000;

export function calculateWeeklyGoalProgress({
  entries,
  weeklyGoal,
  dailyGoal,
  weekStart,
  now,
}: CalculateWeeklyGoalProgressArgs): WeeklyGoalProgress | null {
  if (!weeklyGoal.enabled || weeklyGoal.goalMinutes <= 0) return null;

  const totalMs = entries.reduce((sum, entry) => sum + calculateEntryDuration(entry, now), 0);
  const weeklyGoalMs = weeklyGoal.goalMinutes * MINUTE_MS;
  const remainingWeeklyGoalMs = Math.max(0, weeklyGoalMs - totalMs);

  return {
    totalMs,
    progressPercent: Math.min(100, Math.floor((totalMs / weeklyGoalMs) * 100)),
    estimate: calculateEstimate(entries, weeklyGoal, dailyGoal, weekStart, now, remainingWeeklyGoalMs),
  };
}

function calculateEstimate(
  entries: TimeLogEntry[],
  weeklyGoal: WeeklyGoalSettings,
  dailyGoal: DailyGoalSettings | null,
  weekStart: Date,
  now: Date,
  remainingWeeklyGoalMs: number
): WeeklyGoalEstimate {
  const weeklyGoalMs = weeklyGoal.goalMinutes * MINUTE_MS;
  const totalMs = entries.reduce((sum, entry) => sum + calculateEntryDuration(entry, now), 0);
  if (totalMs > weeklyGoalMs) return { type: "actualOvertime", overtimeMs: totalMs - weeklyGoalMs };
  if (!dailyGoal?.enabled || dailyGoal.goalMinutes <= 0 || remainingWeeklyGoalMs <= 0) return { type: "none" };

  const remainingDays = getRemainingWorkingDays(weekStart, now, weeklyGoal.workingDays);
  if (remainingDays.length === 0) return { type: "insufficient", missingMs: remainingWeeklyGoalMs };

  const dailyGoalMs = dailyGoal.goalMinutes * MINUTE_MS;
  const remainingDailyGoalMs = remainingDays.reduce((sum, day) => {
    const dayTotalMs = entries
      .filter((entry) => formatISODate(new Date(entry.startTime)) === formatISODate(day.date))
      .reduce((entrySum, entry) => entrySum + calculateEntryDuration(entry, now), 0);
    return sum + Math.max(0, dailyGoalMs - dayTotalMs);
  }, 0);

  if (remainingDailyGoalMs === remainingWeeklyGoalMs) {
    return { type: "completion", day: remainingDays[remainingDays.length - 1].weekDay };
  }
  if (remainingDailyGoalMs > remainingWeeklyGoalMs) {
    return {
      type: "overtime",
      day: remainingDays[remainingDays.length - 1].weekDay,
      overtimeMs: remainingDailyGoalMs - remainingWeeklyGoalMs,
    };
  }
  return { type: "insufficient", missingMs: remainingWeeklyGoalMs - remainingDailyGoalMs };
}

function getRemainingWorkingDays(
  weekStart: Date,
  now: Date,
  workingDays: WeekDay[]
): { date: Date; weekDay: WeekDay }[] {
  const today = new Date(now);
  today.setHours(0, 0, 0, 0);

  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart);
    date.setDate(date.getDate() + index);
    date.setHours(0, 0, 0, 0);
    const weekDay = workingDays.find((day) => weekDayToNumber(day) === date.getDay());
    return weekDay ? { date, weekDay } : null;
  }).filter((day): day is { date: Date; weekDay: WeekDay } => day != null && day.date >= today);
}

function calculateEntryDuration(entry: TimeLogEntry, now: Date): number {
  const start = new Date(entry.startTime).getTime();
  const end = entry.endTime ? new Date(entry.endTime).getTime() : now.getTime();
  return Math.max(0, end - start);
}
