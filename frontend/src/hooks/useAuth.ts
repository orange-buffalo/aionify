import { TOKEN_KEY } from "@/lib/constants"

/**
 * Interface representing the current user's authentication state
 */
export interface AuthUser {
  token: string
  isAdmin: boolean
}

/**
 * Checks the authentication state synchronously from localStorage.
 * This is called during render to make routing decisions immediately.
 */
function getAuthUser(): AuthUser | null {
  const token = localStorage.getItem(TOKEN_KEY)
  
  if (!token) {
    return null
  }

  try {
    // JWT format: header.payload.signature
    const parts = token.split(".")
    if (parts.length !== 3) {
      localStorage.removeItem(TOKEN_KEY)
      return null
    }

    // Decode the payload (base64url encoded)
    // JWT uses base64url encoding which replaces + with - and / with _
    // Also, padding with = may be omitted
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    // Add padding if needed
    while (base64.length % 4) {
      base64 += '='
    }
    const payload = JSON.parse(atob(base64))
    
    // Check if token has expired (exp claim is in seconds since epoch)
    if (payload.exp && Date.now() >= payload.exp * 1000) {
      localStorage.removeItem(TOKEN_KEY)
      return null
    }

    // Extract the isAdmin flag - Micronaut JWT uses roles array
    const roles: string[] = payload.roles || []
    const isAdmin = roles.includes("admin")

    return {
      token,
      isAdmin
    }
  } catch (e) {
    // Invalid token format
    localStorage.removeItem(TOKEN_KEY)
    return null
  }
}

/**
 * Hook that checks if there's a valid JWT token and extracts user role information.
 * Note: This does NOT validate the token's signature or expiration - that's handled by the backend.
 * It only checks if a token exists and extracts basic claims for routing decisions.
 * 
 * This hook performs a synchronous check to avoid race conditions during initial render.
 */
export function useAuth(): AuthUser | null {
  return getAuthUser()
}
