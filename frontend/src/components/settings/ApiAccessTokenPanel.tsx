import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormMessage } from "@/components/ui/form-message";
import { apiGet, apiPost, apiPut } from "@/lib/api";
import { Eye, Copy } from "lucide-react";

interface ApiTokenStatus {
  exists: boolean;
}

interface ApiTokenData {
  token: string;
}

export function ApiAccessTokenPanel() {
  const { t } = useTranslation();
  const [tokenExists, setTokenExists] = useState(false);
  const [tokenValue, setTokenValue] = useState<string | null>(null);
  const [isTokenVisible, setIsTokenVisible] = useState(false);
  const [loading, setLoading] = useState(true);
  const [actionInProgress, setActionInProgress] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const loadTokenStatus = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await apiGet<ApiTokenStatus>("/api/users/api-token/status");
      setTokenExists(data.exists);
    } catch (err: any) {
      const errorCode = err.errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || "An error occurred");
      }
    } finally {
      setLoading(false);
    }
  };

  const loadTokenValue = async () => {
    setError(null);

    try {
      const data = await apiGet<ApiTokenData>("/api/users/api-token");
      setTokenValue(data.token);
      setIsTokenVisible(true);
    } catch (err: any) {
      const errorCode = err.errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || "An error occurred");
      }
    }
  };

  const handleGenerateToken = async () => {
    setActionInProgress(true);
    setError(null);
    setSuccess(null);

    try {
      await apiPost("/api/users/api-token", {});
      setSuccess(t("settings.apiToken.generateSuccess"));
      setTokenExists(true);
    } catch (err: any) {
      const errorCode = err.errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || "An error occurred");
      }
    } finally {
      setActionInProgress(false);
    }
  };

  const handleRegenerateToken = async () => {
    setActionInProgress(true);
    setError(null);
    setSuccess(null);
    setTokenValue(null);
    setIsTokenVisible(false);

    try {
      await apiPut("/api/users/api-token", {});
      setSuccess(t("settings.apiToken.regenerateSuccess"));
    } catch (err: any) {
      const errorCode = err.errorCode;
      if (errorCode) {
        setError(t(`errorCodes.${errorCode}`));
      } else {
        setError(err.message || "An error occurred");
      }
    } finally {
      setActionInProgress(false);
    }
  };

  const handleCopyToken = async () => {
    if (tokenValue) {
      try {
        await navigator.clipboard.writeText(tokenValue);
        setSuccess(t("settings.apiToken.tokenCopied"));
      } catch (err) {
        setError("Failed to copy token to clipboard");
      }
    }
  };

  const handleToggleTokenVisibility = () => {
    if (!isTokenVisible) {
      loadTokenValue();
    } else {
      setIsTokenVisible(false);
      setTokenValue(null);
    }
  };

  useEffect(() => {
    loadTokenStatus();
  }, []);

  if (loading) {
    return (
      <Card className="border-none shadow-md">
        <CardHeader>
          <CardTitle data-testid="api-token-title">{t("settings.apiToken.title")}</CardTitle>
          <CardDescription>{t("settings.apiToken.subtitle")}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="text-center py-8 text-foreground" data-testid="api-token-loading">
            {t("common.loading")}
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-none shadow-md">
      <CardHeader>
        <CardTitle data-testid="api-token-title">{t("settings.apiToken.title")}</CardTitle>
        <CardDescription>{t("settings.apiToken.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        {error && (
          <div className="mb-4">
            <FormMessage type="error" message={error} testId="api-token-error" />
          </div>
        )}
        {success && (
          <div className="mb-4">
            <FormMessage type="success" message={success} testId="api-token-success" />
          </div>
        )}

        {!tokenExists ? (
          <div className="space-y-4">
            <p className="text-foreground" data-testid="api-token-no-token-message">
              {t("settings.apiToken.noToken")}
            </p>
            <Button
              onClick={handleGenerateToken}
              disabled={actionInProgress}
              data-testid="generate-api-token-button"
              className="bg-teal-600 hover:bg-teal-700"
            >
              {actionInProgress ? t("settings.apiToken.generating") : t("settings.apiToken.generate")}
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="api-token-input" className="text-foreground">
                {t("settings.apiToken.tokenLabel")}
              </Label>
              <div className="flex gap-2">
                <Input
                  id="api-token-input"
                  type={isTokenVisible && tokenValue ? "text" : "password"}
                  value={isTokenVisible && tokenValue ? tokenValue : "••••••••••••••••••••••••••••••••"}
                  readOnly
                  data-testid="api-token-input"
                  className="text-foreground"
                />
                {!isTokenVisible || !tokenValue ? (
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={handleToggleTokenVisibility}
                    data-testid="show-api-token-button"
                    className="shrink-0"
                  >
                    <Eye className="h-4 w-4" />
                  </Button>
                ) : (
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={handleCopyToken}
                    data-testid="copy-api-token-button"
                    className="shrink-0"
                  >
                    <Copy className="h-4 w-4" />
                  </Button>
                )}
              </div>
            </div>
            <Button
              onClick={handleRegenerateToken}
              disabled={actionInProgress}
              data-testid="regenerate-api-token-button"
              className="bg-teal-600 hover:bg-teal-700"
            >
              {actionInProgress ? t("settings.apiToken.regenerating") : t("settings.apiToken.regenerate")}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
