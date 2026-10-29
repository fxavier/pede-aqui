import type { NextRequest } from "next/server";
import type { Session } from "next-auth";
import type { JWT } from "next-auth/jwt";
import { auth } from "@/auth";
import { ACTIVE_TENANT_COOKIE, decodeActiveTenant, type ActiveTenant } from "./active-tenant";
import { isPlatformAdmin } from "./roles";
import { currentServerToken, runWithTokenSlot } from "./token-store";

/** Server-only helpers shared by the BFF and session route handlers (LAC-10). */

export type ServerAuthContext = {
  req: NextRequest & { auth: Session | null };
  session: Session | null;
  token: JWT | null;
  activeTenant: ActiveTenant | null;
};

/**
 * Wraps a route handler with Auth.js session resolution. Inside it the jwt callback may refresh the
 * Keycloak tokens; the rotated session cookie is appended to the handler's response by Auth.js, and the
 * resolved server-side token (never sent to the browser) is handed to the handler.
 */
export function withAuth<C>(handler: (ctx: ServerAuthContext, routeCtx: C) => Promise<Response>) {
  return (req: NextRequest, routeCtx: C) =>
    runWithTokenSlot(async () => {
      // With a lazily-initialised config, next-auth's `auth(fn)` resolves to the wrapped handler
      // asynchronously (its typings say otherwise), so await it before calling.
      const wrapped = (await Promise.resolve(
        auth(async (authReq) => {
          const session = authReq.auth;
          const token = currentServerToken();
          const sub = token?.sub;
          const activeTenant =
            session && isPlatformAdmin(session.roles, session.tenantId)
              ? decodeActiveTenant(authReq.cookies.get(ACTIVE_TENANT_COOKIE)?.value, sub)
              : null;
          return handler({ req: authReq, session, token, activeTenant }, routeCtx);
        }) as unknown,
      )) as (req: NextRequest, ctx: C) => Promise<Response>;
      return wrapped(req, routeCtx);
    });
}

/** A usable session: signed in, not in a failed-refresh state, and holding an access token server-side. */
export function hasLiveSession(ctx: ServerAuthContext): boolean {
  return !!ctx.session && !ctx.session.error && !!ctx.token?.accessToken;
}

/** Public origin of the app (AUTH_URL when set — e.g. behind a proxy — else the request origin). */
export function appOrigin(req: NextRequest): string {
  const configured = process.env.AUTH_URL;
  return configured ? new URL(configured).origin : req.nextUrl.origin;
}

/**
 * CSRF defence for state-changing BFF/session calls on top of SameSite=Lax: the Origin header (always sent
 * by browsers on non-GET fetches) must be our own origin.
 */
export function isSameOriginMutation(req: NextRequest): boolean {
  if (req.method === "GET" || req.method === "HEAD") return true;
  const origin = req.headers.get("origin");
  return origin === appOrigin(req);
}

export function jsonError(status: number, code: string, message: string): Response {
  return Response.json({ status, code, message }, { status, headers: { "Cache-Control": "no-store" } });
}
