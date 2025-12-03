import { BrowserRouter, Routes, Route, Navigate } from "react-router"
import { LoginPage } from "@/pages/LoginPage"
import { AdminPortal } from "@/pages/AdminPortal"
import { UserPortal } from "@/pages/UserPortal"

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/admin" element={<AdminPortal />} />
        <Route path="/portal" element={<UserPortal />} />
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
