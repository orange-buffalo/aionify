import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "lucide-react";

interface DateTimePickerProps {
  value: Date;
  onChange: (date: Date) => void;
  disabled?: boolean;
  locale: string;
  testIdPrefix: string;
}

export function DateTimePicker({ value, onChange, disabled, locale, testIdPrefix }: DateTimePickerProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [selectedDate, setSelectedDate] = useState(value);

  // Format display value using Intl API for proper localization
  const formatDisplayValue = (date: Date) => {
    return new Intl.DateTimeFormat(locale, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(date);
  };

  const displayValue = formatDisplayValue(value);

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
  };

  const handleTimeChange = (hours: number, minutes: number) => {
    const newDate = new Date(selectedDate);
    newDate.setHours(hours, minutes);
    setSelectedDate(newDate);
  };

  const handleApply = () => {
    onChange(selectedDate);
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
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          className="w-full justify-start text-left font-normal text-foreground"
          disabled={disabled}
          data-testid={`${testIdPrefix}-trigger`}
        >
          <Calendar className="mr-2 h-4 w-4" />
          {displayValue}
        </Button>
      </PopoverTrigger>
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

          {/* Time Picker */}
          <div className="flex items-center gap-2 pt-4 border-t">
            <span className="text-sm text-foreground">{t("timeLogs.datePicker.timeLabel")}:</span>
            <Input
              type="number"
              min="0"
              max="23"
              value={selectedDate.getHours()}
              onChange={(e) => handleTimeChange(parseInt(e.target.value) || 0, selectedDate.getMinutes())}
              className="w-16 text-foreground"
              data-testid={`${testIdPrefix}-hours`}
            />
            <span className="text-foreground">:</span>
            <Input
              type="number"
              min="0"
              max="59"
              value={selectedDate.getMinutes()}
              onChange={(e) => handleTimeChange(selectedDate.getHours(), parseInt(e.target.value) || 0)}
              className="w-16 text-foreground"
              data-testid={`${testIdPrefix}-minutes`}
            />
          </div>

          {/* Action Buttons */}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" size="sm" onClick={() => setIsOpen(false)} className="text-foreground">
              {t("timeLogs.datePicker.cancel")}
            </Button>
            <Button size="sm" onClick={handleApply} data-testid={`${testIdPrefix}-apply`}>
              {t("timeLogs.datePicker.apply")}
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
