import { useEffect, useState, ReactNode } from "react"
import { useLocation } from "react-router"
import { useTranslation } from "react-i18next"
import { Settings, Users, Clock } from "lucide-react"
import { TopNav, NavItem } from "@/components/navigation/TopNav"
import { LAST_USERNAME_KEY } from "@/lib/constants"

interface PortalLayoutProps {
  children: ReactNode
  testId?: string
}

export function PortalLayout({ children, testId }: PortalLayoutProps) {
  const { t } = useTranslation()
  const [userInfo, setUserInfo] = useState<{ userName: string; greeting: string } | null>(null)
  const location = useLocation()
  const isAdmin = location.pathname.startsWith("/admin")

  useEffect(() => {
    const stored = localStorage.getItem(LAST_USERNAME_KEY)
    if (stored) {
      try {
        setUserInfo(JSON.parse(stored))
      } catch {
        // Ignore parsing errors
      }
    }
  }, [])

  const adminMenuItems: NavItem[] = [
    { label: t("nav.users"), icon: <Users className="h-4 w-4 mr-2" />, href: "/admin/users" },
  ]

  const userMenuItems: NavItem[] = [
    { label: t("nav.timeEntry"), icon: <Clock className="h-4 w-4 mr-2" />, href: "/portal/time-logs" },
    { label: t("nav.settings"), icon: <Settings className="h-4 w-4 mr-2" />, href: "/portal/settings" },
  ]

  const menuItems = isAdmin ? adminMenuItems : userMenuItems

  return (
    <div className="dark min-h-screen" data-testid={testId}>
      <TopNav
        menuItems={menuItems}
        userName={userInfo?.userName}
        greeting={userInfo?.greeting}
      />
      {children}
    </div>
  )
}
