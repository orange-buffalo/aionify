import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormMessage } from "@/components/ui/form-message";
import { Eye, EyeOff, KeyRound } from "lucide-react";
import { apiGet, apiPost } from "@/lib/api";

interface ValidateTokenResponse {
  valid: boolean;
  userName: string | null;
  greeting: string | null;
}

interface SetPasswordResponse {
  message: string;
}

export function ActivateAccountPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [isValidating, setIsValidating] = useState(true);
  const [isValid, setIsValid] = useState(false);
  const [userName, setUserName] = useState<string | null>(null);
  const [greeting, setGreeting] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setValidationError(t("activation.noToken"));
      setIsValidating(false);
      return;
    }

    // Validate the token on page load
    const validateToken = async () => {
      try {
        const data = await apiGet<ValidateTokenResponse>(`/api-ui/activation/validate?token=${encodeURIComponent(token)}`);

        if (data.valid) {
          setIsValid(true);
          setUserName(data.userName);
          setGreeting(data.greeting);
        } else {
          setValidationError(t("activation.invalidToken"));
        }
      } catch (err) {
        setValidationError(err instanceof Error ? err.message : t("common.error"));
      } finally {
        setIsValidating(false);
      }
    };

    validateToken();
  }, [token, t]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Client-side validation
    if (!password) {
      setError(t("validation.newPasswordRequired"));
      return;
    }
    if (password.length > 50) {
      setError(t("validation.passwordTooLong"));
      return;
    }
    if (password !== confirmPassword) {
      setError(t("validation.passwordsDoNotMatch"));
      return;
    }

    setLoading(true);

    try {
      await apiPost<SetPasswordResponse>("/api-ui/activation/set-password", {
        token: token!,
        password,
      });

      // Success - redirect to login with a message
      sessionStorage.setItem("activationSuccess", "true");
      navigate("/login");
    } catch (err) {
      setError(err instanceof Error ? err.message : t("common.error"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="dark min-h-screen flex items-center justify-center p-4 login-gradient-bg"
      data-testid="activate-account-page"
    >
      <Card className="w-full max-w-md border-none shadow-md">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-bold" data-testid="activate-title">
            {t("activation.title")}
          </CardTitle>
          <CardDescription>{isValidating ? t("activation.validating") : t("activation.subtitle")}</CardDescription>
        </CardHeader>
        <CardContent>
          {isValidating ? (
            <div className="flex justify-center py-8" data-testid="validating-spinner">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
            </div>
          ) : validationError ? (
            <div data-testid="validation-error">
              <FormMessage type="error" message={validationError} testId="token-error" />
              <p className="mt-4 text-sm text-muted-foreground">{t("activation.contactAdmin")}</p>
            </div>
          ) : isValid ? (
            <form onSubmit={handleSubmit} className="space-y-4">
              {greeting && (
                <div className="p-3 bg-primary/10 rounded-md" data-testid="greeting-message">
                  <p className="text-sm text-foreground">{t("activation.welcome", { greeting })}</p>
                </div>
              )}

              {/* New Password */}
              <div className="space-y-2">
                <Label htmlFor="password" className="text-muted-foreground">
                  {t("activation.password")}
                </Label>
                <div className="relative">
                  <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder={t("activation.passwordPlaceholder")}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="pl-10 pr-10 bg-background/50"
                    data-testid="password-input"
                    autoComplete="new-password"
                    maxLength={50}
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

              {/* Confirm Password */}
              <div className="space-y-2">
                <Label htmlFor="confirmPassword" className="text-muted-foreground">
                  {t("activation.confirmPassword")}
                </Label>
                <div className="relative">
                  <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="confirmPassword"
                    type={showConfirmPassword ? "text" : "password"}
                    placeholder={t("activation.confirmPasswordPlaceholder")}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className="pl-10 pr-10 bg-background/50"
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
              {error && <FormMessage type="error" message={error} testId="set-password-error" />}

              {/* Submit Button */}
              <Button
                type="submit"
                className="w-full bg-teal-600 hover:bg-teal-700"
                disabled={loading}
                data-testid="set-password-button"
              >
                {loading ? t("activation.settingPassword") : t("activation.setPassword")}
              </Button>
            </form>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
