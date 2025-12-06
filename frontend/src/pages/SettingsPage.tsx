import { PortalLayout } from "@/components/layout/PortalLayout"
import { ProfilePanel } from "@/components/settings/ProfilePanel"
import { ChangePasswordPanel } from "@/components/settings/ChangePasswordPanel"

export function SettingsPage() {
  return (
    <PortalLayout testId="settings-page">
      <div className="p-8">
        <div className="max-w-4xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="settings-title">Settings</h1>
            <p className="text-muted-foreground">Manage your account settings</p>
          </div>

          <div className="space-y-6">
            <ProfilePanel />
            <ChangePasswordPanel />
          </div>
        </div>
      </div>
    </PortalLayout>
  )
}
