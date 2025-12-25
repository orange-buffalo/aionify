import { useState, useEffect } from "react"
import { Input } from "@/components/ui/input"
import { Clock } from "lucide-react"
import { uses12HourFormat } from "@/lib/date-format"

interface TimePickerProps {
  value: Date
  onChange: (date: Date) => void
  disabled?: boolean
  locale: string
  testIdPrefix: string
}

export function TimePicker({ value, onChange, disabled, locale, testIdPrefix }: TimePickerProps) {
  const is12HourFormat = uses12HourFormat(locale)

  // Format time value for input
  const formatTimeForInput = (date: Date): string => {
    const hours = date.getHours()
    const minutes = date.getMinutes()
    
    if (is12HourFormat) {
      const hour12 = hours % 12 || 12
      const period = hours < 12 ? 'AM' : 'PM'
      return `${hour12.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')} ${period}`
    } else {
      return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`
    }
  }

  const [inputValue, setInputValue] = useState(formatTimeForInput(value))

  // Note: We don't update inputValue when value prop changes to avoid
  // interference when the date portion changes. The time input is user-controlled.

  // Parse time input and update date
  const handleTimeChange = (timeStr: string) => {
    setInputValue(timeStr)
    
    // Try to parse the time
    const parsed = parseTime(timeStr)
    if (parsed !== null) {
      const newDate = new Date(value)
      newDate.setHours(parsed.hours, parsed.minutes, 0, 0)
      onChange(newDate)
    }
  }

  // Parse time string to hours and minutes
  const parseTime = (timeStr: string): { hours: number; minutes: number } | null => {
    // Remove extra spaces
    timeStr = timeStr.trim()
    
    // First try 12-hour format with AM/PM (more specific)
    const match12 = timeStr.match(/^(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)$/i)
    if (match12) {
      let hours = parseInt(match12[1], 10)
      const minutes = parseInt(match12[2], 10)
      const period = match12[3].toUpperCase()
      
      // Validate
      if (hours < 1 || hours > 12 || minutes < 0 || minutes > 59) {
        return null
      }
      
      // Convert to 24-hour format
      if (period === 'AM') {
        hours = hours === 12 ? 0 : hours
      } else {
        hours = hours === 12 ? 12 : hours + 12
      }
      
      return { hours, minutes }
    }
    
    // Then try 24-hour format (less specific, so check second)
    const match24 = timeStr.match(/^(\d{1,2}):(\d{2})$/)
    if (match24) {
      const hours = parseInt(match24[1], 10)
      const minutes = parseInt(match24[2], 10)
      
      // Validate
      if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
        return null
      }
      
      return { hours, minutes }
    }
    
    return null
  }

  const handleBlur = () => {
    // On blur, reformat the input to ensure it's in the correct format
    const parsed = parseTime(inputValue)
    if (parsed !== null) {
      const newDate = new Date(value)
      newDate.setHours(parsed.hours, parsed.minutes, 0, 0)
      setInputValue(formatTimeForInput(newDate))
    } else {
      // Reset to current value if invalid
      setInputValue(formatTimeForInput(value))
    }
  }

  return (
    <div className="relative flex items-center max-w-[150px]">
      <Clock className="absolute left-3 h-4 w-4 text-muted-foreground pointer-events-none" />
      <Input
        type="text"
        value={inputValue}
        onChange={(e) => handleTimeChange(e.target.value)}
        onBlur={handleBlur}
        disabled={disabled}
        className="pl-10 text-foreground"
        placeholder={is12HourFormat ? "HH:MM AM/PM" : "HH:MM"}
        data-testid={`${testIdPrefix}-input`}
      />
    </div>
  )
}
