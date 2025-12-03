import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useNavigate } from "react-router"
import { LogOut, Settings, Users, BarChart } from "lucide-react"

export function AdminPortal() {
  const navigate = useNavigate()

  const handleLogout = () => {
    localStorage.removeItem("aionify_token")
    navigate("/login")
  }

  return (
    <div className="dark min-h-screen bg-background p-8" data-testid="admin-portal">
      <div className="max-w-6xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <div>
            <h1 className="text-3xl font-bold text-foreground" data-testid="admin-title">Admin Portal</h1>
            <p className="text-muted-foreground">Manage your Aionify instance</p>
          </div>
          <Button variant="outline" onClick={handleLogout} data-testid="logout-button">
            <LogOut className="mr-2 h-4 w-4" />
            Logout
          </Button>
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
  )
}
