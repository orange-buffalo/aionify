import { useTranslation } from "react-i18next"
import { PortalLayout } from "@/components/layout/PortalLayout"
import { ProfilePanel } from "@/components/settings/ProfilePanel"
import { ChangePasswordPanel } from "@/components/settings/ChangePasswordPanel"
import { TagsPanel } from "@/components/settings/TagsPanel"

export function SettingsPage() {
  const { t } = useTranslation()
  
  return (
    <PortalLayout testId="settings-page">
      <div className="p-8">
        <div className="max-w-4xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="settings-title">{t("settings.title")}</h1>
            <p className="text-muted-foreground">{t("settings.subtitle")}</p>
          </div>

          <div className="space-y-6">
            <ProfilePanel />
            <ChangePasswordPanel />
            <TagsPanel />
          </div>
        </div>
      </div>
    </PortalLayout>
  )
}
