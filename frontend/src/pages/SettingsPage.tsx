import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { User, KeyRound, Eye, EyeOff, Check, X, Loader2 } from "lucide-react"
import { PortalLayout } from "@/components/layout/PortalLayout"
import { TOKEN_KEY } from "@/lib/constants"

// Available languages
const LANGUAGES = [
  { code: "en", name: "English" },
  { code: "uk", name: "Ukrainian" },
]

// Standard locales
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

export function SettingsPage() {
  // Profile form state
  const [profileGreeting, setProfileGreeting] = useState("")
  const [profileLanguage, setProfileLanguage] = useState("")
  const [profileLocale, setProfileLocale] = useState("")
  const [profileLoading, setProfileLoading] = useState(true)
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null)

  // Change password form state
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [showCurrentPassword, setShowCurrentPassword] = useState(false)
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  // Load profile data on mount
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const token = localStorage.getItem(TOKEN_KEY)
        const response = await fetch("/api/users/profile", {
          headers: {
            ...(token ? { "Authorization": `Bearer ${token}` } : {})
          }
        })

        if (!response.ok) {
          const errorData = await response.json()
          throw new Error(errorData.error || "Failed to load profile")
        }

        const data = await response.json()
        setProfileGreeting(data.greeting)
        setProfileLanguage(data.languageCode)
        setProfileLocale(data.locale)
      } catch (err) {
        setProfileError(err instanceof Error ? err.message : "Failed to load profile")
      } finally {
        setProfileLoading(false)
      }
    }

    loadProfile()
  }, [])

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    setProfileError(null)
    setProfileSuccess(null)

    // Client-side validation
    if (!profileGreeting.trim()) {
      setProfileError("Greeting cannot be blank")
      return
    }
    if (profileGreeting.length > 255) {
      setProfileError("Greeting cannot exceed 255 characters")
      return
    }
    if (!profileLanguage) {
      setProfileError("Language is required")
      return
    }
    if (!profileLocale) {
      setProfileError("Locale is required")
      return
    }

    setProfileSaving(true)

    try {
      const token = localStorage.getItem(TOKEN_KEY)
      const response = await fetch("/api/users/profile", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { "Authorization": `Bearer ${token}` } : {})
        },
        body: JSON.stringify({
          greeting: profileGreeting,
          languageCode: profileLanguage,
          locale: profileLocale,
        }),
      })

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.error || "Failed to update profile")
      }

      const data = await response.json()
      setProfileSuccess(data.message || "Profile updated successfully")
    } catch (err) {
      setProfileError(err instanceof Error ? err.message : "An error occurred")
    } finally {
      setProfileSaving(false)
    }
  }

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSuccess(null)

    // Client-side validation
    if (!currentPassword) {
      setError("Current password is required")
      return
    }
    if (!newPassword) {
      setError("New password is required")
      return
    }
    if (newPassword.length > 50) {
      setError("Password cannot exceed 50 characters")
      return
    }
    if (newPassword !== confirmPassword) {
      setError("New password and confirmation do not match")
      return
    }

    setIsLoading(true)

    try {
      const token = localStorage.getItem(TOKEN_KEY)
      const response = await fetch("/api/auth/change-password", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { "Authorization": `Bearer ${token}` } : {})
        },
        body: JSON.stringify({
          currentPassword,
          newPassword,
        }),
      })

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.error || "Failed to change password")
      }

      const data = await response.json()
      setSuccess(data.message || "Password changed successfully")
      
      // Reset form on success
      setCurrentPassword("")
      setNewPassword("")
      setConfirmPassword("")
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred")
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <PortalLayout testId="settings-page">
      <div className="p-8">
        <div className="max-w-4xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="settings-title">Settings</h1>
            <p className="text-muted-foreground">Manage your account settings</p>
          </div>

          <div className="space-y-6">
            {/* My Profile Panel */}
            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <User className="h-5 w-5" />
                  My Profile
                </CardTitle>
                <CardDescription>View and manage your profile information</CardDescription>
              </CardHeader>
              <CardContent>
                {profileLoading ? (
                  <div className="flex items-center gap-2 text-muted-foreground" data-testid="profile-loading">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Loading profile...
                  </div>
                ) : (
                  <form onSubmit={handleUpdateProfile} className="space-y-4 max-w-md">
                    {/* Greeting */}
                    <div className="space-y-2">
                      <Label htmlFor="greeting" className="text-muted-foreground">
                        Greeting
                      </Label>
                      <Input
                        id="greeting"
                        type="text"
                        placeholder="Enter your greeting"
                        value={profileGreeting}
                        onChange={(e) => setProfileGreeting(e.target.value)}
                        className="bg-background/50"
                        data-testid="profile-greeting-input"
                        maxLength={255}
                      />
                    </div>

                    {/* Language */}
                    <div className="space-y-2">
                      <Label htmlFor="language" className="text-muted-foreground">
                        Language
                      </Label>
                      <Select
                        value={profileLanguage}
                        onValueChange={setProfileLanguage}
                      >
                        <SelectTrigger 
                          id="language" 
                          className="bg-background/50"
                          data-testid="profile-language-select"
                        >
                          <SelectValue placeholder="Select language" />
                        </SelectTrigger>
                        <SelectContent data-testid="profile-language-dropdown">
                          {LANGUAGES.map((lang) => (
                            <SelectItem 
                              key={lang.code} 
                              value={lang.code}
                              data-testid={`language-option-${lang.code}`}
                            >
                              {lang.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    {/* Locale */}
                    <div className="space-y-2">
                      <Label htmlFor="locale" className="text-muted-foreground">
                        Locale
                      </Label>
                      <Select
                        value={profileLocale}
                        onValueChange={setProfileLocale}
                      >
                        <SelectTrigger 
                          id="locale" 
                          className="bg-background/50"
                          data-testid="profile-locale-select"
                        >
                          <SelectValue placeholder="Select locale" />
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
                    </div>

                    {/* Error Message */}
                    {profileError && (
                      <div className="flex items-center gap-2 p-3 text-sm text-destructive bg-destructive/10 rounded-md" data-testid="profile-error">
                        <X className="h-4 w-4 flex-shrink-0" />
                        {profileError}
                      </div>
                    )}

                    {/* Success Message */}
                    {profileSuccess && (
                      <div className="flex items-center gap-2 p-3 text-sm text-green-500 bg-green-500/10 rounded-md" data-testid="profile-success">
                        <Check className="h-4 w-4 flex-shrink-0" />
                        {profileSuccess}
                      </div>
                    )}

                    {/* Submit Button */}
                    <Button 
                      type="submit" 
                      className="bg-blue-600 hover:bg-blue-700"
                      disabled={profileSaving}
                      data-testid="profile-save-button"
                    >
                      {profileSaving ? "Saving..." : "Save Profile"}
                    </Button>
                  </form>
                )}
              </CardContent>
            </Card>

            {/* Change Password Panel */}
            <Card className="bg-card/90">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <KeyRound className="h-5 w-5" />
                  Change Password
                </CardTitle>
                <CardDescription>Update your account password</CardDescription>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleChangePassword} className="space-y-4 max-w-md">
                  {/* Current Password */}
                  <div className="space-y-2">
                    <Label htmlFor="currentPassword" className="text-muted-foreground">
                      Current Password
                    </Label>
                    <div className="relative">
                      <Input
                        id="currentPassword"
                        type={showCurrentPassword ? "text" : "password"}
                        placeholder="Enter current password"
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        className="pr-10 bg-background/50"
                        data-testid="current-password-input"
                        autoComplete="current-password"
                        maxLength={50}
                      />
                      <button
                        type="button"
                        onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                        data-testid="toggle-current-password-visibility"
                      >
                        {showCurrentPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>

                  {/* New Password */}
                  <div className="space-y-2">
                    <Label htmlFor="newPassword" className="text-muted-foreground">
                      New Password
                    </Label>
                    <div className="relative">
                      <Input
                        id="newPassword"
                        type={showNewPassword ? "text" : "password"}
                        placeholder="Enter new password"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        className="pr-10 bg-background/50"
                        data-testid="new-password-input"
                        autoComplete="new-password"
                        maxLength={50}
                      />
                      <button
                        type="button"
                        onClick={() => setShowNewPassword(!showNewPassword)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                        data-testid="toggle-new-password-visibility"
                      >
                        {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>

                  {/* Confirm New Password */}
                  <div className="space-y-2">
                    <Label htmlFor="confirmPassword" className="text-muted-foreground">
                      Confirm New Password
                    </Label>
                    <div className="relative">
                      <Input
                        id="confirmPassword"
                        type={showConfirmPassword ? "text" : "password"}
                        placeholder="Confirm new password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        className="pr-10 bg-background/50"
                        data-testid="confirm-password-input"
                        autoComplete="new-password"
                        maxLength={50}
                      />
                      <button
                        type="button"
                        onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                        data-testid="toggle-confirm-password-visibility"
                      >
                        {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>

                  {/* Error Message */}
                  {error && (
                    <div className="flex items-center gap-2 p-3 text-sm text-destructive bg-destructive/10 rounded-md" data-testid="change-password-error">
                      <X className="h-4 w-4 flex-shrink-0" />
                      {error}
                    </div>
                  )}

                  {/* Success Message */}
                  {success && (
                    <div className="flex items-center gap-2 p-3 text-sm text-green-500 bg-green-500/10 rounded-md" data-testid="change-password-success">
                      <Check className="h-4 w-4 flex-shrink-0" />
                      {success}
                    </div>
                  )}

                  {/* Submit Button */}
                  <Button 
                    type="submit" 
                    className="bg-blue-600 hover:bg-blue-700"
                    disabled={isLoading}
                    data-testid="change-password-button"
                  >
                    {isLoading ? "Saving..." : "Save Password"}
                  </Button>
                </form>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </PortalLayout>
  )
}
