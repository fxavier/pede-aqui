import { hasLiveSession, isSameOriginMutation, jsonError, withAuth } from "@/lib/auth/server";

/**
 * BFF proxy (LAC-10): `/api/bff/<path>` → `${API_BASE_URL}/<path>`. Attaches the Keycloak access token
 * server-side (the browser never sees it) and, for a platform admin only, the impersonated tenant from the
 * signed server-side cookie as `X-Tenant-Id`. Backend 401s surface as 401 so the client re-authenticates.
 */
export const dynamic = "force-dynamic";

type RouteCtx = { params: Promise<{ path: string[] }> };

/** Backend endpoints callable without a session (the backend also leaves them open). */
const PUBLIC_ENDPOINTS: ReadonlyArray<{ method: string; path: string }> = [{ method: "POST", path: "/register" }];

/** Request headers forwarded to the backend; everything else (cookies included) is dropped. */
const FORWARDED_REQUEST_HEADERS = ["accept", "content-type", "idempotency-key", "accept-language"];
/** Response headers passed back to the browser. */
const FORWARDED_RESPONSE_HEADERS = ["content-type", "content-disposition", "content-length", "location", "retry-after"];

function apiBaseUrl(): string {
  return (process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1").replace(/\/$/, "");
}

async function proxy(ctx: Parameters<Parameters<typeof withAuth>[0]>[0], routeCtx: RouteCtx): Promise<Response> {
  const { req } = ctx;
  const { path } = await routeCtx.params;
  if (!path?.length || path.some((segment) => segment === "." || segment === ".." || segment.includes("/") || segment.includes("\\"))) {
    return jsonError(400, "bad_path", "Caminho inválido.");
  }
  if (!isSameOriginMutation(req)) {
    return jsonError(403, "bad_origin", "Origem do pedido não permitida.");
  }

  const apiPath = "/" + path.map(encodeURIComponent).join("/");
  const isPublic = PUBLIC_ENDPOINTS.some((endpoint) => endpoint.method === req.method && endpoint.path === apiPath);
  const live = hasLiveSession(ctx);
  if (!live && !isPublic) {
    return jsonError(401, "unauthenticated", "Sessão expirada. Inicie sessão novamente.");
  }

  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = req.headers.get(name);
    if (value) headers.set(name, value);
  }
  if (live) {
    headers.set("Authorization", `Bearer ${ctx.token!.accessToken}`);
    if (ctx.activeTenant) headers.set("X-Tenant-Id", ctx.activeTenant.id);
  }

  const hasBody = req.method !== "GET" && req.method !== "HEAD";
  let upstream: Response;
  try {
    upstream = await fetch(`${apiBaseUrl()}${apiPath}${req.nextUrl.search}`, {
      method: req.method,
      headers,
      body: hasBody ? await req.arrayBuffer() : undefined,
      cache: "no-store",
      redirect: "manual",
    });
  } catch {
    return jsonError(502, "backend_unreachable", "Não foi possível contactar a API.");
  }

  const responseHeaders = new Headers({ "Cache-Control": "no-store" });
  for (const name of FORWARDED_RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  return new Response(upstream.body, { status: upstream.status, statusText: upstream.statusText, headers: responseHeaders });
}

const handler = withAuth<RouteCtx>(proxy);

export { handler as GET, handler as POST, handler as PUT, handler as PATCH, handler as DELETE };
