import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader } from "@/components/ui/loader";
import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/api";
import { Eye, Copy } from "lucide-react";
import { useApiExecutor } from "@/hooks/useApiExecutor";
import { ConfirmationDialog } from "@/components/ui/confirmation-dialog";
import { copyToClipboard } from "@/lib/utils";
import { formatDate } from "@/lib/date-format";

interface ApiTokenSummary {
  id: number;
  name: string;
  createdAt: string;
}

interface ApiTokensResponse {
  tokens: ApiTokenSummary[];
}

interface ApiTokenValue {
  token: string;
}

const MASKED_TOKEN = "••••••••••••••••••••••••••••••••";
const MAX_TOKEN_NAME_LENGTH = 100;

export function ApiAccessTokenPanel() {
  const { t, i18n } = useTranslation();
  const { executeApiCall, apiCallInProgress, formMessage } = useApiExecutor("api-token");
  const [tokens, setTokens] = useState<ApiTokenSummary[]>([]);
  const [revealedTokenValues, setRevealedTokenValues] = useState<Record<number, string>>({});
  const [newTokenName, setNewTokenName] = useState("");
  const [initialDataLoaded, setInitialDataLoaded] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [tokenPendingDeletion, setTokenPendingDeletion] = useState<ApiTokenSummary | null>(null);

  const loadTokens = async () => {
    const data = await apiGet<ApiTokensResponse>("/api-ui/users/api-tokens");
    // Empty collections are omitted from API responses
    setTokens(data.tokens ?? []);
  };

  const hideTokenValue = (tokenId: number) => {
    setRevealedTokenValues(({ [tokenId]: _hidden, ...otherValues }) => otherValues);
  };

  const handleCreateToken = async () => {
    const name = newTokenName.trim();
    if (!name) return;

    await executeApiCall(async () => {
      await apiPost("/api-ui/users/api-tokens", { name });
      await loadTokens();
      setNewTokenName("");
      return t("settings.apiToken.createSuccess", { name });
    });
  };

  const handleRevealToken = async (token: ApiTokenSummary) => {
    await executeApiCall(async () => {
      const data = await apiGet<ApiTokenValue>(`/api-ui/users/api-tokens/${token.id}/value`);
      setRevealedTokenValues((values) => ({ ...values, [token.id]: data.token }));
    });
  };

  const handleCopyToken = async (tokenValue: string) => {
    await executeApiCall(async () => {
      await copyToClipboard(tokenValue);
      return t("settings.apiToken.tokenCopied");
    });
  };

  const handleRegenerateToken = async (token: ApiTokenSummary) => {
    hideTokenValue(token.id);

    await executeApiCall(async () => {
      await apiPut(`/api-ui/users/api-tokens/${token.id}`, {});
      return t("settings.apiToken.regenerateSuccess", { name: token.name });
    });
  };

  const handleRequestDeletion = (token: ApiTokenSummary) => {
    setTokenPendingDeletion(token);
    setShowDeleteDialog(true);
  };

  const handleDeleteToken = async () => {
    setShowDeleteDialog(false);
    const token = tokenPendingDeletion;
    if (!token) return;

    await executeApiCall(async () => {
      await apiDelete(`/api-ui/users/api-tokens/${token.id}`);
      // Only update state after successful API call
      hideTokenValue(token.id);
      setTokens((currentTokens) => currentTokens.filter((it) => it.id !== token.id));
      return t("settings.apiToken.deleteSuccess", { name: token.name });
    });
  };

  useEffect(() => {
    const loadInitialData = async () => {
      await executeApiCall(loadTokens);
      setInitialDataLoaded(true);
    };
    loadInitialData();
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
          <Loader className="py-8" testId="api-token-loading" />
        ) : (
          <>
            <div className="mb-4">{formMessage}</div>

            <div className="space-y-6">
              {tokens.length === 0 ? (
                <p className="text-foreground" data-testid="api-token-no-tokens-message">
                  {t("settings.apiToken.noTokens")}
                </p>
              ) : (
                <div className="space-y-3" data-testid="api-tokens-list">
                  {tokens.map((token) => {
                    const revealedValue = revealedTokenValues[token.id];
                    return (
                      <div
                        key={token.id}
                        className="rounded-md border border-input bg-background/30 p-4 space-y-3"
                        data-testid="api-token-row"
                      >
                        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                          <span className="font-medium text-foreground break-all" data-testid="api-token-name">
                            {token.name}
                          </span>
                          <span className="text-sm text-muted-foreground" data-testid="api-token-created-at">
                            {t("settings.apiToken.createdAt", { date: formatDate(token.createdAt, i18n.language) })}
                          </span>
                        </div>
                        <div className="flex flex-col gap-2 sm:flex-row">
                          <div className="flex flex-1 gap-2">
                            <Input
                              type={revealedValue ? "text" : "password"}
                              value={revealedValue ?? MASKED_TOKEN}
                              readOnly
                              aria-label={t("settings.apiToken.tokenLabel", { name: token.name })}
                              data-testid="api-token-input"
                              className="text-foreground"
                            />
                            {revealedValue ? (
                              <Button
                                variant="outline"
                                size="icon"
                                onClick={() => handleCopyToken(revealedValue)}
                                data-testid="copy-api-token-button"
                                className="shrink-0"
                              >
                                <Copy className="h-4 w-4" />
                              </Button>
                            ) : (
                              <Button
                                variant="outline"
                                size="icon"
                                onClick={() => handleRevealToken(token)}
                                data-testid="show-api-token-button"
                                className="shrink-0"
                              >
                                <Eye className="h-4 w-4" />
                              </Button>
                            )}
                          </div>
                          <div className="flex gap-2">
                            <Button
                              onClick={() => handleRegenerateToken(token)}
                              disabled={apiCallInProgress}
                              data-testid="regenerate-api-token-button"
                              className="bg-teal-600 hover:bg-teal-700"
                            >
                              {t("settings.apiToken.regenerate")}
                            </Button>
                            <Button
                              onClick={() => handleRequestDeletion(token)}
                              disabled={apiCallInProgress}
                              data-testid="delete-api-token-button"
                              variant="destructive"
                            >
                              {t("settings.apiToken.delete")}
                            </Button>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              <div className="space-y-2 border-t border-input pt-6">
                <Label htmlFor="new-api-token-name" className="text-foreground">
                  {t("settings.apiToken.newTokenLabel")}
                </Label>
                <p className="text-sm text-muted-foreground">{t("settings.apiToken.newTokenDescription")}</p>
                <form
                  className="flex flex-col gap-2 sm:flex-row"
                  onSubmit={(event) => {
                    event.preventDefault();
                    handleCreateToken();
                  }}
                >
                  <Input
                    id="new-api-token-name"
                    value={newTokenName}
                    onChange={(event) => setNewTokenName(event.target.value)}
                    placeholder={t("settings.apiToken.newTokenPlaceholder")}
                    maxLength={MAX_TOKEN_NAME_LENGTH}
                    data-testid="new-api-token-name-input"
                    className="text-foreground"
                  />
                  <Button
                    type="submit"
                    disabled={apiCallInProgress || newTokenName.trim().length === 0}
                    data-testid="create-api-token-button"
                    className="bg-teal-600 hover:bg-teal-700 shrink-0"
                  >
                    {apiCallInProgress ? t("settings.apiToken.creating") : t("settings.apiToken.create")}
                  </Button>
                </form>
              </div>
            </div>
          </>
        )}
      </CardContent>

      <ConfirmationDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        title={t("settings.apiToken.deleteConfirm.title")}
        description={t("settings.apiToken.deleteConfirm.message", { name: tokenPendingDeletion?.name ?? "" })}
        confirmLabel={t("settings.apiToken.deleteConfirm.confirm")}
        cancelLabel={t("settings.apiToken.deleteConfirm.cancel")}
        onConfirm={handleDeleteToken}
        confirmTestId="confirm-delete-api-token-button"
        cancelTestId="cancel-delete-api-token-button"
      />
    </Card>
  );
}
