import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { DatePicker } from "@/components/ui/date-picker"
import { TimePicker } from "@/components/ui/time-picker"

interface EditEntryFormProps {
  title: string
  startDateTime: Date
  endDateTime?: Date | null
  locale: string
  isSaving: boolean
  onTitleChange: (title: string) => void
  onStartDateTimeChange: (date: Date) => void
  onEndDateTimeChange?: (date: Date) => void
  onSave: () => void
  onCancel: () => void
  testIdPrefix?: string
}

export function EditEntryForm({
  title,
  startDateTime,
  endDateTime,
  locale,
  isSaving,
  onTitleChange,
  onStartDateTimeChange,
  onEndDateTimeChange,
  onSave,
  onCancel,
  testIdPrefix = "edit"
}: EditEntryFormProps) {
  const { t } = useTranslation()

  return (
    <div className="space-y-4">
      <div>
        <Label htmlFor={`${testIdPrefix}-title`} className="text-foreground">
          {t('timeLogs.currentEntry.titleLabel')}
        </Label>
        <Input
          id={`${testIdPrefix}-title`}
          value={title}
          onChange={(e) => onTitleChange(e.target.value)}
          className="text-foreground mt-2"
          data-testid={`${testIdPrefix}-title-input`}
          disabled={isSaving}
        />
      </div>
      <div>
        <Label className="text-foreground">
          {t('timeLogs.currentEntry.startTimeLabel')}
        </Label>
        <div className="mt-2 flex items-center gap-2">
          <DatePicker
            value={startDateTime}
            onChange={onStartDateTimeChange}
            disabled={isSaving}
            locale={locale}
            testIdPrefix={`${testIdPrefix}-date`}
          />
          <TimePicker
            value={startDateTime}
            onChange={onStartDateTimeChange}
            disabled={isSaving}
            locale={locale}
            testIdPrefix={`${testIdPrefix}-time`}
          />
        </div>
      </div>
      {endDateTime !== undefined && onEndDateTimeChange && (
        <div>
          <Label className="text-foreground">
            {t('timeLogs.currentEntry.endTimeLabel')}
          </Label>
          <div className="mt-2 flex items-center gap-2">
            <DatePicker
              value={endDateTime || new Date()}
              onChange={onEndDateTimeChange}
              disabled={isSaving}
              locale={locale}
              testIdPrefix={`${testIdPrefix}-end-date`}
            />
            <TimePicker
              value={endDateTime || new Date()}
              onChange={onEndDateTimeChange}
              disabled={isSaving}
              locale={locale}
              testIdPrefix={`${testIdPrefix}-end-time`}
            />
          </div>
        </div>
      )}
      <div className="flex items-center gap-2">
        <Button
          onClick={onSave}
          disabled={!title.trim() || isSaving}
          data-testid={`save-${testIdPrefix}-button`}
          className="bg-teal-600 hover:bg-teal-700"
        >
          {t('timeLogs.currentEntry.save')}
        </Button>
        <Button
          variant="ghost"
          onClick={onCancel}
          disabled={isSaving}
          data-testid={`cancel-${testIdPrefix}-button`}
          className="text-foreground"
        >
          {t('timeLogs.currentEntry.cancel')}
        </Button>
      </div>
    </div>
  )
}
