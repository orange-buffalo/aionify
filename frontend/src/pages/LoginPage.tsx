import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Eye, EyeOff, LogIn, User, KeyRound } from "lucide-react";
import { FormMessage } from "@/components/ui/form-message";
import { Logo } from "@/components/ui/logo";
import { LAST_USERNAME_KEY, TOKEN_KEY } from "@/lib/constants";
import { initializeLanguage, translateErrorCode } from "@/lib/i18n";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";

export function LoginPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isAutoLoggingIn, setIsAutoLoggingIn] = useState(true);
  const [lastUserGreeting, setLastUserGreeting] = useState<string | null>(null);
  const [showForgotPasswordDialog, setShowForgotPasswordDialog] = useState(false);

  // Common function to handle successful login (both manual and auto-login)
  const handleSuccessfulLogin = async (data: any) => {
    // Store token and user info
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(
      LAST_USERNAME_KEY,
      JSON.stringify({
        userName: data.userName,
        greeting: data.greeting,
      })
    );

    // Store user's preferred language and switch to it
    if (data.languageCode) {
      await initializeLanguage(data.languageCode);
    }

    // Redirect based on user role (Jackson serializes isAdmin as "admin")
    if (data.admin) {
      navigate("/admin/users");
    } else {
      navigate("/portal/time-logs");
    }
  };

  useEffect(() => {
    // Check if session expired (set by API request handler)
    const sessionExpired = sessionStorage.getItem("sessionExpired");
    if (sessionExpired) {
      setError(t("login.sessionExpired"));
      sessionStorage.removeItem("sessionExpired");
    }

    // Check if coming from successful activation
    const activationSuccess = sessionStorage.getItem("activationSuccess");
    if (activationSuccess) {
      setSuccess(t("login.activationSuccess"));
      sessionStorage.removeItem("activationSuccess");
    }

    // Load last user's credentials from localStorage (runs once on mount)
    const lastUsername = localStorage.getItem(LAST_USERNAME_KEY);
    if (lastUsername) {
      try {
        const parsed = JSON.parse(lastUsername);
        setUserName(parsed.userName);
        setLastUserGreeting(parsed.greeting);
      } catch {
        // If parsing fails, just use the raw value as username
        setUserName(lastUsername);
      }
    }

    // Try auto-login if remember me cookie exists
    const attemptAutoLogin = async () => {
      try {
        const response = await fetch("/api-ui/auth/auto-login", {
          method: "POST",
          credentials: "include",
          headers: {
            Accept: "application/json",
          },
        });

        if (response.ok) {
          const data = await response.json();
          await handleSuccessfulLogin(data);
        } else {
          // If auto-login fails (401), show login form
          setIsAutoLoggingIn(false);
        }
      } catch {
        // If auto-login fails, show login form
        setIsAutoLoggingIn(false);
      }
    };

    attemptAutoLogin();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Run once on mount

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      const response = await fetch("/api-ui/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ userName, password, rememberMe }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        // Translate error using error code if available
        throw new Error(translateErrorCode(errorData.errorCode));
      }

      const data = await response.json();

      await handleSuccessfulLogin(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("common.error"));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="dark min-h-screen flex items-center justify-center p-4 login-gradient-bg" data-testid="login-page">
      {/* Centered Login form */}
      <Card className="w-full max-w-md border-none shadow-md">
        <CardHeader className="space-y-1">
          <div className="flex justify-center mb-4">
            <Logo width={200} height={48} className="mb-2" />
          </div>
          <CardTitle className="text-2xl font-bold" data-testid="login-title">
            {t("login.title")}
          </CardTitle>
          <CardDescription>
            {isAutoLoggingIn ? (
              <span data-testid="auto-login-message">{t("login.autoLoggingIn")}</span>
            ) : lastUserGreeting ? (
              <span data-testid="welcome-back-message">{t("login.welcomeBack", { greeting: lastUserGreeting })}</span>
            ) : (
              t("login.signInPrompt")
            )}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="userName" className="text-foreground">
                {t("login.username")}
              </Label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="userName"
                  type="text"
                  placeholder={t("login.usernamePlaceholder")}
                  value={userName}
                  onChange={(e) => setUserName(e.target.value)}
                  className="pl-10 bg-background/50"
                  data-testid="username-input"
                  required
                  autoComplete="username"
                  disabled={isAutoLoggingIn}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="password" className="text-foreground">
                {t("login.password")}
              </Label>
              <div className="relative">
                <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder={t("login.passwordPlaceholder")}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="pl-10 pr-10 bg-background/50"
                  data-testid="password-input"
                  required
                  autoComplete="current-password"
                  disabled={isAutoLoggingIn}
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
              <div className="flex justify-between items-center">
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="rememberMe"
                    checked={rememberMe}
                    onCheckedChange={(checked) => setRememberMe(checked === true)}
                    data-testid="remember-me-checkbox"
                  />
                  <Label htmlFor="rememberMe" className="text-sm text-foreground cursor-pointer">
                    {t("login.rememberMe")}
                  </Label>
                </div>
                <button
                  type="button"
                  className="text-sm text-primary hover:underline text-foreground"
                  data-testid="lost-password-link"
                  onClick={() => setShowForgotPasswordDialog(true)}
                >
                  {t("login.lostPassword")}
                </button>
              </div>
            </div>

            {success && <FormMessage type="success" message={success} testId="login-success" />}

            {error && <FormMessage type="error" message={error} testId="login-error" />}

            <Button
              type="submit"
              className="w-full bg-teal-600 hover:bg-teal-700"
              disabled={isLoading || isAutoLoggingIn}
              data-testid="login-button"
            >
              {isLoading ? (
                t("login.signingIn")
              ) : (
                <>
                  {t("login.signIn")}
                  <LogIn className="ml-2 h-4 w-4" />
                </>
              )}
            </Button>
          </form>
        </CardContent>
      </Card>

      {/* Forgot Password Dialog */}
      <Dialog open={showForgotPasswordDialog} onOpenChange={setShowForgotPasswordDialog}>
        <DialogContent data-testid="forgot-password-dialog">
          <DialogHeader>
            <DialogTitle className="text-foreground">{t("login.lostPasswordDialog.title")}</DialogTitle>
            <DialogDescription className="text-foreground">{t("login.lostPasswordDialog.message")}</DialogDescription>
          </DialogHeader>
          <div className="flex justify-end">
            <Button
              onClick={() => setShowForgotPasswordDialog(false)}
              data-testid="forgot-password-dialog-close"
              variant="ghost"
            >
              {t("login.lostPasswordDialog.close")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
