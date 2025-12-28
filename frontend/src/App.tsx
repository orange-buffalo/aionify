import { BrowserRouter, Routes, Route, Navigate } from "react-router";
import { LoginPage } from "@/pages/LoginPage";
import { ActivateAccountPage } from "@/pages/ActivateAccountPage";
import { SettingsPage } from "@/pages/SettingsPage";
import { ProfilePage } from "@/pages/ProfilePage";
import { UsersPage } from "@/pages/UsersPage";
import { CreateUserPage } from "@/pages/CreateUserPage";
import { EditUserPage } from "@/pages/EditUserPage";
import { TimeLogsPage } from "@/pages/TimeLogsPage";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";
import { RootRedirect } from "@/components/auth/RootRedirect";

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/activate" element={<ActivateAccountPage />} />
        <Route
          path="/admin/users"
          element={
            <ProtectedRoute requireAdmin={true}>
              <UsersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/users/create"
          element={
            <ProtectedRoute requireAdmin={true}>
              <CreateUserPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/users/:id"
          element={
            <ProtectedRoute requireAdmin={true}>
              <EditUserPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/profile"
          element={
            <ProtectedRoute requireAdmin={true}>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/portal/time-logs"
          element={
            <ProtectedRoute requireAdmin={false}>
              <TimeLogsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/portal/settings"
          element={
            <ProtectedRoute requireAdmin={false}>
              <SettingsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/portal/profile"
          element={
            <ProtectedRoute requireAdmin={false}>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route path="/" element={<RootRedirect />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
