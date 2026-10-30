"use client";

import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAppDispatch } from "@/store/hooks";
import { enterTenant, exitTenant, type AuthUser } from "@/store/slices/auth-slice";

/**
 * Browser side of the BFF session (LAC-10). Talks only to same-origin /api/session* routes; never sees
 * a token. Tenant switches clear the TanStack Query cache so no data from the previous tenant survives.
 */

export type SessionInfo = {
  authenticated: true;
  user: { sub: string | null; name: string; email: string | null; roles: string[]; role: string; tenantId: string | null };
  isPlatformAdmin: boolean;
  activeTenant: { id: string; name: string } | null;
};

/** GET /api/session → the signed-in user, or null when there is no usable session. */
export async function fetchSession(): Promise<SessionInfo | null> {
  const response = await fetch("/api/session", { credentials: "same-origin", cache: "no-store" });
  if (!response.ok) return null;
  return (await response.json()) as SessionInfo;
}

/** Maps the BFF session to the Redux auth user. */
export function toAuthUser(info: SessionInfo): AuthUser {
  const activeTenantId = info.isPlatformAdmin ? (info.activeTenant?.id ?? null) : info.user.tenantId;
  return {
    name: info.user.name,
    role: info.user.role,
    roles: info.user.roles,
    tenant: info.user.tenantId ?? "Pede Aqui Platform",
    tenantId: info.user.tenantId,
    activeTenantId,
    activeTenantName: info.isPlatformAdmin ? (info.activeTenant?.name || null) : null,
    email: info.user.email ?? undefined,
  };
}

async function sessionMutation(method: "PUT" | "DELETE", body?: unknown): Promise<void> {
  const response = await fetch("/api/session/tenant", {
    method,
    credentials: "same-origin",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw new Error(`Não foi possível mudar de tenant (${response.status}).`);
}

/** Platform-admin tenant impersonation: server-side cookie first, then Redux, then a full cache reset. */
export function useTenantSwitch() {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();

  const enter = useCallback(
    async (tenantId: string, tenantName: string) => {
      await sessionMutation("PUT", { tenantId, tenantName });
      queryClient.clear();
      dispatch(enterTenant({ tenantId, tenantName }));
    },
    [dispatch, queryClient],
  );

  const exit = useCallback(async () => {
    await sessionMutation("DELETE");
    queryClient.clear();
    dispatch(exitTenant());
  }, [dispatch, queryClient]);

  return { enter, exit };
}

/**
 * Signs out: POSTs a form to /api/session/logout, which drops the session cookies and redirects to
 * Keycloak's end-session endpoint (SSO logout) and back to /login. A full navigation discards the Redux
 * store and query cache with the page, so no client-side redirect can race the form submission.
 */
export function logout(): void {
  const form = document.createElement("form");
  form.method = "POST";
  form.action = "/api/session/logout";
  document.body.appendChild(form);
  form.submit();
}
