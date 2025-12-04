import { useEffect, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Settings, Users, BarChart } from "lucide-react"
import { TopNav, NavItem } from "@/components/navigation/TopNav"

const LAST_USERNAME_KEY = "aionify_last_username"

export function AdminPortal() {
  const [userInfo, setUserInfo] = useState<{ userName: string; greeting: string } | null>(null)

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

  return (
    <div className="dark min-h-screen bg-background" data-testid="admin-portal">
      <TopNav 
        menuItems={adminMenuItems}
        userName={userInfo?.userName}
        greeting={userInfo?.greeting}
      />
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="admin-title">Admin Portal</h1>
            <p className="text-muted-foreground">Manage your Aionify instance</p>
          </div>

          <div className="grid md:grid-cols-3 gap-6">
            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Users className="h-5 w-5" />
                  Users
                </CardTitle>
                <CardDescription>Manage user accounts</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">User management coming soon...</p>
              </CardContent>
            </Card>

            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <BarChart className="h-5 w-5" />
                  Reports
                </CardTitle>
                <CardDescription>View system reports</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">Reports coming soon...</p>
              </CardContent>
            </Card>

            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Settings className="h-5 w-5" />
                  Settings
                </CardTitle>
                <CardDescription>System configuration</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">Settings coming soon...</p>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  )
}
