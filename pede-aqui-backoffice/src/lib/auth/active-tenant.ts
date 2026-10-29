import { createHmac, timingSafeEqual } from "node:crypto";
import { sessionCookieIsSecure } from "./session-cookie";

/**
 * Platform-admin tenant impersonation state (LAC-10), kept server-side in an HttpOnly cookie signed with
 * AUTH_SECRET and bound to the admin's `sub`. The browser can neither read nor forge it; the BFF only
 * forwards it as `X-Tenant-Id` for a platform admin, and the backend still enforces isPlatformAdmin.
 */
export const ACTIVE_TENANT_COOKIE = "pa_active_tenant";

export type ActiveTenant = { id: string; name: string };

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isTenantId(value: unknown): value is string {
  return typeof value === "string" && UUID_RE.test(value);
}

function secret(): string {
  const value = process.env.AUTH_SECRET;
  if (!value) throw new Error("AUTH_SECRET is not configured");
  return value;
}

function sign(payload: string): string {
  return createHmac("sha256", secret()).update(`active-tenant:${payload}`).digest("base64url");
}

/** Serialises and signs the impersonated tenant for one admin subject. */
export function encodeActiveTenant(sub: string, tenant: ActiveTenant): string {
  const payload = Buffer.from(JSON.stringify({ sub, id: tenant.id, name: tenant.name.slice(0, 200) })).toString("base64url");
  return `${payload}.${sign(payload)}`;
}

/** Verifies signature and subject binding; returns null on any mismatch. */
export function decodeActiveTenant(cookieValue: string | undefined, sub: string | undefined): ActiveTenant | null {
  if (!cookieValue || !sub) return null;
  const [payload, signature] = cookieValue.split(".");
  if (!payload || !signature) return null;
  const expected = Buffer.from(sign(payload));
  const actual = Buffer.from(signature);
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) return null;
  try {
    const parsed = JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as { sub?: string; id?: string; name?: string };
    if (parsed.sub !== sub || !isTenantId(parsed.id)) return null;
    return { id: parsed.id, name: typeof parsed.name === "string" ? parsed.name : "" };
  } catch {
    return null;
  }
}

export function activeTenantCookieOptions() {
  return {
    httpOnly: true,
    sameSite: "lax" as const,
    secure: sessionCookieIsSecure(),
    path: "/",
    // Never outlives the Auth.js session (10 h).
    maxAge: 10 * 60 * 60,
  };
}
