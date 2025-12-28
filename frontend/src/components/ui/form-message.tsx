import { Check, X, AlertCircle } from "lucide-react";
import { useState } from "react";

interface FormMessageProps {
  type: "error" | "success";
  message: string;
  testId?: string;
  onClose?: () => void;
}

export function FormMessage({ type, message, testId, onClose }: FormMessageProps) {
  const [isVisible, setIsVisible] = useState(true);

  const handleClose = () => {
    setIsVisible(false);
    onClose?.();
  };

  if (!isVisible) {
    return null;
  }

  if (type === "error") {
    return (
      <div
        className="flex items-center gap-2 p-3 text-sm text-destructive bg-destructive/10 rounded-md mb-4"
        data-testid={testId}
      >
        <AlertCircle className="h-4 w-4 flex-shrink-0" />
        <span className="flex-1">{message}</span>
        <button
          type="button"
          onClick={handleClose}
          className="text-destructive hover:text-destructive/80 transition-colors"
          aria-label="Close message"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    );
  }

  return (
    <div
      className="flex items-center gap-2 p-3 text-sm text-green-600 dark:text-green-400 bg-green-600/10 dark:bg-green-400/10 rounded-md mb-4"
      data-testid={testId}
    >
      <Check className="h-4 w-4 flex-shrink-0" />
      <span className="flex-1">{message}</span>
      <button
        type="button"
        onClick={handleClose}
        className="text-green-600 dark:text-green-400 hover:opacity-80 transition-opacity"
        aria-label="Close message"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
