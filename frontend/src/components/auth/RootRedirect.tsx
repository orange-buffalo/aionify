import { Navigate } from "react-router"
import { useAuth } from "@/hooks/useAuth"

/**
 * Component that handles the root path (/) redirect.
 * - If authenticated as admin: redirect to /admin
 * - If authenticated as regular user: redirect to /portal
 * - If not authenticated: redirect to /login
 */
export function RootRedirect() {
  const user = useAuth()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (user.isAdmin) {
    return <Navigate to="/admin" replace />
  }

  return <Navigate to="/portal" replace />
}
