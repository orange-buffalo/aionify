import { X, Check, AlertCircle } from "lucide-react"
import { cn } from "@/lib/utils"

export interface ToastProps {
  id: string
  type: "error" | "success"
  message: string
  onClose: () => void
  testId?: string
}

export function Toast({ type, message, onClose, testId }: ToastProps) {
  const isError = type === "error"
  
  return (
    <div
      className={cn(
        "fixed top-4 right-4 z-50 flex items-start gap-3 p-4 rounded-lg shadow-lg border max-w-md animate-in slide-in-from-top-5 fade-in",
        isError 
          ? "bg-destructive/10 border-destructive text-destructive" 
          : "bg-green-500/10 border-green-500 text-green-500"
      )}
      data-testid={testId}
      role="alert"
      aria-live="polite"
    >
      <div className="flex-shrink-0 mt-0.5">
        {isError ? (
          <AlertCircle className="h-5 w-5" />
        ) : (
          <Check className="h-5 w-5" />
        )}
      </div>
      
      <div className="flex-1 text-sm leading-relaxed">
        {message}
      </div>
      
      <button
        onClick={onClose}
        className={cn(
          "flex-shrink-0 rounded-sm opacity-70 transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-offset-2",
          isError ? "focus:ring-destructive" : "focus:ring-green-500"
        )}
        data-testid={testId ? `${testId}-close` : undefined}
        aria-label="Close notification"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  )
}
