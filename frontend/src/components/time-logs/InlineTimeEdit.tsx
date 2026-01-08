import { useState, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Send, Loader2 } from "lucide-react";
import { TimePicker } from "@/components/ui/time-picker";
import { formatTime, formatDate } from "@/lib/date-format";

interface InlineTimeEditProps {
  currentDateTime: string; // ISO 8601 timestamp
  onSave: (newDateTime: Date) => Promise<void>;
  locale: string;
  startOfWeek: number;
  testIdPrefix?: string;
}

export function InlineTimeEdit({
  currentDateTime,
  onSave,
  locale,
  startOfWeek,
  testIdPrefix = "inline-time-edit",
}: InlineTimeEditProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [dateTime, setDateTime] = useState(new Date(currentDateTime));
  const [isSaving, setIsSaving] = useState(false);
  const timePickerRef = useRef<HTMLDivElement>(null);

  // Reset dateTime when popover opens
  useEffect(() => {
    if (isOpen) {
      setDateTime(new Date(currentDateTime));
    }
  }, [isOpen, currentDateTime]);

  const handleSave = async () => {
    if (isSaving) return;

    setIsSaving(true);
    try {
      await onSave(dateTime);
      setIsOpen(false);
    } finally {
      setIsSaving(false);
    }
  };

  // Generate calendar days for current month
  const generateCalendar = (date: Date) => {
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
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

  const { days, month, year } = generateCalendar(dateTime);

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
    const newDateTime = new Date(dateTime);
    newDateTime.setFullYear(day.getFullYear(), day.getMonth(), day.getDate());
    setDateTime(newDateTime);
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
    const dateTimeNormalized = new Date(dateTime.getFullYear(), dateTime.getMonth(), dateTime.getDate());

    return dateNormalized.getTime() === dateTimeNormalized.getTime();
  };

  const prevMonth = () => {
    const targetMonth = dateTime.getMonth() - 1;
    const targetYear = dateTime.getFullYear();
    // Get the last day of the target month
    const lastDayOfTargetMonth = new Date(targetYear, targetMonth + 1, 0).getDate();
    // Use the smaller of current day or last day of target month
    const day = Math.min(dateTime.getDate(), lastDayOfTargetMonth);
    const newDateTime = new Date(dateTime);
    newDateTime.setFullYear(targetYear, targetMonth, day);
    setDateTime(newDateTime);
  };

  const nextMonth = () => {
    const targetMonth = dateTime.getMonth() + 1;
    const targetYear = dateTime.getFullYear();
    // Get the last day of the target month
    const lastDayOfTargetMonth = new Date(targetYear, targetMonth + 1, 0).getDate();
    // Use the smaller of current day or last day of target month
    const day = Math.min(dateTime.getDate(), lastDayOfTargetMonth);
    const newDateTime = new Date(dateTime);
    newDateTime.setFullYear(targetYear, targetMonth, day);
    setDateTime(newDateTime);
  };

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
      <PopoverTrigger asChild>
        <span className="text-muted-foreground cursor-pointer" data-testid={`${testIdPrefix}-trigger`}>
          {formatTime(currentDateTime, locale)}
        </span>
      </PopoverTrigger>
      <PopoverContent className="dark w-auto p-3" align="start" side="bottom" data-testid={`${testIdPrefix}-popover`}>
        <div className="space-y-3">
          {/* Time Input Row */}
          <div className="flex items-center gap-2">
            <div ref={timePickerRef}>
              <TimePicker
                value={dateTime}
                onChange={setDateTime}
                disabled={isSaving}
                locale={locale}
                testIdPrefix={`${testIdPrefix}-time`}
              />
            </div>
            <Button
              onClick={handleSave}
              disabled={isSaving}
              className="bg-teal-600 hover:bg-teal-700"
              size="sm"
              data-testid={`${testIdPrefix}-save-button`}
            >
              {isSaving ? (
                <Loader2 className="h-4 w-4 animate-spin" data-testid={`${testIdPrefix}-loading-icon`} />
              ) : (
                <Send className="h-4 w-4" data-testid={`${testIdPrefix}-send-icon`} />
              )}
            </Button>
          </div>

          {/* Inline Calendar */}
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
            <table data-testid={`${testIdPrefix}-calendar-grid`}>
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
        </div>
      </PopoverContent>
    </Popover>
  );
}
