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
  const tokenRequestRef = useRef<AbortController | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const heartbeatTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const reconnectDelayMs = 5000; // 5 seconds
  const heartbeatTimeoutMs = 45000; // 45 seconds = 1.5x backend heartbeat interval (30s)

  // Keep callback ref up to date
  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  const scheduleReconnect = useCallback(() => {
    // Clear any existing reconnect timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }

    // Clear heartbeat timeout as we're reconnecting
    if (heartbeatTimeoutRef.current) {
      clearTimeout(heartbeatTimeoutRef.current);
      heartbeatTimeoutRef.current = null;
    }

    console.log(`[SSE] Scheduling reconnection in ${reconnectDelayMs / 1000} seconds...`);
    reconnectTimeoutRef.current = setTimeout(() => {
      connect();
    }, reconnectDelayMs);
  }, []);

  const resetHeartbeatTimeout = useCallback(() => {
    // Clear any existing heartbeat timeout
    if (heartbeatTimeoutRef.current) {
      clearTimeout(heartbeatTimeoutRef.current);
    }

    // Set a new timeout to detect missing heartbeats
    heartbeatTimeoutRef.current = setTimeout(() => {
      console.log("[SSE] Heartbeat timeout - no heartbeat received, reconnecting...");

      // Close the stale connection
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }

      // Schedule reconnection
      scheduleReconnect();
    }, heartbeatTimeoutMs);
  }, [scheduleReconnect]);

  const connect = useCallback(async () => {
    if (!enabled) return;

    // Don't create duplicate connections, including while a token request is still in flight
    if (eventSourceRef.current || tokenRequestRef.current) {
      return;
    }

    console.log("[SSE] Connecting to time log entry events...");

    const tokenRequest = new AbortController();
    tokenRequestRef.current = tokenRequest;

    try {
      // Generate a short-lived SSE token
      const { token } = await apiPost<{ token: string }>("/api-ui/time-log-entries/sse-token", {}, tokenRequest.signal);

      // The connection was disconnected while the token was being fetched - don't open a stale stream
      if (tokenRequest.signal.aborted) {
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

          // Reset heartbeat timeout on any message (data events count as activity)
          resetHeartbeatTimeout();
        } catch (error) {
          console.debug("[SSE] Failed to parse event data:", error);
        }
      };

      eventSource.addEventListener("heartbeat", () => {
        // Heartbeat event to keep connection alive
        console.debug("[SSE] Heartbeat received");

        // Reset the timeout since we received a heartbeat
        resetHeartbeatTimeout();
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

        // Start heartbeat monitoring when connection opens
        resetHeartbeatTimeout();
      };

      eventSourceRef.current = eventSource;
    } catch (error) {
      // The request was cancelled because we disconnected - nothing to recover from
      if (tokenRequest.signal.aborted) {
        return;
      }

      // Token fetch failed (e.g., server is down). This is a transient condition that is
      // recovered from by reconnecting, same as a dropped connection, so it is not an error.
      console.log("[SSE] Failed to fetch token, will retry:", error);

      // Schedule reconnection to try again later
      scheduleReconnect();
    } finally {
      if (tokenRequestRef.current === tokenRequest) {
        tokenRequestRef.current = null;
      }
    }
  }, [enabled, scheduleReconnect, resetHeartbeatTimeout]);

  const disconnect = useCallback(() => {
    // Clear any pending reconnect timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    // Clear heartbeat timeout
    if (heartbeatTimeoutRef.current) {
      clearTimeout(heartbeatTimeoutRef.current);
      heartbeatTimeoutRef.current = null;
    }

    // Cancel an in-flight token request so it neither opens a stale connection nor
    // reports a failure caused by the page going away
    if (tokenRequestRef.current) {
      tokenRequestRef.current.abort();
      tokenRequestRef.current = null;
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
