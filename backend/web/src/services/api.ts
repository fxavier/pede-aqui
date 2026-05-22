export type ApiError = {
  code: string;
  message: string;
  correlationId?: string;
};

function baseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
}

export async function apiGet<T>(path: string, token: string): Promise<T> {
  const response = await fetch(`${baseUrl()}${path}`, {
    headers: { Authorization: `Bearer ${token}` }
  });

  if (!response.ok) {
    throw (await response.json()) as ApiError;
  }

  return response.json() as Promise<T>;
}

export async function apiPatch<T>(path: string, body: unknown, token: string): Promise<T> {
  const response = await fetch(`${baseUrl()}${path}`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    throw (await response.json()) as ApiError;
  }

  return response.json() as Promise<T>;
}

export type AsyncState<T> =
  | { kind: "loading" }
  | { kind: "forbidden" }
  | { kind: "error"; message: string }
  | { kind: "empty" }
  | { kind: "success"; data: T };

export async function safeApiGet<T>(path: string, token: string): Promise<AsyncState<T>> {
  try {
    const response = await fetch(`${baseUrl()}${path}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (response.status === 403) return { kind: "forbidden" };
    if (!response.ok) {
      return { kind: "error", message: `Erro HTTP ${response.status}` };
    }
    const data = (await response.json()) as T;
    if (Array.isArray(data) && data.length === 0) return { kind: "empty" };
    return { kind: "success", data };
  } catch (error) {
    return { kind: "error", message: error instanceof Error ? error.message : "Erro desconhecido" };
  }
}
