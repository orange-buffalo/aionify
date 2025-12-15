import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import { PortalLayout } from "@/components/layout/PortalLayout"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { FormMessage } from "@/components/ui/form-message"
import { apiPost } from "@/lib/api"
import { ArrowLeft } from "lucide-react"

interface CreateUserResponse {
  id: number
  userName: string
  greeting: string
  isAdmin: boolean
  activationToken: {
    token: string
    expiresAt: string
  }
}

export function CreateUserPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  
  const [userName, setUserName] = useState("")
  const [greeting, setGreeting] = useState("")
  const [isAdmin, setIsAdmin] = useState(false)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    // Client-side validation
    if (!userName || userName.trim() === "") {
      setError(t("validation.usernameBlank"))
      return
    }

    if (userName.length > 255) {
      setError(t("validation.usernameTooLong"))
      return
    }

    if (!greeting || greeting.trim() === "") {
      setError(t("validation.greetingBlank"))
      return
    }

    if (greeting.length > 255) {
      setError(t("validation.greetingTooLong"))
      return
    }

    setCreating(true)

    try {
      const response = await apiPost<CreateUserResponse>("/api/admin/users", {
        userName: userName.trim(),
        greeting: greeting.trim(),
        isAdmin
      })
      
      // Navigate to edit page with success message parameter
      navigate(`/admin/users/${response.id}`, {
        state: { 
          successMessage: t("portal.admin.users.create.createSuccess")
        }
      })
    } catch (err) {
      const errorCode = (err as any).errorCode
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`, { defaultValue: err instanceof Error ? err.message : "An error occurred" }))
      } else {
        setError(err instanceof Error ? err.message : "An error occurred")
      }
    } finally {
      setCreating(false)
    }
  }

  const handleBack = () => {
    navigate("/admin/users")
  }

  return (
    <PortalLayout testId="create-user-page">
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
              {t("portal.admin.users.create.back")}
            </Button>
            
            <h1 className="text-3xl font-bold text-foreground" data-testid="create-user-title">
              {t("portal.admin.users.create.title")}
            </h1>
            <p className="text-muted-foreground">{t("portal.admin.users.create.subtitle")}</p>
          </div>

          {error && (
            <div className="mb-4">
              <FormMessage type="error" message={error} testId="create-user-error" />
            </div>
          )}

          <div className="bg-card rounded-lg border p-6">
            <form onSubmit={handleSubmit} className="space-y-6">
              <div className="space-y-2">
                <Label htmlFor="userName" className="text-foreground">{t("portal.admin.users.create.username")}</Label>
                <Input
                  id="userName"
                  type="text"
                  value={userName}
                  onChange={(e) => setUserName(e.target.value)}
                  placeholder={t("portal.admin.users.create.usernamePlaceholder")}
                  className="text-foreground"
                  data-testid="username-input"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="greeting" className="text-foreground">{t("portal.admin.users.create.greeting")}</Label>
                <Input
                  id="greeting"
                  type="text"
                  value={greeting}
                  onChange={(e) => setGreeting(e.target.value)}
                  placeholder={t("portal.admin.users.create.greetingPlaceholder")}
                  className="text-foreground"
                  data-testid="greeting-input"
                />
              </div>

              <div className="space-y-2">
                <Label className="text-foreground">{t("portal.admin.users.create.userType")}</Label>
                <div className="flex gap-4">
                  <label className="flex items-center space-x-2 cursor-pointer">
                    <input
                      type="radio"
                      name="userType"
                      checked={!isAdmin}
                      onChange={() => setIsAdmin(false)}
                      data-testid="user-type-regular"
                    />
                    <span className="text-foreground">{t("portal.admin.users.create.regularUser")}</span>
                  </label>
                  <label className="flex items-center space-x-2 cursor-pointer">
                    <input
                      type="radio"
                      name="userType"
                      checked={isAdmin}
                      onChange={() => setIsAdmin(true)}
                      data-testid="user-type-admin"
                    />
                    <span className="text-foreground">{t("portal.admin.users.create.admin")}</span>
                  </label>
                </div>
              </div>

              <Button 
                type="submit" 
                disabled={creating}
                data-testid="create-button"
              >
                {creating ? t("portal.admin.users.create.creating") : t("portal.admin.users.create.create")}
              </Button>
            </form>
          </div>
        </div>
      </div>
    </PortalLayout>
  )
}
