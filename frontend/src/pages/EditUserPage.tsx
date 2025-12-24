import { useState, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { useParams, useNavigate } from "react-router"
import { PortalLayout } from "@/components/layout/PortalLayout"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { FormMessage } from "@/components/ui/form-message"
import { apiGet, apiPut, apiPost } from "@/lib/api"
import { ArrowLeft } from "lucide-react"

interface ActivationTokenInfo {
  token: string
  expiresAt: string
}

interface UserDetail {
  id: number
  userName: string
  greeting: string
  isAdmin: boolean
  activationToken: ActivationTokenInfo | null
}

interface ActivationTokenResponse {
  token: string
  expiresAt: string
}

export function EditUserPage() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  
  const [user, setUser] = useState<UserDetail | null>(null)
  const [userName, setUserName] = useState("")
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [regenerating, setRegenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const loadUser = async () => {
    setLoading(true)
    setError(null)
    
    try {
      const data = await apiGet<UserDetail>(`/api/admin/users/${id}`)
      setUser(data)
      setUserName(data.userName)
    } catch (err) {
      const errorCode = (err as any).errorCode
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`, { defaultValue: err instanceof Error ? err.message : "An error occurred" }))
      } else {
        setError(err instanceof Error ? err.message : "An error occurred")
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadUser()
    
    // Check if we have a success message from session storage (e.g., after creating a user)
    const userCreated = sessionStorage.getItem("userCreated")
    if (userCreated) {
      setSuccessMessage(t("portal.admin.users.create.createSuccess"))
      sessionStorage.removeItem("userCreated")
    }
  }, [id])

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSuccessMessage(null)

    // Client-side validation
    if (!userName || userName.trim() === "") {
      setError(t("validation.usernameBlank"))
      return
    }

    if (userName.length > 255) {
      setError(t("validation.usernameTooLong"))
      return
    }

    setSaving(true)

    try {
      await apiPut(`/api/admin/users/${id}`, {
        userName: userName
      })
      
      setSuccessMessage(t("portal.admin.users.edit.updateSuccess"))
      // Reload user to get updated data
      await loadUser()
    } catch (err) {
      const errorCode = (err as any).errorCode
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`, { defaultValue: err instanceof Error ? err.message : "An error occurred" }))
      } else {
        setError(err instanceof Error ? err.message : "An error occurred")
      }
    } finally {
      setSaving(false)
    }
  }

  const handleRegenerateToken = async () => {
    setError(null)
    setSuccessMessage(null)
    setRegenerating(true)

    try {
      const response = await apiPost<ActivationTokenResponse>(
        `/api/admin/users/${id}/regenerate-activation-token`,
        {}
      )
      
      setSuccessMessage(t("portal.admin.users.edit.tokenRegenerated"))
      // Reload user to get updated activation token
      await loadUser()
    } catch (err) {
      const errorCode = (err as any).errorCode
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`, { defaultValue: err instanceof Error ? err.message : "An error occurred" }))
      } else {
        setError(err instanceof Error ? err.message : "An error occurred")
      }
    } finally {
      setRegenerating(false)
    }
  }

  const getActivationUrl = (token: string) => {
    return `${window.location.origin}/activate?token=${token}`
  }

  const handleBack = () => {
    navigate("/admin/users")
  }

  return (
    <PortalLayout testId="edit-user-page">
      <div className="p-8">
        <div className="max-w-2xl mx-auto">
          <div className="mb-8">
            <Button
              variant="ghost"
              size="sm"
              onClick={handleBack}
              className="mb-4 text-foreground"
              data-testid="back-button"
            >
              <ArrowLeft className="h-4 w-4 mr-2" />
              {t("portal.admin.users.edit.back")}
            </Button>
            
            <h1 className="text-3xl font-bold text-foreground" data-testid="edit-user-title">
              {t("portal.admin.users.edit.title")}
            </h1>
            <p className="text-muted-foreground">{t("portal.admin.users.edit.subtitle")}</p>
          </div>

          {error && (
            <div className="mb-4">
              <FormMessage type="error" message={error} testId="edit-user-error" />
            </div>
          )}

          {successMessage && (
            <div className="mb-4">
              <FormMessage type="success" message={successMessage} testId="edit-user-success" />
            </div>
          )}

          {loading ? (
            <div className="text-center py-8 text-foreground" data-testid="edit-user-loading">
              {t("common.loading")}
            </div>
          ) : user ? (
            <div className="space-y-8">
              {/* Username Form */}
              <div className="bg-card rounded-lg border p-6">
                <h2 className="text-xl font-semibold mb-4 text-foreground" data-testid="username-section-title">
                  {t("portal.admin.users.edit.usernameSection")}
                </h2>
                
                <form onSubmit={handleSave} className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="userName" className="text-foreground">{t("portal.admin.users.edit.username")}</Label>
                    <Input
                      id="userName"
                      type="text"
                      value={userName}
                      onChange={(e) => setUserName(e.target.value.trim())}
                      placeholder={t("portal.admin.users.edit.usernamePlaceholder")}
                      required
                      className="text-foreground"
                      data-testid="username-input"
                    />
                  </div>

                  <Button 
                    type="submit" 
                    disabled={saving || userName === user.userName}
                    data-testid="save-button"
                    className="bg-teal-600 hover:bg-teal-700"
                  >
                    {saving ? t("portal.admin.users.edit.saving") : t("portal.admin.users.edit.save")}
                  </Button>
                </form>
              </div>

              {/* User Info */}
              <div className="bg-card rounded-lg border p-6">
                <h2 className="text-xl font-semibold mb-4 text-foreground" data-testid="user-info-title">
                  {t("portal.admin.users.edit.userInfo")}
                </h2>
                
                <div className="space-y-3 text-sm">
                  <div>
                    <span className="text-muted-foreground">{t("portal.admin.users.edit.greeting")}:</span>
                    <span className="ml-2 text-foreground" data-testid="user-greeting">{user.greeting}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">{t("portal.admin.users.edit.userType")}:</span>
                    <span className="ml-2 text-foreground" data-testid="user-type">
                      {user.isAdmin ? t("portal.admin.users.table.admin") : t("portal.admin.users.table.regularUser")}
                    </span>
                  </div>
                </div>
                
                <div className="mt-4 p-3 bg-muted rounded text-sm" data-testid="user-info-note">
                  <p className="text-muted-foreground">
                    {t("portal.admin.users.edit.profileNote")}
                  </p>
                </div>
              </div>

              {/* Activation Token */}
              <div className="bg-card rounded-lg border p-6">
                <h2 className="text-xl font-semibold mb-4 text-foreground" data-testid="activation-section-title">
                  {t("portal.admin.users.edit.activationSection")}
                </h2>
                
                {user.activationToken ? (
                  <div className="space-y-4">
                    <div>
                      <Label htmlFor="activationUrl" className="text-foreground">{t("portal.admin.users.edit.activationUrl")}</Label>
                      <Input
                        id="activationUrl"
                        type="text"
                        value={getActivationUrl(user.activationToken.token)}
                        readOnly
                        className="font-mono text-xs text-foreground"
                        data-testid="activation-url"
                      />
                      <p className="text-xs text-muted-foreground mt-1" data-testid="activation-note">
                        {t("portal.admin.users.edit.activationNote")}
                      </p>
                    </div>
                    
                    <Button
                      onClick={handleRegenerateToken}
                      disabled={regenerating}
                      variant="outline"
                      data-testid="regenerate-token-button"
                    >
                      {regenerating ? t("portal.admin.users.edit.regenerating") : t("portal.admin.users.edit.regenerateToken")}
                    </Button>
                  </div>
                ) : (
                  <div className="space-y-4">
                    <p className="text-muted-foreground" data-testid="no-activation-token">
                      {t("portal.admin.users.edit.noActivationToken")}
                    </p>
                    
                    <Button
                      onClick={handleRegenerateToken}
                      disabled={regenerating}
                      variant="outline"
                      data-testid="generate-token-button"
                    >
                      {regenerating ? t("portal.admin.users.edit.generating") : t("portal.admin.users.edit.generateToken")}
                    </Button>
                  </div>
                )}
              </div>

              {/* Limitations */}
              <div className="bg-card rounded-lg border p-6">
                <h2 className="text-xl font-semibold mb-4 text-foreground" data-testid="limitations-title">
                  {t("portal.admin.users.edit.limitations")}
                </h2>
                
                <div className="space-y-2 text-sm text-muted-foreground" data-testid="limitations-list">
                  <p>• {t("portal.admin.users.edit.noTypeChange")}</p>
                  <p>• {t("portal.admin.users.edit.noPasswordChange")}</p>
                </div>
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </PortalLayout>
  )
}
