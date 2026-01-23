import { useTranslation } from "react-i18next";
import { PortalLayout } from "@/components/layout/PortalLayout";
import { ProfilePanel } from "@/components/settings/ProfilePanel";
import { ChangePasswordPanel } from "@/components/settings/ChangePasswordPanel";

export function ProfilePage() {
  const { t } = useTranslation();

  return (
    <PortalLayout testId="profile-page">
      <div className="p-8 pt-16">
        <div className="max-w-4xl mx-auto">
          <div className="space-y-6">
            <ProfilePanel />
            <ChangePasswordPanel />
          </div>
        </div>
      </div>
    </PortalLayout>
  );
}
