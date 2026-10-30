import { httpClient } from "../http-client";

/**
 * JSON API client used by every service in services.ts. Calls go to the same-origin BFF (see http-client.ts);
 * no token or tenant id is read or sent from the browser — the BFF adds both server-side.
 */
class ApiClient {
  async get<T>(path: string): Promise<T> {
    return httpClient<T>(path);
  }

  async post<T>(path: string, data?: unknown, headers?: Record<string, string>): Promise<T> {
    return httpClient<T>(path, {
      method: "POST",
      body: data ? JSON.stringify(data) : undefined,
      headers,
    });
  }

  async patch<T>(path: string, data?: unknown): Promise<T> {
    return httpClient<T>(path, {
      method: "PATCH",
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  async put<T>(path: string, data?: unknown): Promise<T> {
    return httpClient<T>(path, {
      method: "PUT",
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  async delete<T>(path: string): Promise<T> {
    return httpClient<T>(path, { method: "DELETE" });
  }
}

export const apiClient = new ApiClient();
