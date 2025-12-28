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
  testIdPrefix: string;
}

export function DatePicker({ value, onChange, disabled, locale, testIdPrefix }: DatePickerProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [selectedDate, setSelectedDate] = useState(value);

  // Format display value using Intl API for proper localization (date only)
  const formatDisplayValue = (date: Date) => {
    return new Intl.DateTimeFormat(locale, {
      year: "numeric",
      month: "short",
      day: "numeric",
    }).format(date);
  };

  const [inputValue, setInputValue] = useState(formatDisplayValue(value));

  // Update input value when value prop changes
  useEffect(() => {
    setInputValue(formatDisplayValue(value));
    setSelectedDate(value);
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
      setSelectedDate(parsed);
      onChange(parsed);
    }
  };

  const handleBlur = () => {
    // On blur, reformat the input to ensure it's in the correct format
    const parsed = parseDate(inputValue);
    if (parsed !== null) {
      setInputValue(formatDisplayValue(parsed));
      setSelectedDate(parsed);
    } else {
      // Reset to current value if invalid
      setInputValue(formatDisplayValue(value));
      setSelectedDate(value);
    }
  };

  // Generate calendar days for current month
  const generateCalendar = (date: Date) => {
    const year = date.getFullYear();
    const month = date.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const startDate = new Date(firstDay);
    startDate.setDate(startDate.getDate() - firstDay.getDay());

    const days: Date[] = [];
    const current = new Date(startDate);

    for (let i = 0; i < 42; i++) {
      days.push(new Date(current));
      current.setDate(current.getDate() + 1);
    }

    return { days, month, year };
  };

  const { days, month, year } = generateCalendar(selectedDate);

  // Get localized month name using Intl API
  const getMonthName = (monthIndex: number) => {
    return new Intl.DateTimeFormat(locale, { month: "long" }).format(new Date(year, monthIndex, 1));
  };

  // Get localized day names using Intl API
  const getDayNames = () => {
    // Use Sunday, Jan 2, 2000 as base date (arbitrary past date, day of week is what matters)
    const baseDate = new Date(2000, 0, 2);
    return Array.from({ length: 7 }, (_, i) => {
      const date = new Date(baseDate);
      date.setDate(baseDate.getDate() + i);
      return new Intl.DateTimeFormat(locale, { weekday: "short" }).format(date).toUpperCase();
    });
  };

  const dayNames = getDayNames();
  const monthName = getMonthName(month);

  const handleDateClick = (day: Date) => {
    const newDate = new Date(selectedDate);
    newDate.setFullYear(day.getFullYear(), day.getMonth(), day.getDate());
    setSelectedDate(newDate);
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
    return (
      date.getDate() === selectedDate.getDate() &&
      date.getMonth() === selectedDate.getMonth() &&
      date.getFullYear() === selectedDate.getFullYear()
    );
  };

  const prevMonth = () => {
    setSelectedDate(new Date(selectedDate.getFullYear(), selectedDate.getMonth() - 1, selectedDate.getDate()));
  };

  const nextMonth = () => {
    setSelectedDate(new Date(selectedDate.getFullYear(), selectedDate.getMonth() + 1, selectedDate.getDate()));
  };

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
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
              onChange={(e) => {
                e.stopPropagation();
                handleInputChange(e.target.value);
              }}
              onBlur={handleBlur}
              onClick={(e) => {
                e.stopPropagation();
                setIsOpen(true);
              }}
              disabled={disabled}
              className="w-full bg-transparent border-none outline-none p-0 m-0 text-foreground"
              data-testid={`${testIdPrefix}-input`}
            />
          </button>
        </PopoverTrigger>
      </div>
      <PopoverContent className="w-auto p-0 dark" align="start">
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
          <div className="grid grid-cols-7 gap-1">
            {dayNames.map((day, idx) => (
              <div key={idx} className="text-center text-xs text-muted-foreground p-2">
                {day}
              </div>
            ))}
            {days.map((day, idx) => {
              const isCurrentMonth = day.getMonth() === month;
              const todayDate = isToday(day);
              const selected = isSelected(day);

              return (
                <Button
                  key={idx}
                  variant="ghost"
                  size="sm"
                  className={`
                    h-9 w-9 p-0 font-normal text-foreground
                    ${!isCurrentMonth && "text-muted-foreground opacity-50"}
                    ${selected && "bg-primary text-primary-foreground hover:bg-primary"}
                    ${todayDate && !selected && "bg-accent"}
                  `}
                  onClick={() => handleDateClick(day)}
                >
                  {day.getDate()}
                </Button>
              );
            })}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
