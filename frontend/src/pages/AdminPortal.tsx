import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Settings, Users, BarChart } from "lucide-react"
import { PortalLayout } from "@/components/layout/PortalLayout"

export function AdminPortal() {
  const { t } = useTranslation()
  
  return (
    <PortalLayout testId="admin-portal">
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="admin-title">{t("portal.admin.title")}</h1>
            <p className="text-muted-foreground">{t("portal.admin.subtitle")}</p>
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
    </PortalLayout>
  )
}
