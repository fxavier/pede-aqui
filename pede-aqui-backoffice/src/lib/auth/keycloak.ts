/**
 * Server-only Keycloak helpers for the Auth.js BFF (LAC-10): endpoint URLs, single-flight refresh-token
 * rotation and access-token claim decoding. Never import from client components.
 */

export type KeycloakTokenSet = {
  accessToken: string;
  refreshToken: string;
  idToken?: string;
  /** Access-token expiry, epoch seconds. */
  expiresAt: number;
};

export function keycloakIssuer(): string {
  const issuer = process.env.AUTH_KEYCLOAK_ISSUER;
  if (!issuer) throw new Error("AUTH_KEYCLOAK_ISSUER is not configured");
  return issuer.replace(/\/$/, "");
}

export function keycloakClientId(): string {
  return process.env.AUTH_KEYCLOAK_ID ?? "delivery-app";
}

/** Decodes (without verifying — it came straight from Keycloak's token endpoint over TLS) a JWT payload. */
export function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const part = jwt.split(".")[1];
  if (!part) return {};
  try {
    return JSON.parse(Buffer.from(part, "base64url").toString("utf8")) as Record<string, unknown>;
  } catch {
    return {};
  }
}

type RefreshEntry = { promise: Promise<KeycloakTokenSet>; settledAt?: number };

// Shared through globalThis so the middleware bundle and the route-handler bundles (separate module
// instances in the same Node process) dedupe against the same map.
const REFRESH_KEY = Symbol.for("pede-aqui.backoffice.keycloak-refresh");
const globalRefresh = globalThis as unknown as Record<symbol, Map<string, RefreshEntry> | undefined>;
const inFlight: Map<string, RefreshEntry> = (globalRefresh[REFRESH_KEY] ??= new Map());

/**
 * How long a completed refresh stays cached under the OLD refresh token. The realm rotates refresh tokens
 * with `refreshTokenMaxReuse: 0`, so parallel requests that still carry the previous session cookie must get
 * the already-rotated result instead of replaying the old (now revoked) refresh token.
 */
const SETTLED_REUSE_MS = 30_000;

/** Refreshes once per refresh token value (single-flight per session), with rotation. */
export function refreshKeycloakTokens(refreshToken: string): Promise<KeycloakTokenSet> {
  const now = Date.now();
  for (const [key, entry] of inFlight) {
    if (entry.settledAt && now - entry.settledAt > SETTLED_REUSE_MS) inFlight.delete(key);
  }
  const existing = inFlight.get(refreshToken);
  if (existing) return existing.promise;

  const entry: RefreshEntry = { promise: doRefresh(refreshToken) };
  entry.promise.then(
    () => {
      entry.settledAt = Date.now();
    },
    () => {
      // Failures are not cached: the caller turns them into a RefreshTokenError session.
      inFlight.delete(refreshToken);
    },
  );
  inFlight.set(refreshToken, entry);
  return entry.promise;
}

async function doRefresh(refreshToken: string): Promise<KeycloakTokenSet> {
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: keycloakClientId(),
    refresh_token: refreshToken,
  });
  if (process.env.AUTH_KEYCLOAK_SECRET) body.set("client_secret", process.env.AUTH_KEYCLOAK_SECRET);

  const response = await fetch(`${keycloakIssuer()}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
    body,
    cache: "no-store",
  });
  const json = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok || typeof json.access_token !== "string") {
    throw new Error(`Keycloak refresh failed: ${response.status} ${String(json.error ?? "")}`.trim());
  }
  return {
    accessToken: json.access_token,
    // Keycloak rotates the refresh token; fall back to the old one only if none was returned.
    refreshToken: typeof json.refresh_token === "string" ? json.refresh_token : refreshToken,
    idToken: typeof json.id_token === "string" ? json.id_token : undefined,
    expiresAt: Math.floor(Date.now() / 1000) + Number(json.expires_in ?? 60),
  };
}

/** RP-initiated logout URL (end_session_endpoint) ending the Keycloak SSO session. */
export function keycloakLogoutUrl(idToken: string | undefined, postLogoutRedirectUri: string): string {
  const url = new URL(`${keycloakIssuer()}/protocol/openid-connect/logout`);
  url.searchParams.set("client_id", keycloakClientId());
  url.searchParams.set("post_logout_redirect_uri", postLogoutRedirectUri);
  if (idToken) url.searchParams.set("id_token_hint", idToken);
  return url.toString();
}
