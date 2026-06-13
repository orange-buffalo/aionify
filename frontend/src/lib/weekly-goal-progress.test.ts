import { describe, expect, test } from "bun:test";
import { calculateWeeklyGoalProgress } from "./weekly-goal-progress";
import type { DailyGoalSettings, TimeLogEntry, WeeklyGoalSettings } from "../components/time-logs/types";

const HOUR_MS = 60 * 60 * 1000;

const standardDailyGoal: DailyGoalSettings = { enabled: true, goalMinutes: 480, typicalBreaks: [] };
const standardWeeklyGoal: WeeklyGoalSettings = {
  enabled: true,
  goalMinutes: 2_400,
  workingDays: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
};

function entry(date: string, start: string, end: string | null, id = 1): TimeLogEntry {
  return {
    id,
    startTime: time(date, start).toISOString(),
    endTime: end ? time(date, end).toISOString() : null,
    title: "Task",
    ownerId: 1,
    tags: [],
  };
}

function entries(...items: Array<[string, string, string | null]>): TimeLogEntry[] {
  return items.map(([date, start, end], index) => entry(date, start, end, index + 1));
}

function time(date: string, value: string): Date {
  const [year, month, day] = date.split("-").map(Number);
  const [hours, minutes] = value.split(":").map(Number);
  return new Date(year, month - 1, day, hours, minutes, 0, 0);
}

function calculate(
  args: {
    entries?: TimeLogEntry[];
    weeklyGoal?: WeeklyGoalSettings;
    dailyGoal?: DailyGoalSettings | null;
    weekStart?: Date;
    now?: Date;
  } = {}
) {
  return calculateWeeklyGoalProgress({
    entries: args.entries ?? [],
    weeklyGoal: args.weeklyGoal ?? standardWeeklyGoal,
    dailyGoal: args.dailyGoal === undefined ? standardDailyGoal : args.dailyGoal,
    weekStart: args.weekStart ?? time("2024-03-11", "00:00"),
    now: args.now ?? time("2024-03-11", "09:00"),
  });
}

describe("calculateWeeklyGoalProgress", () => {
  test("returns null when weekly goal is disabled or zero", () => {
    expect(calculate({ weeklyGoal: { ...standardWeeklyGoal, enabled: false } })).toBeNull();
    expect(calculate({ weeklyGoal: { ...standardWeeklyGoal, goalMinutes: 0 } })).toBeNull();
  });

  test("calculates percentage without estimate when daily goal is disabled", () => {
    const result = calculate({
      entries: entries(["2024-03-11", "09:00", "11:00"]),
      dailyGoal: { ...standardDailyGoal, enabled: false },
      now: time("2024-03-11", "12:00"),
    });

    expect(result?.progressPercent).toBe(5);
    expect(result?.estimate).toEqual({ type: "none" });
  });

  test("uses now for active entries", () => {
    const result = calculate({
      entries: entries(["2024-03-11", "09:00", null]),
      now: time("2024-03-11", "11:00"),
    });

    expect(result?.totalMs).toBe(2 * HOUR_MS);
    expect(result?.progressPercent).toBe(5);
  });

  test("standard 40h week is estimated to complete on Friday when no time is logged yet", () => {
    const result = calculate();

    expect(result?.progressPercent).toBe(0);
    expect(result?.estimate).toEqual({ type: "completion", day: "FRIDAY" });
  });

  test("standard 40h week remains exact when Monday and Tuesday have exactly 8h each", () => {
    const result = calculate({
      entries: entries(["2024-03-11", "09:00", "17:00"], ["2024-03-12", "09:00", "17:00"]),
      now: time("2024-03-13", "09:00"),
    });

    expect(result?.progressPercent).toBe(40);
    expect(result?.estimate).toEqual({ type: "completion", day: "FRIDAY" });
  });

  test("standard 40h week balances overtime on one day with less time on another day", () => {
    const result = calculate({
      entries: entries(["2024-03-11", "09:00", "19:00"], ["2024-03-12", "09:00", "15:00"]),
      now: time("2024-03-13", "09:00"),
    });

    expect(result?.totalMs).toBe(16 * HOUR_MS);
    expect(result?.progressPercent).toBe(40);
    expect(result?.estimate).toEqual({ type: "completion", day: "FRIDAY" });
  });

  test("standard 40h week estimates planned overtime when logged time is ahead of daily plan", () => {
    const result = calculate({
      entries: entries(["2024-03-11", "09:00", "19:00"], ["2024-03-12", "09:00", "17:00"]),
      now: time("2024-03-13", "09:00"),
    });

    expect(result?.totalMs).toBe(18 * HOUR_MS);
    expect(result?.progressPercent).toBe(45);
    expect(result?.estimate).toEqual({ type: "overtime", day: "FRIDAY", overtimeMs: 2 * HOUR_MS });
  });

  test("standard 40h week reports insufficient planned time when logged time is behind daily plan", () => {
    const result = calculate({
      entries: entries(["2024-03-11", "09:00", "15:00"], ["2024-03-12", "09:00", "15:00"]),
      now: time("2024-03-13", "09:00"),
    });

    expect(result?.totalMs).toBe(12 * HOUR_MS);
    expect(result?.progressPercent).toBe(30);
    expect(result?.estimate).toEqual({ type: "insufficient", missingMs: 4 * HOUR_MS });
  });

  test("standard 40h week includes current day partial progress in remaining daily capacity", () => {
    const result = calculate({
      entries: entries(
        ["2024-03-11", "09:00", "17:00"],
        ["2024-03-12", "09:00", "17:00"],
        ["2024-03-13", "09:00", "11:00"]
      ),
      now: time("2024-03-13", "12:00"),
    });

    expect(result?.totalMs).toBe(18 * HOUR_MS);
    expect(result?.estimate).toEqual({ type: "completion", day: "FRIDAY" });
  });

  test("standard 40h week ignores current day capacity once that daily goal is exceeded", () => {
    const result = calculate({
      entries: entries(
        ["2024-03-11", "09:00", "17:00"],
        ["2024-03-12", "09:00", "17:00"],
        ["2024-03-13", "09:00", "19:00"]
      ),
      now: time("2024-03-13", "19:00"),
    });

    expect(result?.totalMs).toBe(26 * HOUR_MS);
    expect(result?.estimate).toEqual({ type: "overtime", day: "FRIDAY", overtimeMs: 2 * HOUR_MS });
  });

  test("counts all logged time in the displayed week even on days outside weekly goal days", () => {
    const result = calculate({
      entries: entries(["2024-03-10", "09:00", "13:00"]),
      weekStart: time("2024-03-10", "00:00"),
      now: time("2024-03-11", "09:00"),
    });

    expect(result?.totalMs).toBe(4 * HOUR_MS);
    expect(result?.progressPercent).toBe(10);
    expect(result?.estimate).toEqual({ type: "overtime", day: "FRIDAY", overtimeMs: 4 * HOUR_MS });
  });

  test("uses only configured weekly goal days for planned remaining time", () => {
    const result = calculate({
      weeklyGoal: { ...standardWeeklyGoal, goalMinutes: 960, workingDays: ["TUESDAY", "THURSDAY"] },
      dailyGoal: standardDailyGoal,
      now: time("2024-03-11", "09:00"),
    });

    expect(result?.estimate).toEqual({ type: "completion", day: "THURSDAY" });
  });

  test("reports insufficient time when no configured weekly goal days remain", () => {
    const result = calculate({
      weeklyGoal: { ...standardWeeklyGoal, goalMinutes: 960, workingDays: ["MONDAY", "TUESDAY"] },
      now: time("2024-03-13", "09:00"),
    });

    expect(result?.estimate).toEqual({ type: "insufficient", missingMs: 16 * HOUR_MS });
  });

  test("reports actual overtime when weekly total already exceeds the weekly goal", () => {
    const result = calculate({
      entries: entries(
        ["2024-03-11", "09:00", "19:00"],
        ["2024-03-12", "09:00", "19:00"],
        ["2024-03-13", "09:00", "19:00"],
        ["2024-03-14", "09:00", "19:00"],
        ["2024-03-15", "09:00", "11:00"]
      ),
      now: time("2024-03-15", "11:00"),
    });

    expect(result?.totalMs).toBe(42 * HOUR_MS);
    expect(result?.progressPercent).toBe(100);
    expect(result?.estimate).toEqual({ type: "actualOvertime", overtimeMs: 2 * HOUR_MS });
  });

  test("reports actual overtime even when daily goal is disabled", () => {
    const result = calculate({
      entries: entries(
        ["2024-03-11", "09:00", "19:00"],
        ["2024-03-12", "09:00", "19:00"],
        ["2024-03-13", "09:00", "19:00"],
        ["2024-03-14", "09:00", "19:00"],
        ["2024-03-15", "09:00", "11:00"]
      ),
      dailyGoal: { ...standardDailyGoal, enabled: false },
      now: time("2024-03-15", "11:00"),
    });

    expect(result?.estimate).toEqual({ type: "actualOvertime", overtimeMs: 2 * HOUR_MS });
  });

  test("caps progress at 100 percent when weekly goal is exceeded", () => {
    const result = calculate({
      entries: entries(["2024-03-11", "00:00", "23:00"], ["2024-03-12", "00:00", "23:00"]),
      now: time("2024-03-12", "23:00"),
    });

    expect(result?.progressPercent).toBe(100);
  });
});
