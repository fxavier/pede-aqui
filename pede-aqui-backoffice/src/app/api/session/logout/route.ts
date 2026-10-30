import type { NextRequest } from "next/server";
import { ACTIVE_TENANT_COOKIE } from "@/lib/auth/active-tenant";
import { keycloakLogoutUrl } from "@/lib/auth/keycloak";
import { appOrigin, isSameOriginMutation, jsonError } from "@/lib/auth/server";
import { readSessionToken, sessionCookieIsSecure, sessionCookieName } from "@/lib/auth/session-cookie";

/**
 * Sign-out (LAC-10): expires the Auth.js session cookie (all chunks) and the impersonation cookie, then
 * sends the browser to Keycloak's end_session_endpoint with id_token_hint so the SSO session ends too.
 * POST only, same-origin. Deliberately not wrapped in auth(): that wrapper re-issues the rolling session
 * cookie on every response, which would undo the deletion.
 */
export const dynamic = "force-dynamic";

export async function POST(req: NextRequest): Promise<Response> {
  if (!isSameOriginMutation(req)) return jsonError(403, "bad_origin", "Origem do pedido não permitida.");
  const token = await readSessionToken(req);
  const target = keycloakLogoutUrl(token?.idToken, `${appOrigin(req)}/login?logged_out=1`);

  const response = new Response(null, { status: 303, headers: { Location: target, "Cache-Control": "no-store" } });
  const secure = sessionCookieIsSecure() ? "; Secure" : "";
  const sessionCookie = sessionCookieName();
  const toClear = req.cookies
    .getAll()
    .map((cookie) => cookie.name)
    .filter((name) => name === ACTIVE_TENANT_COOKIE || name === sessionCookie || name.startsWith(`${sessionCookie}.`));
  for (const name of toClear) {
    response.headers.append("Set-Cookie", `${name}=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax${secure}`);
  }
  return response;
}
