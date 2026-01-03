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
  const reconnectAttemptsRef = useRef(0);
  const maxReconnectAttempts = 3;

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
        // EventSource will automatically try to reconnect, but it will use the same URL
        // with the expired token. We need to close it and create a new connection with a new token.
        console.log("[SSE] Connection error, readyState:", eventSource.readyState);

        // Close the current connection
        eventSource.close();
        eventSourceRef.current = null;

        // Try to reconnect with a new token if we haven't exceeded retry limit
        if (reconnectAttemptsRef.current < maxReconnectAttempts) {
          reconnectAttemptsRef.current += 1;
          console.log(`[SSE] Reconnecting (attempt ${reconnectAttemptsRef.current}/${maxReconnectAttempts})...`);

          // Reconnect after a short delay to avoid rapid reconnection loops
          setTimeout(() => {
            connect();
          }, 1000 * reconnectAttemptsRef.current); // Exponential backoff
        } else {
          console.error("[SSE] Max reconnection attempts reached");
        }
      };

      eventSource.onopen = () => {
        console.log("[SSE] Connection established");
        // Reset reconnect attempts on successful connection
        reconnectAttemptsRef.current = 0;
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
    // Reset reconnect attempts when explicitly disconnecting
    reconnectAttemptsRef.current = 0;
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
