import { useEffect } from "react";
import { TOKEN_KEY } from "@/lib/constants";
import { isTokenCloseToExpiration, refreshToken } from "@/lib/token";

/**
 * Hook that automatically refreshes the JWT token when it's close to expiration.
 * Checks every minute if the token needs refreshing (within 5 minutes of expiration).
 * This provides a background token refresh mechanism in addition to request-time refresh.
 */
export function useTokenRefresh() {
  useEffect(() => {
    const checkAndRefresh = async () => {
      const token = localStorage.getItem(TOKEN_KEY);
      if (token && isTokenCloseToExpiration(token)) {
        await refreshToken();
      }
    };

    // Check immediately on mount
    checkAndRefresh();

    // Check every minute
    const interval = setInterval(checkAndRefresh, 60 * 1000);

    return () => clearInterval(interval);
  }, []);
}
