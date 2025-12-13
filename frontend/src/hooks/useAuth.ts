import { useState, useEffect } from "react"
import { TOKEN_KEY } from "@/lib/constants"

/**
 * Interface representing the current user's authentication state
 */
export interface AuthUser {
  token: string
  isAdmin: boolean
}

/**
 * Hook that checks if there's a valid JWT token and extracts user role information.
 * Note: This does NOT validate the token's signature or expiration - that's handled by the backend.
 * It only checks if a token exists and extracts basic claims for routing decisions.
 */
export function useAuth(): AuthUser | null {
  const [user, setUser] = useState<AuthUser | null>(null)

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY)
    
    if (!token) {
      setUser(null)
      return
    }

    try {
      // JWT format: header.payload.signature
      const parts = token.split(".")
      if (parts.length !== 3) {
        setUser(null)
        return
      }

      // Decode the payload (base64url encoded)
      const payload = JSON.parse(atob(parts[1]))
      
      // Check if token has expired (exp claim is in seconds since epoch)
      if (payload.exp && Date.now() >= payload.exp * 1000) {
        setUser(null)
        localStorage.removeItem(TOKEN_KEY)
        return
      }

      // Extract the isAdmin flag - Micronaut JWT uses roles array
      const roles: string[] = payload.roles || []
      const isAdmin = roles.includes("admin")

      setUser({
        token,
        isAdmin
      })
    } catch (e) {
      // Invalid token format
      setUser(null)
      localStorage.removeItem(TOKEN_KEY)
    }
  }, [])

  return user
}
