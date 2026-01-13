import { TOKEN_KEY } from "./constants";

/**
 * Makes an authenticated API request with the stored JWT token.
 * If a 401 Unauthorized response is received, clears the token and redirects to login.
 */
export async function apiRequest<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem(TOKEN_KEY);

  const headers: HeadersInit = {
    ...options.headers,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  if (options.body && typeof options.body === "string") {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (!response.ok) {
    // Handle 401 Unauthorized - token expired or invalid
    if (response.status === 401) {
      // Clear the token
      localStorage.removeItem(TOKEN_KEY);

      // Store a flag to show session expired message on login page
      sessionStorage.setItem("sessionExpired", "true");

      // Redirect to login page
      window.location.href = "/login";

      // Throw error to prevent further processing
      throw new Error("Session expired");
    }

    const errorData = await response.json().catch(() => ({}));
    const error = new Error(errorData.error || `Request failed with status ${response.status}`);
    // Attach errorCode to the error for translation
    (error as any).errorCode = errorData.errorCode;
    throw error;
  }

  return response.json();
}

/**
 * GET request helper
 * @param url URL to fetch
 * @param signal Optional AbortSignal to cancel the request
 */
export async function apiGet<T>(url: string, signal?: AbortSignal): Promise<T> {
  return apiRequest<T>(url, { signal });
}

/**
 * POST request helper
 */
export async function apiPost<T>(url: string, body: unknown): Promise<T> {
  return apiRequest<T>(url, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/**
 * PUT request helper
 */
export async function apiPut<T>(url: string, body: unknown): Promise<T> {
  return apiRequest<T>(url, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/**
 * PATCH request helper
 */
export async function apiPatch<T>(url: string, body: unknown): Promise<T> {
  return apiRequest<T>(url, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

/**
 * DELETE request helper
 */
export async function apiDelete<T>(url: string): Promise<T> {
  return apiRequest<T>(url, {
    method: "DELETE",
  });
}
