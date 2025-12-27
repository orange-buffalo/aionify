import { useEffect } from "react";

/**
 * Custom hook to manage the browser tab title.
 * This provides a central place for title management that can be extended
 * for WebSocket/SSE updates in the future.
 *
 * @param title - The title to display. If null or empty, shows the default app name.
 */
export function useDocumentTitle(title: string | null) {
  useEffect(() => {
    const defaultTitle = "Aionify - Time Tracking";
    const previousTitle = document.title;

    if (title && title.trim()) {
      document.title = title;
    } else {
      document.title = defaultTitle;
    }

    // Cleanup: restore previous title when component unmounts or title changes
    return () => {
      document.title = previousTitle;
    };
  }, [title]);
}
