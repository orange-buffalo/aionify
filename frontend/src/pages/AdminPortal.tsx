import { useTranslation } from "react-i18next"
import { Link } from "react-router"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
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
                  {t("portal.admin.users.title")}
                </CardTitle>
                <CardDescription>{t("portal.admin.users.description")}</CardDescription>
              </CardHeader>
              <CardContent>
                <Link to="/admin/users">
                  <Button variant="outline" size="sm" data-testid="admin-users-link">
                    {t("portal.admin.users.manage")}
                  </Button>
                </Link>
              </CardContent>
            </Card>

            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <BarChart className="h-5 w-5" />
                  {t("portal.admin.reports.title")}
                </CardTitle>
                <CardDescription>{t("portal.admin.reports.description")}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">{t("portal.admin.reports.comingSoon")}</p>
              </CardContent>
            </Card>

            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Settings className="h-5 w-5" />
                  {t("portal.admin.settings.title")}
                </CardTitle>
                <CardDescription>{t("portal.admin.settings.description")}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">{t("portal.admin.settings.comingSoon")}</p>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </PortalLayout>
  )
}
