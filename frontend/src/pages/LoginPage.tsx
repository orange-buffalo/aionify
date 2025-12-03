import { useState, useEffect } from "react"
import { useNavigate } from "react-router"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Eye, EyeOff, LogIn, User, KeyRound } from "lucide-react"

const LAST_USERNAME_KEY = "aionify_last_username"

export function LoginPage() {
  const navigate = useNavigate()
  const [userName, setUserName] = useState("")
  const [password, setPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [lastUserGreeting, setLastUserGreeting] = useState<string | null>(null)

  useEffect(() => {
    const lastUsername = localStorage.getItem(LAST_USERNAME_KEY)
    if (lastUsername) {
      try {
        const parsed = JSON.parse(lastUsername)
        setUserName(parsed.userName)
        setLastUserGreeting(parsed.greeting)
      } catch {
        // If parsing fails, just use the raw value as username
        setUserName(lastUsername)
      }
    }
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setIsLoading(true)

    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ userName, password }),
      })

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.error || "Login failed")
      }

      const data = await response.json()
      
      // Store token and user info
      localStorage.setItem("aionify_token", data.token)
      localStorage.setItem(LAST_USERNAME_KEY, JSON.stringify({
        userName: data.userName,
        greeting: data.greeting
      }))

      // Redirect based on user role (Jackson serializes isAdmin as "admin")
      if (data.admin) {
        navigate("/admin")
      } else {
        navigate("/portal")
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred")
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="dark min-h-screen flex items-center justify-center p-4 login-gradient-bg" data-testid="login-page">
      {/* Centered Login form */}
      <Card className="w-full max-w-md bg-card/90 backdrop-blur-sm border-border/50">
          <CardHeader className="space-y-1">
            <CardTitle className="text-2xl font-bold" data-testid="login-title">Login</CardTitle>
            <CardDescription>
              {lastUserGreeting 
                ? <span data-testid="welcome-back-message">Welcome back, {lastUserGreeting}!</span>
                : "Sign in to your account to continue."
              }
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="userName" className="text-muted-foreground">
                  Username
                </Label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="userName"
                    type="text"
                    placeholder="Enter your username"
                    value={userName}
                    onChange={(e) => setUserName(e.target.value)}
                    className="pl-10 bg-background/50"
                    data-testid="username-input"
                    required
                    autoComplete="username"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="password" className="text-muted-foreground">
                    Password
                  </Label>
                  <button
                    type="button"
                    className="text-sm text-primary hover:underline"
                    data-testid="lost-password-link"
                    onClick={() => {/* Dummy - no action */}}
                  >
                    Lost password?
                  </button>
                </div>
                <div className="relative">
                  <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="pl-10 pr-10 bg-background/50"
                    data-testid="password-input"
                    required
                    autoComplete="current-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    data-testid="toggle-password-visibility"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {error && (
                <div className="p-3 text-sm text-destructive bg-destructive/10 rounded-md" data-testid="login-error">
                  {error}
                </div>
              )}

              <Button 
                type="submit" 
                className="w-full bg-blue-600 hover:bg-blue-700"
                disabled={isLoading}
                data-testid="login-button"
              >
                {isLoading ? (
                  "Signing in..."
                ) : (
                  <>
                    Sign in
                    <LogIn className="ml-2 h-4 w-4" />
                  </>
                )}
              </Button>
            </form>
          </CardContent>
        </Card>
    </div>
  )
}
