import { Check, X } from "lucide-react"

interface FormMessageProps {
  type: "error" | "success"
  message: string
  testId?: string
}

export function FormMessage({ type, message, testId }: FormMessageProps) {
  if (type === "error") {
    return (
      <div 
        className="flex items-center gap-2 p-3 text-sm text-destructive bg-destructive/10 rounded-md" 
        data-testid={testId}
      >
        <X className="h-4 w-4 flex-shrink-0" />
        {message}
      </div>
    )
  }

  return (
    <div 
      className="flex items-center gap-2 p-3 text-sm text-green-500 bg-green-500/10 rounded-md" 
      data-testid={testId}
    >
      <Check className="h-4 w-4 flex-shrink-0" />
      {message}
    </div>
  )
}
