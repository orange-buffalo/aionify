import { Check, X } from "lucide-react"

interface FormMessageProps {
  type: "error" | "success"
  message: string
  onDismiss?: () => void
  "data-testid"?: string
}

export function FormMessage({ type, message, onDismiss, "data-testid": testId }: FormMessageProps) {
  if (type === "error") {
    return (
      <div 
        className="flex items-center gap-2 p-3 text-sm text-destructive bg-destructive/10 rounded-md" 
        data-testid={testId}
      >
        <X className="h-4 w-4 flex-shrink-0" />
        <span className="flex-1">{message}</span>
        {onDismiss && (
          <button onClick={onDismiss} className="ml-auto text-foreground hover:text-foreground/80">
            <X className="h-4 w-4" />
          </button>
        )}
      </div>
    )
  }

  return (
    <div 
      className="flex items-center gap-2 p-3 text-sm text-green-500 bg-green-500/10 rounded-md" 
      data-testid={testId}
    >
      <Check className="h-4 w-4 flex-shrink-0" />
      <span className="flex-1">{message}</span>
      {onDismiss && (
        <button onClick={onDismiss} className="ml-auto text-foreground hover:text-foreground/80">
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  )
}
