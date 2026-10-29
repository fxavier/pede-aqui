/**
 * Browser HTTP client for the backend API, routed through the same-origin BFF (`/api/bff`, LAC-10).
 * The BFF attaches the access token and the impersonated tenant server-side, so this client never handles
 * credentials: the session travels in the HttpOnly Auth.js cookie. A 401 means the session is gone
 * (expired, refresh failed, signed out elsewhere) and sends the user back to the login page.
 */
export const BFF_BASE_PATH = "/api/bff";

/** Redirects to /login (keeping the current page as callback) when the BFF reports no usable session. */
export function redirectToLogin(): void {
  if (typeof window === "undefined") return;
  if (window.location.pathname.startsWith("/login")) return;
  const callbackUrl = `${window.location.pathname}${window.location.search}`;
  window.location.assign(`/login?expired=1&callbackUrl=${encodeURIComponent(callbackUrl)}`);
}

/** fetch() against the BFF with the common headers and 401 handling; returns the raw Response. */
export async function bffFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(`${BFF_BASE_PATH}${path}`, {
    ...init,
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...init.headers,
    },
  });
  if (response.status === 401) {
    redirectToLogin();
  }
  return response;
}

export async function httpClient<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await bffFetch(path, {
    ...options,
    headers: {
      ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const text = await response.text();
    let parsed: unknown = null;
    try {
      parsed = JSON.parse(text);
    } catch {
      // body não é JSON válido — usar fallback de texto
    }
    if (parsed !== null) {
      throw parsed;
    }
    throw new Error(`Pedido à API falhou: ${response.status} ${response.statusText} ${text}`.trim());
  }

  // 204s e respostas 2xx sem corpo (approve/reject, deletes, resend) não têm JSON para parsear
  const body = await response.text();
  if (!body) {
    return undefined as T;
  }
  return JSON.parse(body) as T;
}
