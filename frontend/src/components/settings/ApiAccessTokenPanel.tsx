import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiGet, apiPost, apiPut, apiRequest } from "@/lib/api";
import { Eye, Copy } from "lucide-react";
import { useApiExecutor } from "@/hooks/useApiExecutor";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface ApiTokenStatus {
  exists: boolean;
}

interface ApiTokenData {
  token: string;
}

export function ApiAccessTokenPanel() {
  const { t } = useTranslation();
  const { executeApiCall, apiCallInProgress, formMessage } = useApiExecutor("api-token");
  const [tokenExists, setTokenExists] = useState(false);
  const [tokenValue, setTokenValue] = useState<string | null>(null);
  const [isTokenVisible, setIsTokenVisible] = useState(false);
  const [initialDataLoaded, setInitialDataLoaded] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);

  const loadTokenStatus = async () => {
    await executeApiCall(async () => {
      const data = await apiGet<ApiTokenStatus>("/api-ui/users/api-token/status");
      setTokenExists(data.exists);
    });
    setInitialDataLoaded(true);
  };

  const loadTokenValue = async () => {
    await executeApiCall(async () => {
      const data = await apiGet<ApiTokenData>("/api-ui/users/api-token");
      setTokenValue(data.token);
      setIsTokenVisible(true);
    });
  };

  const handleGenerateToken = async () => {
    await executeApiCall(async () => {
      await apiPost("/api-ui/users/api-token", {});
      setTokenExists(true);
      return t("settings.apiToken.generateSuccess");
    });
  };

  const handleRegenerateToken = async () => {
    setTokenValue(null);
    setIsTokenVisible(false);

    await executeApiCall(async () => {
      await apiPut("/api-ui/users/api-token", {});
      return t("settings.apiToken.regenerateSuccess");
    });
  };

  const handleDeleteToken = async () => {
    setShowDeleteDialog(false);

    await executeApiCall(async () => {
      await apiRequest("/api-ui/users/api-token", {
        method: "DELETE",
      });
      // Only update state after successful API call
      setTokenExists(false);
      setTokenValue(null);
      setIsTokenVisible(false);
      return t("settings.apiToken.deleteSuccess");
    });
  };

  const handleCopyToken = async () => {
    if (tokenValue) {
      await executeApiCall(async () => {
        await navigator.clipboard.writeText(tokenValue);
        return t("settings.apiToken.tokenCopied");
      });
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

  return (
    <Card className="border-none shadow-md">
      <CardHeader>
        <CardTitle data-testid="api-token-title">{t("settings.apiToken.title")}</CardTitle>
        <CardDescription>
          {t("settings.apiToken.subtitle")}
          {" · "}
          <a
            href="/api/schema"
            target="_blank"
            rel="noopener noreferrer"
            className="text-teal-600 hover:test-teal-700 underline"
            data-testid="openapi-schema-link"
          >
            {t("settings.apiToken.openApiSchemaLink")}
          </a>
        </CardDescription>
      </CardHeader>
      <CardContent>
        {!initialDataLoaded ? (
          <div className="text-center py-8 text-foreground" data-testid="api-token-loading">
            {t("common.loading")}
          </div>
        ) : (
          <>
            <div className="mb-4">{formMessage}</div>

            {!tokenExists ? (
              <div className="space-y-4">
                <p className="text-foreground" data-testid="api-token-no-token-message">
                  {t("settings.apiToken.noToken")}
                </p>
                <Button
                  onClick={handleGenerateToken}
                  disabled={apiCallInProgress}
                  data-testid="generate-api-token-button"
                  className="bg-teal-600 hover:bg-teal-700"
                >
                  {apiCallInProgress ? t("settings.apiToken.generating") : t("settings.apiToken.generate")}
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
                <div className="flex gap-2">
                  <Button
                    onClick={handleRegenerateToken}
                    disabled={apiCallInProgress}
                    data-testid="regenerate-api-token-button"
                    className="bg-teal-600 hover:bg-teal-700"
                  >
                    {apiCallInProgress ? t("settings.apiToken.regenerating") : t("settings.apiToken.regenerate")}
                  </Button>
                  <Button
                    onClick={() => setShowDeleteDialog(true)}
                    disabled={apiCallInProgress}
                    data-testid="delete-api-token-button"
                    variant="destructive"
                  >
                    {t("settings.apiToken.delete")}
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </CardContent>

      <Dialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <DialogContent className="text-foreground">
          <DialogHeader>
            <DialogTitle className="text-foreground">{t("settings.apiToken.deleteConfirm.title")}</DialogTitle>
            <DialogDescription className="text-foreground">
              {t("settings.apiToken.deleteConfirm.message")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setShowDeleteDialog(false)}
              data-testid="cancel-delete-api-token-button"
              className="text-foreground"
            >
              {t("settings.apiToken.deleteConfirm.cancel")}
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteToken}
              disabled={apiCallInProgress}
              data-testid="confirm-delete-api-token-button"
            >
              {apiCallInProgress ? t("settings.apiToken.deleting") : t("settings.apiToken.deleteConfirm.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
