import { useEffect, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Clock, Calendar, FileText } from "lucide-react"
import { TopNav, NavItem } from "@/components/navigation/TopNav"

const LAST_USERNAME_KEY = "aionify_last_username"

export function UserPortal() {
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

  const userMenuItems: NavItem[] = [
    { label: "Time Entry", icon: <Clock className="h-4 w-4 mr-2" /> },
    { label: "Calendar", icon: <Calendar className="h-4 w-4 mr-2" /> },
    { label: "Reports", icon: <FileText className="h-4 w-4 mr-2" /> },
  ]

  return (
    <div className="dark min-h-screen bg-background" data-testid="user-portal">
      <TopNav 
        menuItems={userMenuItems}
        userName={userInfo?.userName}
        greeting={userInfo?.greeting}
      />
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="user-title">Time Tracking</h1>
            <p className="text-muted-foreground">Track your time efficiently</p>
          </div>

          <div className="grid md:grid-cols-3 gap-6">
            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Clock className="h-5 w-5" />
                  Time Entry
                </CardTitle>
                <CardDescription>Log your time</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">Time entry coming soon...</p>
              </CardContent>
            </Card>

            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Calendar className="h-5 w-5" />
                  Calendar
                </CardTitle>
                <CardDescription>View your schedule</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">Calendar coming soon...</p>
              </CardContent>
            </Card>

            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <FileText className="h-5 w-5" />
                  Reports
                </CardTitle>
                <CardDescription>View your reports</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">Reports coming soon...</p>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  )
}
