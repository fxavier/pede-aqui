import { NextResponse } from "next/server";
import {
  ACTIVE_TENANT_COOKIE,
  activeTenantCookieOptions,
  encodeActiveTenant,
  isTenantId,
} from "@/lib/auth/active-tenant";
import { isPlatformAdmin } from "@/lib/auth/roles";
import { hasLiveSession, isSameOriginMutation, jsonError, withAuth, type ServerAuthContext } from "@/lib/auth/server";

/**
 * Platform-admin tenant impersonation (LAC-10). PUT enters a tenant, DELETE exits. The choice is stored in
 * a signed HttpOnly cookie bound to the admin's subject; only platform admins may set it.
 */
export const dynamic = "force-dynamic";

function guard(ctx: ServerAuthContext): Response | null {
  if (!isSameOriginMutation(ctx.req)) return jsonError(403, "bad_origin", "Origem do pedido não permitida.");
  if (!hasLiveSession(ctx) || !ctx.session || !ctx.token?.sub) return jsonError(401, "unauthenticated", "Sessão expirada.");
  if (!isPlatformAdmin(ctx.session.roles, ctx.session.tenantId)) {
    return jsonError(403, "forbidden", "Só o administrador da plataforma pode mudar de tenant.");
  }
  return null;
}

export const PUT = withAuth<unknown>(async (ctx) => {
  const denied = guard(ctx);
  if (denied) return denied;
  const body = (await ctx.req.json().catch(() => null)) as { tenantId?: unknown; tenantName?: unknown } | null;
  if (!body || !isTenantId(body.tenantId)) return jsonError(400, "bad_tenant", "tenantId inválido.");
  const tenant = { id: body.tenantId, name: typeof body.tenantName === "string" ? body.tenantName : "" };
  const response = NextResponse.json({ activeTenant: tenant }, { headers: { "Cache-Control": "no-store" } });
  response.cookies.set(ACTIVE_TENANT_COOKIE, encodeActiveTenant(ctx.token!.sub!, tenant), activeTenantCookieOptions());
  return response;
});

export const DELETE = withAuth<unknown>(async (ctx) => {
  if (!isSameOriginMutation(ctx.req)) return jsonError(403, "bad_origin", "Origem do pedido não permitida.");
  const response = NextResponse.json({ activeTenant: null }, { headers: { "Cache-Control": "no-store" } });
  response.cookies.set(ACTIVE_TENANT_COOKIE, "", { ...activeTenantCookieOptions(), maxAge: 0 });
  return response;
});
