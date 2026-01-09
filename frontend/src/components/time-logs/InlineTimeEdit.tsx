import { useState, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Send, Loader2 } from "lucide-react";
import { TimePicker } from "@/components/ui/time-picker";
import { InlineCalendar } from "@/components/ui/inline-calendar";
import { formatTime, formatTimeWithWeekday } from "@/lib/date-format";

interface InlineTimeEditProps {
  currentDateTime: string; // ISO 8601 timestamp
  onSave: (newDateTime: Date) => Promise<void>;
  locale: string;
  startOfWeek: number;
  showWeekday?: boolean; // Show weekday in trigger for cross-day entries
  testIdPrefix?: string;
}

export function InlineTimeEdit({
  currentDateTime,
  onSave,
  locale,
  startOfWeek,
  showWeekday = false,
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

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
      <PopoverTrigger asChild>
        <span className="text-muted-foreground cursor-pointer" data-testid={`${testIdPrefix}-trigger`}>
          {showWeekday ? formatTimeWithWeekday(currentDateTime, locale) : formatTime(currentDateTime, locale)}
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
          <InlineCalendar
            value={dateTime}
            onChange={setDateTime}
            locale={locale}
            startOfWeek={startOfWeek}
            testIdPrefix={testIdPrefix}
          />
        </div>
      </PopoverContent>
    </Popover>
  );
}
