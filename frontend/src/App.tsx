import { BrowserRouter, Routes, Route, Navigate } from "react-router"
import { LoginPage } from "@/pages/LoginPage"
import { ActivateAccountPage } from "@/pages/ActivateAccountPage"
import { AdminPortal } from "@/pages/AdminPortal"
import { UserPortal } from "@/pages/UserPortal"
import { SettingsPage } from "@/pages/SettingsPage"
import { UsersPage } from "@/pages/UsersPage"
import { EditUserPage } from "@/pages/EditUserPage"
import { ProtectedRoute } from "@/components/auth/ProtectedRoute"
import { RootRedirect } from "@/components/auth/RootRedirect"

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/activate" element={<ActivateAccountPage />} />
        <Route path="/admin" element={
          <ProtectedRoute requireAdmin={true}>
            <AdminPortal />
          </ProtectedRoute>
        } />
        <Route path="/admin/users" element={
          <ProtectedRoute requireAdmin={true}>
            <UsersPage />
          </ProtectedRoute>
        } />
        <Route path="/admin/users/:id" element={
          <ProtectedRoute requireAdmin={true}>
            <EditUserPage />
          </ProtectedRoute>
        } />
        <Route path="/admin/settings" element={
          <ProtectedRoute requireAdmin={true}>
            <SettingsPage />
          </ProtectedRoute>
        } />
        <Route path="/portal" element={
          <ProtectedRoute requireAdmin={false}>
            <UserPortal />
          </ProtectedRoute>
        } />
        <Route path="/portal/settings" element={
          <ProtectedRoute requireAdmin={false}>
            <SettingsPage />
          </ProtectedRoute>
        } />
        <Route path="/" element={<RootRedirect />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
