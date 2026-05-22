import { httpClient } from "../http-client";

function getAuthToken(): string | undefined {
  if (typeof window !== "undefined") {
    const token = sessionStorage.getItem("auth_token");
    return token ?? undefined;
  }
  return undefined;
}

class ApiClient {
  private basePath = "";

  async get<T>(path: string): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, { authToken: getAuthToken() });
  }

  async post<T>(path: string, data?: unknown): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, {
      method: "POST",
      body: data ? JSON.stringify(data) : undefined,
      authToken: getAuthToken(),
    });
  }

  async patch<T>(path: string, data?: unknown): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, {
      method: "PATCH",
      body: data ? JSON.stringify(data) : undefined,
      authToken: getAuthToken(),
    });
  }

  async put<T>(path: string, data?: unknown): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, {
      method: "PUT",
      body: data ? JSON.stringify(data) : undefined,
      authToken: getAuthToken(),
    });
  }

  async delete<T>(path: string): Promise<T> {
    return httpClient<T>(`${this.basePath}${path}`, {
      method: "DELETE",
      authToken: getAuthToken(),
    });
  }
}

export const apiClient = new ApiClient();
