import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "lucide-react";

interface DatePickerProps {
  value: Date;
  onChange: (date: Date) => void;
  disabled?: boolean;
  locale: string;
  startOfWeek: number;
  testIdPrefix: string;
}

export function DatePicker({ value, onChange, disabled, locale, startOfWeek, testIdPrefix }: DatePickerProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  // Separate state for which month/year is being viewed in the calendar (for navigation)
  const [viewingDate, setViewingDate] = useState(value);

  // Format display value using Intl API for proper localization (date only)
  const formatDisplayValue = (date: Date) => {
    return new Intl.DateTimeFormat(locale, {
      year: "numeric",
      month: "short",
      day: "numeric",
    }).format(date);
  };

  const [inputValue, setInputValue] = useState(formatDisplayValue(value));

  // Update input value and reset viewing date when value prop changes
  useEffect(() => {
    setInputValue(formatDisplayValue(value));
    setViewingDate(value);
  }, [value]);

  // Parse date string - try multiple formats
  const parseDate = (dateStr: string): Date | null => {
    dateStr = dateStr.trim();

    // Try locale-specific format first using Intl API
    try {
      // Common date separators
      const separators = ["-", "/", ".", " "];

      for (const sep of separators) {
        const parts = dateStr.split(sep);

        if (parts.length === 3) {
          // Try different date formats based on locale
          // Most locales use DD/MM/YYYY or MM/DD/YYYY or YYYY-MM-DD
          const formats = [
            // YYYY-MM-DD
            { year: 0, month: 1, day: 2 },
            // DD-MM-YYYY
            { year: 2, month: 1, day: 0 },
            // MM-DD-YYYY (US format)
            { year: 2, month: 0, day: 1 },
          ];

          for (const format of formats) {
            const year = parseInt(parts[format.year], 10);
            const month = parseInt(parts[format.month], 10);
            const day = parseInt(parts[format.day], 10);

            // Basic validation
            if (year >= 1900 && year <= 2100 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
              const date = new Date(year, month - 1, day);
              // Verify the date is valid (e.g., not Feb 31)
              if (date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day) {
                return date;
              }
            }
          }
        }
      }
    } catch (e) {
      // Parsing failed
    }

    return null;
  };

  const handleInputChange = (inputStr: string) => {
    setInputValue(inputStr);

    const parsed = parseDate(inputStr);
    if (parsed !== null) {
      setViewingDate(parsed);
      onChange(parsed);
    }
  };

  const handleBlur = () => {
    // On blur, reformat the input to ensure it's in the correct format
    const parsed = parseDate(inputValue);
    if (parsed !== null) {
      setInputValue(formatDisplayValue(parsed));
      setViewingDate(parsed);
    } else {
      // Reset to current value if invalid
      setInputValue(formatDisplayValue(value));
      setViewingDate(value);
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
    setViewingDate(newDate);
    setInputValue(formatDisplayValue(newDate));
    onChange(newDate);
    setIsOpen(false);
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
    // This ensures we're comparing just the date part, not the time
    const dateNormalized = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const valueNormalized = new Date(value.getFullYear(), value.getMonth(), value.getDate());

    return dateNormalized.getTime() === valueNormalized.getTime();
  };

  const prevMonth = () => {
    const targetMonth = viewingDate.getMonth() - 1;
    const targetYear = viewingDate.getFullYear();
    // Get the last day of the target month
    const lastDayOfTargetMonth = new Date(targetYear, targetMonth + 1, 0).getDate();
    // Use the smaller of current day or last day of target month
    const day = Math.min(viewingDate.getDate(), lastDayOfTargetMonth);
    setViewingDate(new Date(targetYear, targetMonth, day));
  };

  const nextMonth = () => {
    const targetMonth = viewingDate.getMonth() + 1;
    const targetYear = viewingDate.getFullYear();
    // Get the last day of the target month
    const lastDayOfTargetMonth = new Date(targetYear, targetMonth + 1, 0).getDate();
    // Use the smaller of current day or last day of target month
    const day = Math.min(viewingDate.getDate(), lastDayOfTargetMonth);
    setViewingDate(new Date(targetYear, targetMonth, day));
  };

  const handleOpenChange = (open: boolean) => {
    if (open) {
      // Reset viewing date to the current value when opening the calendar
      setViewingDate(value);
    }
    setIsOpen(open);
  };

  return (
    <Popover open={isOpen} onOpenChange={handleOpenChange}>
      <div className="relative">
        <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none z-10" />
        <PopoverTrigger asChild>
          <button
            type="button"
            onClick={() => setIsOpen(true)}
            disabled={disabled}
            className="flex h-10 w-full rounded-md border border-input focus-visible:border-ring bg-background px-3 py-2 text-base ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:border-ring disabled:cursor-not-allowed disabled:opacity-50 md:text-sm pl-10 text-foreground text-left transition-all duration-200"
            data-testid={`${testIdPrefix}-trigger`}
          >
            <input
              type="text"
              value={inputValue}
              readOnly
              onClick={(e) => {
                e.stopPropagation();
                setIsOpen(true);
              }}
              disabled={disabled}
              className="w-full bg-transparent border-none outline-none p-0 m-0 text-foreground cursor-pointer"
              data-testid={`${testIdPrefix}-input`}
            />
          </button>
        </PopoverTrigger>
      </div>
      <PopoverContent className="w-auto p-0 dark" align="start" data-testid="calendar">
        <div className="p-4 space-y-4">
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
          <table data-testid="calendar-grid">
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
      </PopoverContent>
    </Popover>
  );
}
