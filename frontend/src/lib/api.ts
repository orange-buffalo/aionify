import { TOKEN_KEY } from "./constants"

/**
 * Makes an authenticated API request with the stored JWT token.
 */
export async function apiRequest<T>(
  url: string,
  options: RequestInit = {}
): Promise<T> {
  const token = localStorage.getItem(TOKEN_KEY)
  
  const headers: HeadersInit = {
    ...options.headers,
    ...(token ? { "Authorization": `Bearer ${token}` } : {})
  }

  if (options.body && typeof options.body === "string") {
    headers["Content-Type"] = "application/json"
  }

  const response = await fetch(url, {
    ...options,
    headers
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.error || `Request failed with status ${response.status}`)
  }

  return response.json()
}

/**
 * GET request helper
 */
export async function apiGet<T>(url: string): Promise<T> {
  return apiRequest<T>(url)
}

/**
 * POST request helper
 */
export async function apiPost<T>(url: string, body: unknown): Promise<T> {
  return apiRequest<T>(url, {
    method: "POST",
    body: JSON.stringify(body)
  })
}

/**
 * PUT request helper
 */
export async function apiPut<T>(url: string, body: unknown): Promise<T> {
  return apiRequest<T>(url, {
    method: "PUT",
    body: JSON.stringify(body)
  })
}
