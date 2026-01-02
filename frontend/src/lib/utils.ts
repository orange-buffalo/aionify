import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Copies text to clipboard with fallback for non-secure contexts.
 *
 * The Clipboard API (navigator.clipboard) is only available in secure contexts (HTTPS or localhost).
 * This function provides a fallback using the legacy execCommand approach for HTTP contexts.
 *
 * @param text The text to copy to clipboard
 * @throws Error if copying fails
 */
export async function copyToClipboard(text: string): Promise<void> {
  // Try modern Clipboard API first (works in secure contexts)
  if (navigator.clipboard && navigator.clipboard.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }

  // Fallback for non-secure contexts (HTTP)
  // Note: document.execCommand('copy') is deprecated but necessary for HTTP contexts
  // where the Clipboard API is unavailable. This provides a graceful fallback.
  const textArea = document.createElement("textarea");
  textArea.value = text;

  // Make the textarea invisible and out of viewport
  textArea.style.position = "fixed";
  textArea.style.top = "0";
  textArea.style.left = "0";
  textArea.style.width = "2em";
  textArea.style.height = "2em";
  textArea.style.padding = "0";
  textArea.style.border = "none";
  textArea.style.outline = "none";
  textArea.style.boxShadow = "none";
  textArea.style.background = "transparent";

  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();

  try {
    const successful = document.execCommand("copy");
    if (!successful) {
      throw new Error("Copy command was unsuccessful");
    }
  } finally {
    // Use .remove() for safer element removal
    textArea.remove();
  }
}
