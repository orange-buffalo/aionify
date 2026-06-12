import { describe, expect, test } from "bun:test";
import { calculateDailyGoalProgress } from "./daily-goal-progress";
import type { TimeLogEntry } from "../components/time-logs/types";

function entry(start: string, end: string | null, id = 1): TimeLogEntry {
  return {
    id,
    startTime: time(start).toISOString(),
    endTime: end ? time(end).toISOString() : null,
    title: "Task",
    ownerId: 1,
    tags: [],
  };
}

function time(value: string): Date {
  const [hours, minutes] = value.split(":").map(Number);
  return new Date(2024, 2, 16, hours, minutes, 0, 0);
}

describe("calculateDailyGoalProgress", () => {
  test("returns null for disabled or zero-minute goals", () => {
    expect(
      calculateDailyGoalProgress({
        entries: [],
        goalMinutes: 0,
        typicalBreaks: [],
        now: time("10:00"),
      })
    ).toBeNull();
  });

  test("calculates progress and completion without breaks", () => {
    const result = calculateDailyGoalProgress({
      entries: [entry("09:00", "10:30")],
      goalMinutes: 180,
      typicalBreaks: [],
      now: time("11:00"),
    });

    expect(result?.totalMs).toBe(90 * 60 * 1000);
    expect(result?.progressPercent).toBe(50);
    expect(result?.estimatedCompletionTime.toISOString()).toBe(time("12:30").toISOString());
  });

  test("caps progress at 100 percent when the goal is exceeded", () => {
    const result = calculateDailyGoalProgress({
      entries: [entry("09:00", "12:30")],
      goalMinutes: 120,
      typicalBreaks: [],
      now: time("13:00"),
    });

    expect(result?.progressPercent).toBe(100);
    expect(result?.estimatedCompletionTime.toISOString()).toBe(time("13:00").toISOString());
  });

  test("uses now for active entries", () => {
    const result = calculateDailyGoalProgress({
      entries: [entry("09:00", null)],
      goalMinutes: 180,
      typicalBreaks: [],
      now: time("10:15"),
    });

    expect(result?.totalMs).toBe(75 * 60 * 1000);
    expect(result?.progressPercent).toBe(41);
    expect(result?.estimatedCompletionTime.toISOString()).toBe(time("12:00").toISOString());
  });

  test("delays completion by a future break", () => {
    const result = calculateDailyGoalProgress({
      entries: [entry("09:00", "10:00")],
      goalMinutes: 180,
      typicalBreaks: [{ from: "12:00", to: "12:30" }],
      now: time("11:30"),
    });

    expect(result?.estimatedCompletionTime.toISOString()).toBe(time("14:00").toISOString());
  });

  test("starts after the current break when now is inside a break", () => {
    const result = calculateDailyGoalProgress({
      entries: [entry("09:00", "10:00")],
      goalMinutes: 120,
      typicalBreaks: [{ from: "11:45", to: "12:15" }],
      now: time("12:00"),
    });

    expect(result?.estimatedCompletionTime.toISOString()).toBe(time("13:15").toISOString());
  });

  test("skips multiple breaks in chronological order", () => {
    const result = calculateDailyGoalProgress({
      entries: [entry("09:00", "10:00")],
      goalMinutes: 240,
      typicalBreaks: [
        { from: "14:00", to: "14:15" },
        { from: "12:00", to: "12:30" },
      ],
      now: time("11:30"),
    });

    expect(result?.estimatedCompletionTime.toISOString()).toBe(time("15:15").toISOString());
  });

  test("ignores breaks that have already ended", () => {
    const result = calculateDailyGoalProgress({
      entries: [entry("09:00", "10:00")],
      goalMinutes: 120,
      typicalBreaks: [{ from: "12:00", to: "12:30" }],
      now: time("13:00"),
    });

    expect(result?.estimatedCompletionTime.toISOString()).toBe(time("14:00").toISOString());
  });

  test("ignores invalid break windows", () => {
    const result = calculateDailyGoalProgress({
      entries: [entry("09:00", "10:00")],
      goalMinutes: 120,
      typicalBreaks: [
        { from: "bad", to: "12:00" },
        { from: "13:00", to: "12:00" },
      ],
      now: time("11:00"),
    });

    expect(result?.estimatedCompletionTime.toISOString()).toBe(time("12:00").toISOString());
  });
});
