import { useState, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FormMessage } from "@/components/ui/form-message";
import { User, Loader2 } from "lucide-react";
import { apiGet, apiPut } from "@/lib/api";
import { initializeLanguage, translateErrorCode } from "@/lib/i18n";

// Standard locales (BCP 47 language tags with region)
const LOCALES = [
  "en-US",
  "en-GB",
  "en-AU",
  "en-CA",
  "uk-UA",
  "de-DE",
  "de-AT",
  "de-CH",
  "fr-FR",
  "fr-CA",
  "fr-BE",
  "fr-CH",
  "es-ES",
  "es-MX",
  "es-AR",
  "it-IT",
  "it-CH",
  "pt-BR",
  "pt-PT",
  "nl-NL",
  "nl-BE",
  "pl-PL",
  "ru-RU",
  "ja-JP",
  "zh-CN",
  "zh-TW",
  "ko-KR",
];

interface ProfileResponse {
  userName: string;
  greeting: string;
  locale: string;
}

interface ProfileUpdateResponse {
  message: string;
}

function formatDateTimeExample(locale: string): string {
  try {
    const now = new Date();
    return new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "medium",
    }).format(now);
  } catch {
    return "";
  }
}

export function ProfilePanel() {
  const { t, i18n } = useTranslation();
  const [greeting, setGreeting] = useState("");
  const [locale, setLocale] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Format example based on selected locale
  const localeExample = useMemo(() => {
    if (!locale) return "";
    return formatDateTimeExample(locale);
  }, [locale]);

  // Load profile data on mount
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const data = await apiGet<ProfileResponse>("/api/users/profile");
        setGreeting(data.greeting);
        setLocale(data.locale);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load profile");
      } finally {
        setLoading(false);
      }
    };

    loadProfile();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    // Client-side validation
    if (!greeting.trim()) {
      setError(t("validation.greetingBlank"));
      return;
    }
    if (greeting.length > 255) {
      setError(t("validation.greetingTooLong"));
      return;
    }
    if (!locale) {
      setError(t("validation.localeRequired"));
      return;
    }

    setSaving(true);

    try {
      await apiPut<ProfileUpdateResponse>("/api/users/profile", {
        greeting,
        locale,
      });

      // Extract language from locale (e.g., "en" from "en-US")
      const language = locale.split("-")[0];

      // Update user language preference and apply changes
      await initializeLanguage(language);

      // Set success flag (translation will be applied in JSX with current language)
      setSuccess("success");
    } catch (err) {
      // Translate error using error code if available
      const errorMessage = err instanceof Error ? err.message : t("common.error");
      setError(errorMessage);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="border-none shadow-md">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <User className="h-5 w-5" />
          {t("profile.profile.title")}
        </CardTitle>
        <CardDescription>{t("profile.profile.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div
            className="flex items-center gap-2 text-muted-foreground"
            data-testid="profile-loading"
          >
            <Loader2 className="h-4 w-4 animate-spin" />
            {t("profile.profile.loading")}
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4 max-w-md">
            {/* Greeting */}
            <div className="space-y-2">
              <Label htmlFor="greeting" className="text-muted-foreground">
                {t("profile.profile.greeting")}
              </Label>
              <Input
                id="greeting"
                type="text"
                placeholder={t("profile.profile.greetingPlaceholder")}
                value={greeting}
                onChange={(e) => setGreeting(e.target.value)}
                className="bg-background/50"
                data-testid="profile-greeting-input"
                maxLength={255}
              />
            </div>

            {/* Language (shown as locale dropdown) */}
            <div className="space-y-2">
              <Label htmlFor="locale" className="text-muted-foreground">
                {t("profile.profile.language")}
              </Label>
              <Select value={locale} onValueChange={setLocale}>
                <SelectTrigger
                  id="locale"
                  className="bg-background/50"
                  data-testid="profile-locale-select"
                >
                  <SelectValue placeholder={t("profile.profile.languagePlaceholder")} />
                </SelectTrigger>
                <SelectContent data-testid="profile-locale-dropdown" className="dark">
                  {LOCALES.map((localeCode) => (
                    <SelectItem
                      key={localeCode}
                      value={localeCode}
                      data-testid={`locale-option-${localeCode}`}
                    >
                      {t(`locales.${localeCode}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {localeExample && (
                <p className="text-xs text-muted-foreground" data-testid="locale-example">
                  {t("profile.profile.localeExample", { example: localeExample })}
                </p>
              )}
            </div>

            {/* Error Message */}
            {error && <FormMessage type="error" message={error} testId="profile-error" />}

            {/* Success Message */}
            {success && (
              <FormMessage
                type="success"
                message={t("profile.profile.updateSuccess")}
                testId="profile-success"
              />
            )}

            {/* Submit Button */}
            <Button
              type="submit"
              className="bg-teal-600 hover:bg-teal-700"
              disabled={saving}
              data-testid="profile-save-button"
            >
              {saving ? t("profile.profile.saving") : t("profile.profile.save")}
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
