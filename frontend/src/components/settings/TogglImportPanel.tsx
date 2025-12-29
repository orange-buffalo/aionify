import { useState, useRef, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { FormMessage } from "@/components/ui/form-message";
import { Upload } from "lucide-react";

interface ImportSuccessResponse {
  imported: number;
  duplicates: number;
}

export function TogglImportPanel() {
  const { t } = useTranslation();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [userTimezone, setUserTimezone] = useState<string>("");

  // Get user's timezone on mount
  useEffect(() => {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    setUserTimezone(tz);
  }, []);

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

      // Create multipart form data
      const formData = new FormData();
      formData.append("file", new Blob([fileContent], { type: "text/csv" }), selectedFile.name);
      formData.append(
        "metadata",
        new Blob([JSON.stringify({ timezone: userTimezone || "UTC" })], { type: "application/json" })
      );

      // Use fetch directly for multipart
      const token = localStorage.getItem("aionify_token");
      const response = await fetch("/api/import/toggl", {
        method: "POST",
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: formData,
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

  const instructions = t("settings.import.togglInstructions", { returnObjects: true }) as string[];

  return (
    <div className="space-y-4">
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

      {/* Instructions */}
      <div className="space-y-2">
        <div className="text-sm text-foreground prose prose-sm dark:prose-invert max-w-none">
          <ul className="list-disc pl-5 space-y-1" data-testid="toggl-instructions">
            {instructions.map((instruction, index) => (
              <li key={index}>{instruction}</li>
            ))}
          </ul>
          <p className="text-muted-foreground text-xs mt-2">
            {t("settings.import.timezoneNote", { timezone: userTimezone || "UTC" })}
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
