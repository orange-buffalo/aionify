import { useTranslation } from "react-i18next";

export function AionifyImportPanel() {
  const { t } = useTranslation();

  return (
    <div className="text-sm text-muted-foreground" data-testid="aionify-not-implemented">
      {t("settings.import.notImplemented")}
    </div>
  );
}
