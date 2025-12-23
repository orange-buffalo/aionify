import React, { createContext, useContext, useState, useCallback, useEffect } from "react"
import { Toast } from "@/components/ui/toast"

interface ToastMessage {
  id: string
  type: "error" | "success"
  message: string
}

interface ToastContextValue {
  showToast: (type: "error" | "success", message: string) => void
  clearToast: () => void
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined)

const AUTO_CLOSE_DELAY = 10000 // 10 seconds for success messages

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toast, setToast] = useState<ToastMessage | null>(null)
  const [timeoutId, setTimeoutId] = useState<NodeJS.Timeout | null>(null)

  const clearToast = useCallback(() => {
    setToast(null)
    if (timeoutId) {
      clearTimeout(timeoutId)
      setTimeoutId(null)
    }
  }, [timeoutId])

  const showToast = useCallback((type: "error" | "success", message: string) => {
    // Clear any existing toast and timeout
    if (timeoutId) {
      clearTimeout(timeoutId)
    }

    // Create new toast
    const newToast: ToastMessage = {
      id: `toast-${Date.now()}`,
      type,
      message,
    }
    setToast(newToast)

    // Auto-close only for success messages
    if (type === "success") {
      const newTimeoutId = setTimeout(() => {
        setToast(null)
        setTimeoutId(null)
      }, AUTO_CLOSE_DELAY)
      setTimeoutId(newTimeoutId)
    } else {
      setTimeoutId(null)
    }
  }, [timeoutId])

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (timeoutId) {
        clearTimeout(timeoutId)
      }
    }
  }, [timeoutId])

  return (
    <ToastContext.Provider value={{ showToast, clearToast }}>
      {children}
      {toast && (
        <Toast
          id={toast.id}
          type={toast.type}
          message={toast.message}
          onClose={clearToast}
          testId="toast-message"
        />
      )}
    </ToastContext.Provider>
  )
}

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error("useToast must be used within a ToastProvider")
  }
  return context
}
