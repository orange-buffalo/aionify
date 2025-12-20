import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Clock, Calendar, FileText } from "lucide-react"
import { PortalLayout } from "@/components/layout/PortalLayout"

export function UserPortal() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  
  return (
    <PortalLayout testId="user-portal">
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="user-title">{t("portal.user.title")}</h1>
            <p className="text-muted-foreground">{t("portal.user.subtitle")}</p>
          </div>

          <div className="grid md:grid-cols-3 gap-6">
            <Card 
              className="bg-card/90 cursor-pointer hover:bg-card/95 transition-colors"
              onClick={() => navigate("/portal/time-logs")}
            >
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Clock className="h-5 w-5" />
                  {t("portal.user.timeEntry.title")}
                </CardTitle>
                <CardDescription>{t("portal.user.timeEntry.description")}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">{t("portal.user.timeEntry.comingSoon")}</p>
              </CardContent>
            </Card>

            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Calendar className="h-5 w-5" />
                  {t("portal.user.calendar.title")}
                </CardTitle>
                <CardDescription>{t("portal.user.calendar.description")}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">{t("portal.user.calendar.comingSoon")}</p>
              </CardContent>
            </Card>

            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <FileText className="h-5 w-5" />
                  {t("portal.user.reports.title")}
                </CardTitle>
                <CardDescription>{t("portal.user.reports.description")}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground text-sm">{t("portal.user.reports.comingSoon")}</p>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </PortalLayout>
  )
}
