import { useEffect, useRef, useCallback } from "react";

export interface TimeLogEntryEvent {
  type: "ENTRY_STARTED" | "ENTRY_STOPPED";
  entryId: number | null;
  title: string | null;
}

/**
 * Hook for subscribing to Server-Sent Events for time log entry updates.
 * Automatically handles connection lifecycle, reconnection, and cleanup.
 *
 * @param onEvent Callback invoked when an event is received
 * @param enabled Whether the SSE connection should be active
 */
export function useTimeLogEntryEvents(onEvent: (event: TimeLogEntryEvent) => void, enabled: boolean = true) {
  const eventSourceRef = useRef<EventSource | null>(null);
  const onEventRef = useRef(onEvent);

  // Keep callback ref up to date
  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  const connect = useCallback(() => {
    if (!enabled) return;

    // Don't create duplicate connections
    if (eventSourceRef.current) {
      return;
    }

    console.log("[SSE] Connecting to time log entry events...");

    // Get JWT token from localStorage
    const token = localStorage.getItem("aionify_token");
    if (!token) {
      console.error("[SSE] No authentication token found, cannot connect");
      return;
    }

    // Pass token as query parameter since EventSource doesn't support custom headers
    const url = `/api-ui/time-log-entries/events?token=${encodeURIComponent(token)}`;

    const eventSource = new EventSource(url, {
      withCredentials: true,
    });

    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data) as TimeLogEntryEvent;
        console.log("[SSE] Received event:", data);
        onEventRef.current(data);
      } catch (error) {
        console.error("[SSE] Failed to parse event data:", error);
      }
    };

    eventSource.addEventListener("heartbeat", () => {
      // Heartbeat event to keep connection alive - no action needed
      console.debug("[SSE] Heartbeat received");
    });

    eventSource.onerror = (error) => {
      console.error("[SSE] Connection error:", error);

      // Close and cleanup on error
      if (eventSource.readyState === EventSource.CLOSED) {
        console.log("[SSE] Connection closed, will reconnect...");
        eventSourceRef.current = null;

        // Reconnect after a delay
        setTimeout(() => {
          if (enabled) {
            connect();
          }
        }, 3000);
      }
    };

    eventSource.onopen = () => {
      console.log("[SSE] Connection established");
    };

    eventSourceRef.current = eventSource;
  }, [enabled]);

  const disconnect = useCallback(() => {
    if (eventSourceRef.current) {
      console.log("[SSE] Disconnecting from events");
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  }, []);

  // Connect on mount, disconnect on unmount
  useEffect(() => {
    if (enabled) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [enabled, connect, disconnect]);

  return {
    connected: eventSourceRef.current !== null,
    reconnect: connect,
  };
}
