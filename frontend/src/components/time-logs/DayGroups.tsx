import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { DayGroup } from "./DayGroup";
import { formatISODate, calculateDuration } from "@/lib/time-utils";
import { formatDate } from "@/lib/date-format";
import type { TimeLogEntry, DayGroup as DayGroupType } from "./types";

interface DayGroupsProps {
  entries: TimeLogEntry[];
  activeEntry: TimeLogEntry | null;
  locale: string;
  startOfWeek: number;
  onDataChange: () => Promise<void>;
}

export function DayGroups({ entries, activeEntry, locale, startOfWeek, onDataChange }: DayGroupsProps) {
  const { t } = useTranslation();
  const [dayGroups, setDayGroups] = useState<DayGroupType[]>([]);

  // Get day title (Today, Yesterday, or day of week + date)
  function getDayTitle(dateStr: string, locale: string): string {
    const [year, month, day] = dateStr.split("-").map(Number);
    const date = new Date(year, month - 1, day);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    today.setHours(0, 0, 0, 0);
    yesterday.setHours(0, 0, 0, 0);
    date.setHours(0, 0, 0, 0);

    if (date.getTime() === today.getTime()) {
      return t("timeLogs.today");
    } else if (date.getTime() === yesterday.getTime()) {
      return t("timeLogs.yesterday");
    } else {
      const dayName = date.toLocaleDateString(locale, { weekday: "long" });
      const formattedDate = formatDate(dateStr, locale);
      return `${dayName}, ${formattedDate}`;
    }
  }

  // Group entries by day - always show on start day
  function groupEntriesByDay(entries: TimeLogEntry[], locale: string): DayGroupType[] {
    const groups: { [key: string]: TimeLogEntry[] } = {};

    entries.forEach((entry) => {
      const startDate = new Date(entry.startTime);
      const startDay = formatISODate(startDate);

      if (!groups[startDay]) groups[startDay] = [];
      groups[startDay].push(entry);
    });

    return Object.entries(groups)
      .map(([date, entries]) => {
        const totalDuration = entries.reduce(
          (sum, entry) => sum + calculateDuration(entry.startTime, entry.endTime),
          0
        );

        return {
          date,
          displayTitle: getDayTitle(date, locale),
          entries: entries.sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime()),
          totalDuration,
        };
      })
      .sort((a, b) => {
        const [yearA, monthA, dayA] = a.date.split("-").map(Number);
        const [yearB, monthB, dayB] = b.date.split("-").map(Number);
        const dateA = new Date(yearA, monthA - 1, dayA);
        const dateB = new Date(yearB, monthB - 1, dayB);
        return dateB.getTime() - dateA.getTime();
      });
  }

  // Recalculate day groups when entries change
  useEffect(() => {
    const groups = groupEntriesByDay(entries, locale);
    setDayGroups(groups);
  }, [entries, locale]);

  if (dayGroups.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground" data-testid="no-entries">
        {t("timeLogs.noEntries")}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {dayGroups.map((group) => (
        <DayGroup
          key={group.date}
          group={group}
          locale={locale}
          startOfWeek={startOfWeek}
          onDataChange={onDataChange}
        />
      ))}
    </div>
  );
}
