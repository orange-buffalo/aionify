import { ReactNode } from "react";
import { Navigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";

interface ProtectedRouteProps {
  children: ReactNode;
  requireAdmin?: boolean;
}

/**
 * Wrapper component that protects routes requiring authentication.
 * Redirects to login if not authenticated.
 * Redirects to appropriate portal if user role doesn't match required role.
 */
export function ProtectedRoute({ children, requireAdmin = false }: ProtectedRouteProps) {
  const user = useAuth();

  // Not authenticated - redirect to login
  if (!user) {
    return <Navigate to="/login" replace />;
  }

  // Authenticated but wrong role - redirect to appropriate portal
  if (requireAdmin && !user.isAdmin) {
    // Regular user trying to access admin page - redirect to time logs page
    return <Navigate to="/portal/time-logs" replace />;
  }

  if (!requireAdmin && user.isAdmin) {
    // Admin trying to access user portal - redirect to admin users page
    return <Navigate to="/admin/users" replace />;
  }

  // Authenticated with correct role - render the protected content
  return <>{children}</>;
}
