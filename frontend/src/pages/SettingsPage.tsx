import { useEffect, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Settings, Users, BarChart, Clock, Calendar, FileText, User, KeyRound, Eye, EyeOff, Check, X } from "lucide-react"
import { TopNav, NavItem } from "@/components/navigation/TopNav"
import { LAST_USERNAME_KEY } from "@/lib/constants"
import { useLocation } from "react-router"

export function SettingsPage() {
  const [userInfo, setUserInfo] = useState<{ userName: string; greeting: string } | null>(null)
  const location = useLocation()
  const isAdminSettings = location.pathname.startsWith("/admin")

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

  useEffect(() => {
    const stored = localStorage.getItem(LAST_USERNAME_KEY)
    if (stored) {
      try {
        setUserInfo(JSON.parse(stored))
      } catch {
        // Ignore parsing errors
      }
    }
  }, [])

  const adminMenuItems: NavItem[] = [
    { label: "Users", icon: <Users className="h-4 w-4 mr-2" /> },
    { label: "Reports", icon: <BarChart className="h-4 w-4 mr-2" /> },
    { label: "Settings", icon: <Settings className="h-4 w-4 mr-2" /> },
  ]

  const userMenuItems: NavItem[] = [
    { label: "Time Entry", icon: <Clock className="h-4 w-4 mr-2" /> },
    { label: "Calendar", icon: <Calendar className="h-4 w-4 mr-2" /> },
    { label: "Reports", icon: <FileText className="h-4 w-4 mr-2" /> },
  ]

  const menuItems = isAdminSettings ? adminMenuItems : userMenuItems

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

    if (!userInfo?.userName) {
      setError("User information not available")
      return
    }

    setIsLoading(true)

    try {
      const response = await fetch("/api/auth/change-password", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          userName: userInfo.userName,
          currentPassword,
          newPassword,
          confirmPassword,
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
    <div className="dark min-h-screen bg-background" data-testid="settings-page">
      <TopNav 
        menuItems={menuItems}
        userName={userInfo?.userName}
        greeting={userInfo?.greeting}
      />
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
                <p className="text-muted-foreground text-sm" data-testid="profile-placeholder">
                  Profile management coming soon...
                </p>
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
    </div>
  )
}
