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
  // Generate calendar days for current month - use UTC to avoid timezone issues
  const generateCalendar = (date: Date) => {
    const year = date.getUTCFullYear();
    const month = date.getUTCMonth();
    const firstDay = new Date(Date.UTC(year, month, 1));
    const startDate = new Date(firstDay);
    // Adjust to start from the configured start of week
    const dayOfWeek = firstDay.getUTCDay();
    const diff = (dayOfWeek - startOfWeek + 7) % 7;
    startDate.setUTCDate(firstDay.getUTCDate() - diff);

    const days: Date[] = [];
    const current = new Date(startDate);

    for (let i = 0; i < 42; i++) {
      days.push(new Date(current));
      current.setUTCDate(current.getUTCDate() + 1);
    }

    return { days, month, year };
  };

  const { days, month, year } = generateCalendar(value);

  // Get localized month name using Intl API
  const getMonthName = (monthIndex: number) => {
    // Use UTC to avoid timezone-related month shifts
    return new Intl.DateTimeFormat(locale, { month: "long", timeZone: "UTC" }).format(
      new Date(Date.UTC(year, monthIndex, 1))
    );
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
    // Use UTC methods to preserve the time portion correctly
    newDate.setUTCFullYear(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate());
    onChange(newDate);
  };

  const isToday = (date: Date) => {
    const today = new Date();
    return (
      date.getUTCDate() === today.getUTCDate() &&
      date.getUTCMonth() === today.getUTCMonth() &&
      date.getUTCFullYear() === today.getUTCFullYear()
    );
  };

  const isSelected = (date: Date) => {
    // Compare UTC dates
    return (
      date.getUTCDate() === value.getUTCDate() &&
      date.getUTCMonth() === value.getUTCMonth() &&
      date.getUTCFullYear() === value.getUTCFullYear()
    );
  };

  const prevMonth = () => {
    const targetMonth = value.getUTCMonth() - 1;
    const targetYear = value.getUTCFullYear();
    // Get the last day of the target month
    const lastDayOfTargetMonth = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
    // Use the smaller of current day or last day of target month
    const day = Math.min(value.getUTCDate(), lastDayOfTargetMonth);
    const newDate = new Date(value);
    newDate.setUTCFullYear(targetYear, targetMonth, day);
    onChange(newDate);
  };

  const nextMonth = () => {
    const targetMonth = value.getUTCMonth() + 1;
    const targetYear = value.getUTCFullYear();
    // Get the last day of the target month
    const lastDayOfTargetMonth = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
    // Use the smaller of current day or last day of target month
    const day = Math.min(value.getUTCDate(), lastDayOfTargetMonth);
    const newDate = new Date(value);
    newDate.setUTCFullYear(targetYear, targetMonth, day);
    onChange(newDate);
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
                const isCurrentMonth = day.getUTCMonth() === month;
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
                      {day.getUTCDate()}
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
