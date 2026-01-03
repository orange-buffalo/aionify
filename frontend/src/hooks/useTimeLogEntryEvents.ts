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
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const reconnectDelayMs = 5000; // 5 seconds

  // Keep callback ref up to date
  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  const scheduleReconnect = useCallback(() => {
    // Clear any existing reconnect timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }

    console.log(`[SSE] Scheduling reconnection in ${reconnectDelayMs / 1000} seconds...`);
    reconnectTimeoutRef.current = setTimeout(() => {
      connect();
    }, reconnectDelayMs);
  }, []);

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

        // Schedule reconnection with a new token
        scheduleReconnect();
      };

      eventSource.onopen = () => {
        console.log("[SSE] Connection established");
      };

      eventSourceRef.current = eventSource;
    } catch (error) {
      // Token fetch failed (e.g., server is down)
      console.error("[SSE] Failed to fetch token:", error);

      // Schedule reconnection to try again later
      scheduleReconnect();
    }
  }, [enabled, scheduleReconnect]);

  const disconnect = useCallback(() => {
    // Clear any pending reconnect timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

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
