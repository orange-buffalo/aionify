import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useParams, useNavigate } from "react-router";
import { PortalLayout } from "@/components/layout/PortalLayout";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { apiGet, apiPut, apiPost } from "@/lib/api";
import { ArrowLeft } from "lucide-react";
import { useApiExecutor } from "@/hooks/useApiExecutor";

interface ActivationTokenInfo {
  token: string;
  expiresAt: string;
}

interface UserDetail {
  id: number;
  userName: string;
  greeting: string;
  isAdmin: boolean;
  activationToken: ActivationTokenInfo | null;
}

interface ActivationTokenResponse {
  token: string;
  expiresAt: string;
}

export function EditUserPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { executeApiCall, apiCallInProgress, formMessage } = useApiExecutor();

  const [user, setUser] = useState<UserDetail | null>(null);
  const [userName, setUserName] = useState("");
  const [initialDataLoaded, setInitialDataLoaded] = useState(false);

  const loadUser = async () => {
    await executeApiCall(async () => {
      const data = await apiGet<UserDetail>(`/api/admin/users/${id}`);
      setUser(data);
      setUserName(data.userName);
    });
  };

  useEffect(() => {
    const loadInitialData = async () => {
      await loadUser();
      setInitialDataLoaded(true);

      // Check if we have a success message from session storage (e.g., after creating a user)
      const userCreated = sessionStorage.getItem("userCreated");
      if (userCreated) {
        // We'll need to handle this differently since success is managed by the hook
        // For now, we can just remove it from session storage
        sessionStorage.removeItem("userCreated");
      }
    };
    loadInitialData();
  }, [id]);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();

    // Client-side validation
    if (!userName || userName.trim() === "") {
      await executeApiCall(async () => {
        throw new Error(t("validation.usernameBlank"));
      });
      return;
    }

    if (userName.length > 255) {
      await executeApiCall(async () => {
        throw new Error(t("validation.usernameTooLong"));
      });
      return;
    }

    await executeApiCall(async () => {
      await apiPut(`/api/admin/users/${id}`, {
        userName: userName,
      });

      // Reload user to get updated data
      const data = await apiGet<UserDetail>(`/api/admin/users/${id}`);
      setUser(data);
      setUserName(data.userName);

      return t("portal.admin.users.edit.updateSuccess");
    });
  };

  const handleRegenerateToken = async () => {
    await executeApiCall(async () => {
      await apiPost<ActivationTokenResponse>(`/api/admin/users/${id}/regenerate-activation-token`, {});

      // Reload user to get updated activation token
      const data = await apiGet<UserDetail>(`/api/admin/users/${id}`);
      setUser(data);

      return t("portal.admin.users.edit.tokenRegenerated");
    });
  };

  const getActivationUrl = (token: string) => {
    return `${window.location.origin}/activate?token=${token}`;
  };

  const handleBack = () => {
    navigate("/admin/users");
  };

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

          <div className="mb-4">{formMessage}</div>

          {!initialDataLoaded ? (
            <div className="text-center py-8 text-foreground" data-testid="edit-user-loading">
              {t("common.loading")}
            </div>
          ) : user ? (
            <div className="space-y-8">
              {/* Username Form */}
              <Card className="border-none shadow-md">
                <CardHeader>
                  <CardTitle className="text-xl font-semibold" data-testid="username-section-title">
                    {t("portal.admin.users.edit.usernameSection")}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <form onSubmit={handleSave} className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="userName" className="text-foreground">
                        {t("portal.admin.users.edit.username")}
                      </Label>
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
                      disabled={apiCallInProgress || userName === user.userName}
                      data-testid="save-button"
                      className="bg-teal-600 hover:bg-teal-700"
                    >
                      {apiCallInProgress ? t("portal.admin.users.edit.saving") : t("portal.admin.users.edit.save")}
                    </Button>
                  </form>
                </CardContent>
              </Card>

              {/* User Info */}
              <Card className="border-none shadow-md">
                <CardHeader>
                  <CardTitle className="text-xl font-semibold" data-testid="user-info-title">
                    {t("portal.admin.users.edit.userInfo")}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3 text-sm">
                    <div>
                      <span className="text-muted-foreground">{t("portal.admin.users.edit.greeting")}:</span>
                      <span className="ml-2 text-foreground" data-testid="user-greeting">
                        {user.greeting}
                      </span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">{t("portal.admin.users.edit.userType")}:</span>
                      <span className="ml-2 text-foreground" data-testid="user-type">
                        {user.isAdmin ? t("portal.admin.users.table.admin") : t("portal.admin.users.table.regularUser")}
                      </span>
                    </div>
                  </div>

                  <div className="mt-4 p-3 bg-muted rounded text-sm" data-testid="user-info-note">
                    <p className="text-muted-foreground">{t("portal.admin.users.edit.profileNote")}</p>
                  </div>
                </CardContent>
              </Card>

              {/* Activation Token */}
              <Card className="border-none shadow-md">
                <CardHeader>
                  <CardTitle className="text-xl font-semibold" data-testid="activation-section-title">
                    {t("portal.admin.users.edit.activationSection")}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {user.activationToken ? (
                    <div className="space-y-4">
                      <div>
                        <Label htmlFor="activationUrl" className="text-foreground">
                          {t("portal.admin.users.edit.activationUrl")}
                        </Label>
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
                        disabled={apiCallInProgress}
                        className="bg-teal-600 hover:bg-teal-700"
                        data-testid="regenerate-token-button"
                      >
                        {apiCallInProgress
                          ? t("portal.admin.users.edit.regenerating")
                          : t("portal.admin.users.edit.regenerateToken")}
                      </Button>
                    </div>
                  ) : (
                    <div className="space-y-4">
                      <p className="text-muted-foreground" data-testid="no-activation-token">
                        {t("portal.admin.users.edit.noActivationToken")}
                      </p>

                      <Button
                        onClick={handleRegenerateToken}
                        disabled={apiCallInProgress}
                        className="bg-teal-600 hover:bg-teal-700"
                        data-testid="generate-token-button"
                      >
                        {apiCallInProgress
                          ? t("portal.admin.users.edit.generating")
                          : t("portal.admin.users.edit.generateToken")}
                      </Button>
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Limitations */}
              <Card className="border-none shadow-md">
                <CardHeader>
                  <CardTitle className="text-xl font-semibold" data-testid="limitations-title">
                    {t("portal.admin.users.edit.limitations")}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2 text-sm text-muted-foreground" data-testid="limitations-list">
                    <p>• {t("portal.admin.users.edit.noTypeChange")}</p>
                    <p>• {t("portal.admin.users.edit.noPasswordChange")}</p>
                  </div>
                </CardContent>
              </Card>
            </div>
          ) : null}
        </div>
      </div>
    </PortalLayout>
  );
}
