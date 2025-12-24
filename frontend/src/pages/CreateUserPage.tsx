import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import { PortalLayout } from "@/components/layout/PortalLayout"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
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
  const [userType, setUserType] = useState("regular")
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
        isAdmin: userType === "admin"
      })

      // Save success message to session storage
      sessionStorage.setItem("userCreated", "true")

      // Navigate to edit page
      navigate(`/admin/users/${response.id}`)
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

          <div className="bg-card rounded-lg shadow-md p-6">
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
                <Label htmlFor="userType" className="text-foreground">{t("portal.admin.users.create.userType")}</Label>
                <Select value={userType} onValueChange={setUserType}>
                  <SelectTrigger id="userType" className="text-foreground" data-testid="user-type-select">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="regular" data-testid="user-type-regular">
                      {t("portal.admin.users.create.regularUser")}
                    </SelectItem>
                    <SelectItem value="admin" data-testid="user-type-admin">
                      {t("portal.admin.users.create.admin")}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Button
                type="submit"
                disabled={creating}
                data-testid="create-button"
                className="bg-teal-600 hover:bg-teal-700"
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
