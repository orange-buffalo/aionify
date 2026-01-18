import { TOKEN_KEY } from "./constants";

/**
 * Decoded JWT payload
 */
interface JwtPayload {
  exp?: number; // Expiration time in seconds since epoch
  roles?: string[];
  [key: string]: unknown;
}

/**
 * Decodes a JWT token and returns the payload.
 * @param token JWT token string
 * @returns Decoded payload or null if invalid
 */
export function decodeJwt(token: string): JwtPayload | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) {
      return null;
    }

    // Decode the payload (base64url encoded)
    let base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    while (base64.length % 4) {
      base64 += "=";
    }
    return JSON.parse(atob(base64));
  } catch (e) {
    return null;
  }
}

/**
 * Gets the token expiration time in milliseconds since epoch.
 * @param token JWT token string
 * @returns Expiration time in milliseconds or null if not available
 */
export function getTokenExpiration(token: string): number | null {
  const payload = decodeJwt(token);
  if (!payload || !payload.exp) {
    return null;
  }
  // JWT exp is in seconds, convert to milliseconds
  return payload.exp * 1000;
}

/**
 * Checks if a token is close to expiration (within 5 minutes).
 * @param token JWT token string
 * @returns true if token expires within 5 minutes, false otherwise
 */
export function isTokenCloseToExpiration(token: string): boolean {
  const expiration = getTokenExpiration(token);
  if (!expiration) {
    return false;
  }

  const now = Date.now();
  const fiveMinutes = 5 * 60 * 1000;
  return expiration - now <= fiveMinutes;
}

/**
 * Refreshes the current token by calling the refresh endpoint.
 * Updates localStorage with the new token on success.
 * @returns true if refresh was successful, false otherwise
 */
export async function refreshToken(): Promise<boolean> {
  const currentToken = localStorage.getItem(TOKEN_KEY);
  if (!currentToken) {
    return false;
  }

  try {
    const response = await fetch("/api-ui/auth/refresh", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${currentToken}`,
      },
    });

    if (!response.ok) {
      return false;
    }

    const data = await response.json();
    if (data.token) {
      localStorage.setItem(TOKEN_KEY, data.token);
      return true;
    }

    return false;
  } catch (error) {
    console.error("Token refresh failed:", error);
    return false;
  }
}
