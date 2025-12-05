import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Clock, Calendar, FileText } from "lucide-react"
import { PortalLayout } from "@/components/layout/PortalLayout"

export function UserPortal() {
  return (
    <PortalLayout testId="user-portal">
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
    </PortalLayout>
  )
}
