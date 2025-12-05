import { useEffect, useState, ReactNode } from "react"
import { useLocation } from "react-router"
import { Settings, Users, BarChart, Clock, Calendar, FileText } from "lucide-react"
import { TopNav, NavItem } from "@/components/navigation/TopNav"
import { LAST_USERNAME_KEY } from "@/lib/constants"

interface PortalLayoutProps {
  children: ReactNode
  testId?: string
}

export function PortalLayout({ children, testId }: PortalLayoutProps) {
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
    { label: "Users", icon: <Users className="h-4 w-4 mr-2" /> },
    { label: "Reports", icon: <BarChart className="h-4 w-4 mr-2" /> },
    { label: "Settings", icon: <Settings className="h-4 w-4 mr-2" /> },
  ]

  const userMenuItems: NavItem[] = [
    { label: "Time Entry", icon: <Clock className="h-4 w-4 mr-2" /> },
    { label: "Calendar", icon: <Calendar className="h-4 w-4 mr-2" /> },
    { label: "Reports", icon: <FileText className="h-4 w-4 mr-2" /> },
  ]

  const menuItems = isAdmin ? adminMenuItems : userMenuItems

  return (
    <div className="dark min-h-screen bg-background" data-testid={testId}>
      <TopNav 
        menuItems={menuItems}
        userName={userInfo?.userName}
        greeting={userInfo?.greeting}
      />
      {children}
    </div>
  )
}
