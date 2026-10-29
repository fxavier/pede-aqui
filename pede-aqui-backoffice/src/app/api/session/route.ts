import { hasLiveSession, withAuth } from "@/lib/auth/server";
import { isPlatformAdmin, primaryRole } from "@/lib/auth/roles";

/**
 * Browser-facing view of the BFF session (LAC-10): identity, roles and the impersonated tenant.
 * Never includes any token. 401 when there is no usable session (including a failed refresh).
 */
export const dynamic = "force-dynamic";

export const GET = withAuth<unknown>(async (ctx) => {
  if (!hasLiveSession(ctx) || !ctx.session) {
    return Response.json(
      { authenticated: false, error: ctx.session?.error ?? null },
      { status: 401, headers: { "Cache-Control": "no-store" } },
    );
  }
  const { session, activeTenant } = ctx;
  return Response.json(
    {
      authenticated: true,
      user: {
        sub: session.sub ?? null,
        name: session.user?.name ?? session.user?.email ?? "",
        email: session.user?.email ?? null,
        roles: session.roles,
        role: primaryRole(session.roles),
        tenantId: session.tenantId,
      },
      isPlatformAdmin: isPlatformAdmin(session.roles, session.tenantId),
      activeTenant,
    },
    { headers: { "Cache-Control": "no-store" } },
  );
});
