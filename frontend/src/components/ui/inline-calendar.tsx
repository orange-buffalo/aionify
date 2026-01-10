import { useState } from "react";
import { Button } from "@/components/ui/button";

interface InlineCalendarProps {
  value: Date;
  onChange: (date: Date) => void;
  locale: string;
  startOfWeek: number;
  testIdPrefix?: string;
}

export function InlineCalendar({
  value,
  onChange,
  locale,
  startOfWeek,
  testIdPrefix = "calendar",
}: InlineCalendarProps) {
  // Track which month is currently being viewed (separate from selected date)
  const [viewingDate, setViewingDate] = useState(value);

  // Generate calendar days for current month
  const generateCalendar = (date: Date) => {
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const startDate = new Date(firstDay);
    // Adjust to start from the configured start of week
    const dayOfWeek = firstDay.getDay();
    const diff = (dayOfWeek - startOfWeek + 7) % 7;
    startDate.setDate(startDate.getDate() - diff);

    const days: Date[] = [];
    const current = new Date(startDate);

    for (let i = 0; i < 42; i++) {
      days.push(new Date(current));
      current.setDate(current.getDate() + 1);
    }

    return { days, month, year };
  };

  const { days, month, year } = generateCalendar(viewingDate);

  // Get localized month name using Intl API
  const getMonthName = (monthIndex: number) => {
    return new Intl.DateTimeFormat(locale, { month: "long" }).format(new Date(year, monthIndex, 1));
  };

  // Get localized day names using Intl API starting from the configured start of week
  const getDayNames = () => {
    // Use a known date and offset by startOfWeek
    const baseDate = new Date(2000, 0, 2); // Jan 2, 2000 is a Sunday (day 0)
    return Array.from({ length: 7 }, (_, i) => {
      const date = new Date(baseDate);
      date.setDate(baseDate.getDate() + ((startOfWeek + i) % 7));
      return new Intl.DateTimeFormat(locale, { weekday: "short" }).format(date).toUpperCase();
    });
  };

  const dayNames = getDayNames();
  const monthName = getMonthName(month);

  const handleDateClick = (day: Date) => {
    const newDate = new Date(value);
    newDate.setFullYear(day.getFullYear(), day.getMonth(), day.getDate());
    onChange(newDate);
  };

  const isToday = (date: Date) => {
    const today = new Date();
    return (
      date.getDate() === today.getDate() &&
      date.getMonth() === today.getMonth() &&
      date.getFullYear() === today.getFullYear()
    );
  };

  const isSelected = (date: Date) => {
    // Normalize both dates to midnight local time for comparison
    const dateNormalized = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const valueNormalized = new Date(value.getFullYear(), value.getMonth(), value.getDate());

    return dateNormalized.getTime() === valueNormalized.getTime();
  };

  const prevMonth = () => {
    const newViewingDate = new Date(viewingDate);
    newViewingDate.setMonth(newViewingDate.getMonth() - 1);
    setViewingDate(newViewingDate);
  };

  const nextMonth = () => {
    const newViewingDate = new Date(viewingDate);
    newViewingDate.setMonth(newViewingDate.getMonth() + 1);
    setViewingDate(newViewingDate);
  };

  return (
    <div className="space-y-2">
      {/* Month/Year Header */}
      <div className="flex items-center justify-between">
        <Button variant="ghost" size="sm" onClick={prevMonth} className="h-7 w-7 p-0 text-foreground">
          ‹
        </Button>
        <div className="text-sm font-medium text-foreground">
          {monthName} {year}
        </div>
        <Button variant="ghost" size="sm" onClick={nextMonth} className="h-7 w-7 p-0 text-foreground">
          ›
        </Button>
      </div>

      {/* Calendar Grid */}
      <table data-testid={`${testIdPrefix}-grid`}>
        <thead>
          <tr>
            {dayNames.map((day, idx) => (
              <th key={idx} className="text-center text-xs text-muted-foreground p-2">
                {day}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: 6 }, (_, weekIdx) => (
            <tr key={weekIdx}>
              {days.slice(weekIdx * 7, weekIdx * 7 + 7).map((day, dayIdx) => {
                const isCurrentMonth = day.getMonth() === month;
                const todayDate = isToday(day);
                const selected = isSelected(day);

                return (
                  <td key={weekIdx * 7 + dayIdx}>
                    <Button
                      variant="ghost"
                      size="sm"
                      className={`
                        h-9 w-9 p-0 font-normal text-foreground
                        ${!isCurrentMonth ? "text-muted-foreground opacity-50" : ""}
                        ${selected ? "bg-primary text-primary-foreground hover:bg-primary" : ""}
                        ${todayDate && !selected ? "bg-accent" : ""}
                      `}
                      onClick={() => handleDateClick(day)}
                    >
                      {day.getDate()}
                    </Button>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
