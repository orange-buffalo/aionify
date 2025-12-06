import { useState, useEffect, useMemo } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { FormMessage } from "@/components/ui/form-message"
import { User, Loader2 } from "lucide-react"
import { apiGet, apiPut } from "@/lib/api"
import { initializeLanguage, translateErrorCode } from "@/lib/i18n"

// Available languages with native names
const LANGUAGES = [
  { code: "en", name: "English" },
  { code: "uk", name: "Ukrainian (Українська)" },
]

// Standard locales (BCP 47 language tags with region)
const LOCALES = [
  { code: "en-US", name: "English (United States)" },
  { code: "en-GB", name: "English (United Kingdom)" },
  { code: "en-AU", name: "English (Australia)" },
  { code: "en-CA", name: "English (Canada)" },
  { code: "uk-UA", name: "Ukrainian (Ukraine)" },
  { code: "de-DE", name: "German (Germany)" },
  { code: "de-AT", name: "German (Austria)" },
  { code: "de-CH", name: "German (Switzerland)" },
  { code: "fr-FR", name: "French (France)" },
  { code: "fr-CA", name: "French (Canada)" },
  { code: "fr-BE", name: "French (Belgium)" },
  { code: "fr-CH", name: "French (Switzerland)" },
  { code: "es-ES", name: "Spanish (Spain)" },
  { code: "es-MX", name: "Spanish (Mexico)" },
  { code: "es-AR", name: "Spanish (Argentina)" },
  { code: "it-IT", name: "Italian (Italy)" },
  { code: "it-CH", name: "Italian (Switzerland)" },
  { code: "pt-BR", name: "Portuguese (Brazil)" },
  { code: "pt-PT", name: "Portuguese (Portugal)" },
  { code: "nl-NL", name: "Dutch (Netherlands)" },
  { code: "nl-BE", name: "Dutch (Belgium)" },
  { code: "pl-PL", name: "Polish (Poland)" },
  { code: "ru-RU", name: "Russian (Russia)" },
  { code: "ja-JP", name: "Japanese (Japan)" },
  { code: "zh-CN", name: "Chinese (China)" },
  { code: "zh-TW", name: "Chinese (Taiwan)" },
  { code: "ko-KR", name: "Korean (South Korea)" },
]

interface ProfileResponse {
  userName: string
  greeting: string
  languageCode: string
  locale: string
}

interface ProfileUpdateResponse {
  message: string
}

function formatDateTimeExample(locale: string): string {
  try {
    const now = new Date()
    return new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "medium"
    }).format(now)
  } catch {
    return ""
  }
}

export function ProfilePanel() {
  const { t, i18n } = useTranslation()
  const [greeting, setGreeting] = useState("")
  const [language, setLanguage] = useState("")
  const [locale, setLocale] = useState("")
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  // Format example based on selected locale
  const localeExample = useMemo(() => {
    if (!locale) return ""
    return formatDateTimeExample(locale)
  }, [locale])

  // Load profile data on mount
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const data = await apiGet<ProfileResponse>("/api/users/profile")
        setGreeting(data.greeting)
        setLanguage(data.languageCode)
        setLocale(data.locale)
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load profile")
      } finally {
        setLoading(false)
      }
    }

    loadProfile()
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSuccess(null)

    // Client-side validation
    if (!greeting.trim()) {
      setError(t("validation.greetingBlank"))
      return
    }
    if (greeting.length > 255) {
      setError(t("validation.greetingTooLong"))
      return
    }
    if (!language) {
      setError(t("validation.languageRequired"))
      return
    }
    if (!locale) {
      setError(t("validation.localeRequired"))
      return
    }

    setSaving(true)

    try {
      await apiPut<ProfileUpdateResponse>("/api/users/profile", {
        greeting,
        languageCode: language,
        locale,
      })
      
      // Save language to localStorage and update i18n using centralized function
      await initializeLanguage(language)
      
      // Set success flag (translation will be applied in JSX with current language)
      setSuccess("success")
    } catch (err) {
      // Translate error using error code if available
      const errorMessage = err instanceof Error ? err.message : t("common.error")
      setError(errorMessage)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card className="bg-card/90">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <User className="h-5 w-5" />
          {t("settings.profile.title")}
        </CardTitle>
        <CardDescription>{t("settings.profile.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className="flex items-center gap-2 text-muted-foreground" data-testid="profile-loading">
            <Loader2 className="h-4 w-4 animate-spin" />
            {t("settings.profile.loading")}
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4 max-w-md">
            {/* Greeting */}
            <div className="space-y-2">
              <Label htmlFor="greeting" className="text-muted-foreground">
                {t("settings.profile.greeting")}
              </Label>
              <Input
                id="greeting"
                type="text"
                placeholder={t("settings.profile.greetingPlaceholder")}
                value={greeting}
                onChange={(e) => setGreeting(e.target.value)}
                className="bg-background/50"
                data-testid="profile-greeting-input"
                maxLength={255}
              />
            </div>

            {/* Language */}
            <div className="space-y-2">
              <Label htmlFor="language" className="text-muted-foreground">
                {t("settings.profile.language")}
              </Label>
              <Select value={language} onValueChange={setLanguage}>
                <SelectTrigger 
                  id="language" 
                  className="bg-background/50"
                  data-testid="profile-language-select"
                >
                  <SelectValue placeholder={t("settings.profile.languagePlaceholder")} />
                </SelectTrigger>
                <SelectContent data-testid="profile-language-dropdown">
                  {LANGUAGES.map((lang) => (
                    <SelectItem 
                      key={lang.code} 
                      value={lang.code}
                      data-testid={`language-option-${lang.code}`}
                    >
                      {t(`languages.${lang.code}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Locale */}
            <div className="space-y-2">
              <Label htmlFor="locale" className="text-muted-foreground">
                {t("settings.profile.locale")}
              </Label>
              <Select value={locale} onValueChange={setLocale}>
                <SelectTrigger 
                  id="locale" 
                  className="bg-background/50"
                  data-testid="profile-locale-select"
                >
                  <SelectValue placeholder={t("settings.profile.localePlaceholder")} />
                </SelectTrigger>
                <SelectContent data-testid="profile-locale-dropdown">
                  {LOCALES.map((loc) => (
                    <SelectItem 
                      key={loc.code} 
                      value={loc.code}
                      data-testid={`locale-option-${loc.code}`}
                    >
                      {loc.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {localeExample && (
                <p className="text-xs text-muted-foreground" data-testid="locale-example">
                  {t("settings.profile.localeExample", { example: localeExample })}
                </p>
              )}
            </div>

            {/* Error Message */}
            {error && (
              <FormMessage type="error" message={error} testId="profile-error" />
            )}

            {/* Success Message */}
            {success && (
              <FormMessage type="success" message={t("settings.profile.updateSuccess")} testId="profile-success" />
            )}

            {/* Submit Button */}
            <Button 
              type="submit" 
              className="bg-blue-600 hover:bg-blue-700"
              disabled={saving}
              data-testid="profile-save-button"
            >
              {saving ? t("settings.profile.saving") : t("settings.profile.save")}
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  )
}
