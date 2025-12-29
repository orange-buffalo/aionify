import { useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { FormMessage } from "@/components/ui/form-message";
import { Upload } from "lucide-react";

type ImportSource = "aionify" | "toggl" | "";

interface ImportSuccessResponse {
  imported: number;
  duplicates: number;
}

export function ImportDataPanel() {
  const { t } = useTranslation();
  const [source, setSource] = useState<ImportSource>("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] || null;
    setSelectedFile(file);
    setError(null);
    setSuccess(null);
  };

  const handleImport = async () => {
    if (!selectedFile) {
      return;
    }

    setIsImporting(true);
    setError(null);
    setSuccess(null);

    try {
      const fileContent = await selectedFile.text();

      // Use fetch directly to avoid apiRequest's default Content-Type handling
      const token = localStorage.getItem("aionify_token");
      const response = await fetch("/api/import/toggl", {
        method: "POST",
        headers: {
          "Content-Type": "text/plain",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: fileContent,
      });

      if (!response.ok) {
        const errorData = await response.json();
        const errorCode = errorData.errorCode;
        if (errorCode) {
          setError(t(`errorCodes.${errorCode}`));
        } else {
          setError(errorData.error || "An error occurred");
        }
        return;
      }

      const data: ImportSuccessResponse = await response.json();
      setSuccess(t("settings.import.importSuccess", { imported: data.imported, duplicates: data.duplicates }));
      setSelectedFile(null);
      // Reset file input using ref
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } catch (err: any) {
      const errorCode = err.errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || "An error occurred");
      }
    } finally {
      setIsImporting(false);
    }
  };

  const renderSourceSpecificContent = () => {
    if (source === "aionify") {
      return (
        <div className="text-sm text-muted-foreground" data-testid="aionify-not-implemented">
          {t("settings.import.notImplemented")}
        </div>
      );
    }

    if (source === "toggl") {
      return (
        <div className="space-y-4">
          {/* Instructions */}
          <div className="space-y-2">
            <div className="text-sm text-foreground prose prose-sm dark:prose-invert max-w-none">
              <p className="whitespace-pre-line" data-testid="toggl-instructions">
                Login to your Toggl account, go to Reports / Detailed, select Description, Tags and Time | Date columns,
                select the timeframe, click export.
              </p>
              <p className="text-muted-foreground text-xs mt-2">
                Note: Date and time will be interpreted in your current timezone (UTC).
              </p>
            </div>
          </div>

          {/* File Input */}
          <div className="space-y-2">
            <Label htmlFor="import-file-input" className="text-foreground">
              {t("settings.import.selectFile")}
            </Label>
            <div className="flex items-center gap-2">
              <label
                htmlFor="import-file-input"
                className="flex-1 flex items-center gap-2 px-4 py-2 border border-input rounded-md cursor-pointer hover:bg-accent transition-colors text-foreground"
                data-testid="file-input-label"
              >
                <Upload className="h-4 w-4" />
                <span className="text-sm truncate">{selectedFile ? selectedFile.name : "Choose a CSV file..."}</span>
              </label>
              <input
                id="import-file-input"
                ref={fileInputRef}
                type="file"
                accept=".csv,text/csv"
                onChange={handleFileChange}
                className="hidden"
                data-testid="import-file-input"
              />
            </div>
          </div>

          {/* Import Button */}
          <Button
            onClick={handleImport}
            disabled={!selectedFile || isImporting}
            data-testid="start-import-button"
            className="bg-teal-600 hover:bg-teal-700"
          >
            {isImporting ? t("settings.import.importing") : t("settings.import.startImport")}
          </Button>
        </div>
      );
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
        {error && (
          <div className="mb-4">
            <FormMessage type="error" message={error} testId="import-error" />
          </div>
        )}
        {success && (
          <div className="mb-4">
            <FormMessage type="success" message={success} testId="import-success" />
          </div>
        )}

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
