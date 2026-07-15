import { httpClient } from "../http-client";

// Exported so callers that need a raw fetch (e.g. blob/CSV downloads) can reuse the same
// auth/tenant resolution as the JSON client instead of duplicating sessionStorage lookups.
export function getAuthToken(): string | undefined {
  if (typeof window !== "undefined") {
    const token = sessionStorage.getItem("auth_token");
    return token ?? undefined;
  }
  return undefined;
}

export function getTenantId(): string | undefined {
  if (typeof window !== "undefined") {
    const id = sessionStorage.getItem("tenant_id");
    return id ?? undefined;
  }
  return undefined;
}

class ApiClient {
  private basePath = "";

  async get<T>(path: string): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, { authToken: getAuthToken(), tenantId: getTenantId() });
  }

  async post<T>(path: string, data?: unknown, headers?: Record<string, string>): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, {
      method: "POST",
      body: data ? JSON.stringify(data) : undefined,
      authToken: getAuthToken(),
      tenantId: getTenantId(),
      headers,
    });
  }

  async patch<T>(path: string, data?: unknown): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, {
      method: "PATCH",
      body: data ? JSON.stringify(data) : undefined,
      authToken: getAuthToken(),
      tenantId: getTenantId(),
    });
  }

  async put<T>(path: string, data?: unknown): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, {
      method: "PUT",
      body: data ? JSON.stringify(data) : undefined,
      authToken: getAuthToken(),
      tenantId: getTenantId(),
    });
  }

  async delete<T>(path: string): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, {
      method: "DELETE",
      authToken: getAuthToken(),
      tenantId: getTenantId(),
    });
  }
}

export const apiClient = new ApiClient();
