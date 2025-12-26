import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { FormMessage } from "@/components/ui/form-message"
import { KeyRound, Eye, EyeOff } from "lucide-react"
import { apiPost } from "@/lib/api"

interface ChangePasswordResponse {
  message: string
}

export function ChangePasswordPanel() {
  const { t } = useTranslation()
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [showCurrentPassword, setShowCurrentPassword] = useState(false)
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSuccess(null)

    // Client-side validation
    if (!currentPassword) {
      setError(t("validation.currentPasswordRequired"))
      return
    }
    if (!newPassword) {
      setError(t("validation.newPasswordRequired"))
      return
    }
    if (newPassword.length > 50) {
      setError(t("validation.passwordTooLong"))
      return
    }
    if (newPassword !== confirmPassword) {
      setError(t("validation.passwordsDoNotMatch"))
      return
    }

    setLoading(true)

    try {
      const data = await apiPost<ChangePasswordResponse>("/api/auth/change-password", {
        currentPassword,
        newPassword,
      })
      setSuccess(t("profile.changePassword.changeSuccess"))

      // Reset form on success
      setCurrentPassword("")
      setNewPassword("")
      setConfirmPassword("")
    } catch (err) {
      setError(err instanceof Error ? err.message : t("common.error"))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="border-none shadow-md">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <KeyRound className="h-5 w-5" />
          {t("profile.changePassword.title")}
        </CardTitle>
        <CardDescription>{t("profile.changePassword.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4 max-w-md">
          {/* Current Password */}
          <div className="space-y-2">
            <Label htmlFor="currentPassword" className="text-muted-foreground">
              {t("profile.changePassword.currentPassword")}
            </Label>
            <div className="relative">
              <Input
                id="currentPassword"
                type={showCurrentPassword ? "text" : "password"}
                placeholder={t("profile.changePassword.currentPasswordPlaceholder")}
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
              {t("profile.changePassword.newPassword")}
            </Label>
            <div className="relative">
              <Input
                id="newPassword"
                type={showNewPassword ? "text" : "password"}
                placeholder={t("profile.changePassword.newPasswordPlaceholder")}
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
              {t("profile.changePassword.confirmPassword")}
            </Label>
            <div className="relative">
              <Input
                id="confirmPassword"
                type={showConfirmPassword ? "text" : "password"}
                placeholder={t("profile.changePassword.confirmPasswordPlaceholder")}
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
            <FormMessage type="error" message={error} testId="change-password-error" />
          )}

          {/* Success Message */}
          {success && (
            <FormMessage type="success" message={success} testId="change-password-success" />
          )}

          {/* Submit Button */}
          <Button
            type="submit"
            className="bg-teal-600 hover:bg-teal-700"
            disabled={loading}
            data-testid="change-password-button"
          >
            {loading ? t("profile.changePassword.changing") : t("profile.changePassword.change")}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
