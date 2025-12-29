import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { AionifyImportPanel } from "./AionifyImportPanel";
import { TogglImportPanel } from "./TogglImportPanel";

type ImportSource = "aionify" | "toggl" | "";

export function ImportDataPanel() {
  const { t } = useTranslation();
  const [source, setSource] = useState<ImportSource>("");

  const renderSourceSpecificContent = () => {
    if (source === "aionify") {
      return <AionifyImportPanel />;
    }

    if (source === "toggl") {
      return <TogglImportPanel />;
    }

    return null;
  };

  return (
    <Card className="border-none shadow-md">
      <CardHeader>
        <CardTitle data-testid="import-title">{t("settings.import.title")}</CardTitle>
        <CardDescription>{t("settings.import.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {/* Source Selection */}
          <div className="space-y-2">
            <Label htmlFor="import-source" className="text-foreground">
              {t("settings.import.source")}
            </Label>
            <Select value={source} onValueChange={(value) => setSource(value as ImportSource)}>
              <SelectTrigger id="import-source" data-testid="import-source-select" className="text-foreground">
                <SelectValue placeholder={t("settings.import.sourcePlaceholder")} />
              </SelectTrigger>
              <SelectContent className="dark">
                <SelectItem value="aionify" data-testid="import-source-aionify">
                  {t("settings.import.aionifyExport")}
                </SelectItem>
                <SelectItem value="toggl" data-testid="import-source-toggl">
                  {t("settings.import.togglTimer")}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Source-specific content */}
          {renderSourceSpecificContent()}
        </div>
      </CardContent>
    </Card>
  );
}
