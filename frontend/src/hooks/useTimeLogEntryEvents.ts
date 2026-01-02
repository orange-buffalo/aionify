import { useEffect, useRef, useCallback } from "react";
import { apiPost } from "@/lib/api";

export interface TimeLogEntryEvent {
  type: "ENTRY_STARTED" | "ENTRY_STOPPED";
  entryId: number;
  title: string;
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

  const connect = useCallback(async () => {
    if (!enabled) return;

    // Don't create duplicate connections
    if (eventSourceRef.current) {
      return;
    }

    console.log("[SSE] Connecting to time log entry events...");

    try {
      // Generate a short-lived SSE token
      const { token } = await apiPost<{ token: string }>("/api-ui/time-log-entries/sse-token", {});

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
          console.debug("[SSE] Failed to parse event data:", error);
        }
      };

      eventSource.addEventListener("heartbeat", () => {
        // Heartbeat event to keep connection alive - no action needed
        console.debug("[SSE] Heartbeat received");
      });

      eventSource.onerror = () => {
        // Close and cleanup on error
        // Don't automatically reconnect to avoid infinite loops
        if (eventSource.readyState === EventSource.CLOSED) {
          console.log("[SSE] Connection closed");
          eventSourceRef.current = null;
        }
      };

      eventSource.onopen = () => {
        console.log("[SSE] Connection established");
      };

      eventSourceRef.current = eventSource;
    } catch (error) {
      console.error("[SSE] Failed to connect:", error);
    }
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
