import type { TimeLogEntry } from "../components/time-logs/types";

export interface DailyGoalBreak {
  from: string;
  to: string;
}

export interface DailyGoalProgress {
  totalMs: number;
  progressPercent: number;
  estimatedCompletionTime: Date;
}

const MINUTE_MS = 60 * 1000;

export function calculateDailyGoalProgress(
  entries: TimeLogEntry[],
  goalMinutes: number,
  typicalBreaks: DailyGoalBreak[],
  now: Date
): DailyGoalProgress | null {
  if (goalMinutes <= 0) return null;

  const totalMs = entries.reduce((sum, entry) => sum + calculateEntryDuration(entry, now), 0);
  const goalMs = goalMinutes * MINUTE_MS;
  const remainingMs = Math.max(0, goalMs - totalMs);

  return {
    totalMs,
    progressPercent: Math.min(100, Math.floor((totalMs / goalMs) * 100)),
    estimatedCompletionTime: addWorkingTime(now, remainingMs, typicalBreaks),
  };
}

function calculateEntryDuration(entry: TimeLogEntry, now: Date): number {
  const start = new Date(entry.startTime).getTime();
  const end = entry.endTime ? new Date(entry.endTime).getTime() : now.getTime();
  return Math.max(0, end - start);
}

function addWorkingTime(start: Date, durationMs: number, typicalBreaks: DailyGoalBreak[]): Date {
  let cursor = new Date(start);
  let remainingMs = durationMs;

  while (remainingMs > 0) {
    const nextBreak = getNextBreak(cursor, typicalBreaks);

    if (!nextBreak) {
      return new Date(cursor.getTime() + remainingMs);
    }

    if (cursor < nextBreak.from) {
      const workUntilBreakMs = nextBreak.from.getTime() - cursor.getTime();
      if (remainingMs <= workUntilBreakMs) {
        return new Date(cursor.getTime() + remainingMs);
      }
      remainingMs -= workUntilBreakMs;
      cursor = new Date(nextBreak.to);
    } else if (cursor < nextBreak.to) {
      cursor = new Date(nextBreak.to);
    }
  }

  return cursor;
}

function getNextBreak(cursor: Date, typicalBreaks: DailyGoalBreak[]): { from: Date; to: Date } | null {
  return (
    typicalBreaks
      .map((typicalBreak) => toBreakPeriod(cursor, typicalBreak))
      .filter((period): period is { from: Date; to: Date } => period != null && period.to > cursor)
      .sort((a, b) => a.from.getTime() - b.from.getTime())[0] ?? null
  );
}

function toBreakPeriod(day: Date, typicalBreak: DailyGoalBreak): { from: Date; to: Date } | null {
  const from = parseLocalTime(day, typicalBreak.from);
  const to = parseLocalTime(day, typicalBreak.to);
  if (!from || !to || to <= from) return null;
  return { from, to };
}

function parseLocalTime(day: Date, value: string): Date | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(value);
  if (!match) return null;

  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (hours > 23 || minutes > 59) return null;

  const date = new Date(day);
  date.setHours(hours, minutes, 0, 0);
  return date;
}
