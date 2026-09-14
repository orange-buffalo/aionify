import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { ConfirmationDialog } from "@/components/ui/confirmation-dialog";
import { apiPost } from "@/lib/api";
import { TOKEN_KEY } from "@/lib/constants";
import { decodeJwt } from "@/lib/token";
import { getHostBridge, HostBridge, HostBridgeError, isInAppPath } from "@/lib/host-bridge";

const MAX_TOKEN_NAME_LENGTH = 100;

interface AuthState {
  authenticated: boolean;
  userName?: string;
}

interface ProvisionedApiToken {
  name: string;
  token: string;
}

interface PendingTokenConsent {
  name: string;
  // The user the request was made for - it must not be approved on behalf of anyone else
  userId: number;
  resolve: (token: ProvisionedApiToken) => void;
  reject: (error: HostBridgeError) => void;
}

/**
 * Connects the app to an OS-specific wrapper via the host bridge, if the app is hosted by one.
 * Renders nothing in regular browsers.
 */
export function HostBridgeIntegration() {
  const bridge = getHostBridge();
  return bridge ? <ActiveHostBridgeIntegration bridge={bridge} /> : null;
}

function ActiveHostBridgeIntegration({ bridge }: { bridge: HostBridge }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [ready, setReady] = useState(false);
  const [tokenConsentOpen, setTokenConsentOpen] = useState(false);
  const [tokenConsentName, setTokenConsentName] = useState("");
  const [isProvisioningToken, setIsProvisioningToken] = useState(false);
  const tokenConsentRef = useRef<PendingTokenConsent | null>(null);
  const isProvisioningTokenRef = useRef(false);
  const navigateRef = useRef(navigate);
  const lastPublishedAuthStateRef = useRef<string | null>(null);

  useEffect(() => {
    navigateRef.current = navigate;
  }, [navigate]);

  // Handlers must be registered before the handshake, as it announces the available methods to the host
  useEffect(() => {
    const unregisterNavigate = bridge.registerHandler("app.navigate", (params) => {
      const path = getStringParam(params, "path");
      if (!isInAppPath(path)) {
        throw new HostBridgeError("INVALID_PARAMS", "'path' must be an in-app path starting with '/'");
      }
      navigateRef.current(path);
      return {};
    });

    const unregisterProvisionApiToken = bridge.registerHandler("auth.provisionApiToken", (params) => {
      const name = getStringParam(params, "name").trim();
      if (name.length === 0 || name.length > MAX_TOKEN_NAME_LENGTH) {
        throw new HostBridgeError("INVALID_PARAMS", `'name' must be 1 to ${MAX_TOKEN_NAME_LENGTH} characters long`);
      }
      const userId = getAuthenticatedUserId();
      if (userId === null) {
        throw new HostBridgeError("NOT_AUTHENTICATED", "The user is not logged in");
      }
      if (tokenConsentRef.current) {
        throw new HostBridgeError("REQUEST_IN_PROGRESS", "Another token request is awaiting the user's decision");
      }
      return new Promise<ProvisionedApiToken>((resolve, reject) => {
        tokenConsentRef.current = { name, userId, resolve, reject };
        setTokenConsentName(name);
        setTokenConsentOpen(true);
      });
    });

    bridge.start().then(
      () => setReady(true),
      (error) => console.log("[HostBridge] Handshake with the host failed:", error)
    );

    return () => {
      unregisterNavigate();
      unregisterProvisionApiToken();
    };
  }, [bridge]);

  // Auth state can change on navigation (login, logout) or in another tab of the same origin
  useEffect(() => {
    if (!ready) return;

    const publishAuthState = () => {
      const authState = getAuthState();
      const serializedAuthState = JSON.stringify(authState);
      if (serializedAuthState === lastPublishedAuthStateRef.current) return;
      lastPublishedAuthStateRef.current = serializedAuthState;
      bridge.emitEvent("auth.changed", authState);
    };

    publishAuthState();
    window.addEventListener("storage", publishAuthState);
    return () => window.removeEventListener("storage", publishAuthState);
  }, [ready, location, bridge]);

  // A pending token request is cancelled as soon as the logged in user changes (logout, login or another tab)
  useEffect(() => {
    const cancelTokenConsentOnUserChange = () => {
      const consent = tokenConsentRef.current;
      // Once provisioning has started, the server binds the request to the user (see handleApproveToken)
      if (!consent || isProvisioningTokenRef.current || consent.userId === getAuthenticatedUserId()) return;
      takeTokenConsent();
      consent.reject(userChangedError());
    };

    cancelTokenConsentOnUserChange();
    window.addEventListener("storage", cancelTokenConsentOnUserChange);
    return () => window.removeEventListener("storage", cancelTokenConsentOnUserChange);
  }, [location]);

  const takeTokenConsent = () => {
    const consent = tokenConsentRef.current;
    tokenConsentRef.current = null;
    setTokenConsentOpen(false);
    return consent;
  };

  const handleApproveToken = async () => {
    const consent = tokenConsentRef.current;
    if (!consent) return;

    if (consent.userId !== getAuthenticatedUserId()) {
      takeTokenConsent();
      consent.reject(userChangedError());
      return;
    }

    isProvisioningTokenRef.current = true;
    setIsProvisioningToken(true);
    try {
      // The server rejects the request if the session belongs to a different user by the time it is processed
      const createdToken = await apiPost<ProvisionedApiToken>("/api-ui/users/api-tokens", {
        name: consent.name,
        expectedUserId: consent.userId,
      });
      consent.resolve({ name: createdToken.name, token: createdToken.token });
    } catch (error: any) {
      // The host requested the token, so it receives the failure and decides how to present it
      consent.reject(
        new HostBridgeError(error.errorCode ?? "TOKEN_PROVISIONING_FAILED", error.message ?? "Failed to create token")
      );
    } finally {
      takeTokenConsent();
      isProvisioningTokenRef.current = false;
      setIsProvisioningToken(false);
    }
  };

  const handleRejectToken = () => {
    if (isProvisioningTokenRef.current) return;
    takeTokenConsent()?.reject(new HostBridgeError("USER_REJECTED", "The user rejected the token request"));
  };

  return (
    <ConfirmationDialog
      open={tokenConsentOpen}
      onOpenChange={(open) => {
        if (!open) handleRejectToken();
      }}
      title={t("hostBridge.tokenConsent.title")}
      description={t("hostBridge.tokenConsent.message", { name: tokenConsentName })}
      confirmLabel={isProvisioningToken ? t("hostBridge.tokenConsent.approving") : t("hostBridge.tokenConsent.approve")}
      cancelLabel={t("hostBridge.tokenConsent.reject")}
      confirmVariant="default"
      confirmClassName="bg-teal-600 hover:bg-teal-700"
      onConfirm={handleApproveToken}
      isConfirming={isProvisioningToken}
      dialogTestId="host-token-consent-dialog"
      confirmTestId="host-token-consent-approve-button"
      cancelTestId="host-token-consent-reject-button"
    />
  );
}

function getValidJwtPayload() {
  const token = localStorage.getItem(TOKEN_KEY);
  const payload = token ? decodeJwt(token) : null;
  if (!payload || (typeof payload.exp === "number" && payload.exp * 1000 <= Date.now())) {
    return null;
  }
  return payload;
}

function getAuthState(): AuthState {
  const payload = getValidJwtPayload();
  if (!payload) {
    return { authenticated: false };
  }
  return typeof payload.sub === "string" ? { authenticated: true, userName: payload.sub } : { authenticated: true };
}

function getAuthenticatedUserId(): number | null {
  const userId = getValidJwtPayload()?.userId;
  return typeof userId === "number" ? userId : null;
}

function userChangedError() {
  return new HostBridgeError("AUTH_CHANGED", "The logged in user has changed");
}

function getStringParam(params: unknown, name: string): string {
  const value = typeof params === "object" && params !== null ? (params as Record<string, unknown>)[name] : undefined;
  if (typeof value !== "string") {
    throw new HostBridgeError("INVALID_PARAMS", `'${name}' must be a string`);
  }
  return value;
}
