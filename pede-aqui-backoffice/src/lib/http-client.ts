type RequestOptions = RequestInit & {
  authToken?: string;
  tenantId?: string;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

export async function httpClient<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (!API_BASE_URL) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL não está configurada. Mantenha os mocks activos ou configure a URL base da API.");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.authToken ? { Authorization: `Bearer ${options.authToken}` } : {}),
      ...(options.tenantId ? { "X-Tenant-Id": options.tenantId } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`Pedido à API falhou: ${response.status} ${response.statusText} ${body}`.trim());
  }

  return response.json() as Promise<T>;
}
