import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";
import { decodeJwtPayload, keycloakClientId, keycloakIssuer, refreshKeycloakTokens } from "@/lib/auth/keycloak";
import { toMarketplaceRoles } from "@/lib/auth/roles";
import { sessionCookieIsSecure } from "@/lib/auth/session-cookie";
import { publishServerToken } from "@/lib/auth/token-store";

/**
 * Auth.js (next-auth v5) configuration — the backoffice BFF session (LAC-10).
 *
 * Authorization Code + PKCE (S256) + state against the Keycloak `delivery-app` client. The session is a
 * JWE in an HttpOnly, SameSite=Lax cookie (Secure over https). Access/refresh/id tokens live only inside
 * that encrypted cookie: the `session` callback (what `/api/auth/session` exposes to the browser) carries
 * identity and roles only. Expired access tokens are refreshed in the `jwt` callback with rotation,
 * single-flight per refresh token (see refreshKeycloakTokens).
 */

declare module "next-auth" {
  interface Session {
    sub?: string;
    roles: string[];
    tenantId: string | null;
    error?: "RefreshTokenError";
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    idToken?: string;
    /** Access-token expiry, epoch seconds. */
    expiresAt?: number;
    roles?: string[];
    tenantId?: string | null;
    error?: "RefreshTokenError";
  }
}

/** Refresh a little before expiry so a proxied call never leaves with a token about to lapse. */
const EXPIRY_SKEW_SECONDS = 30;

function claimsFromAccessToken(accessToken: string) {
  const claims = decodeJwtPayload(accessToken);
  const realmAccess = claims.realm_access as { roles?: unknown } | undefined;
  return {
    sub: typeof claims.sub === "string" ? claims.sub : undefined,
    roles: toMarketplaceRoles(realmAccess?.roles),
    tenantId: typeof claims.tenant_id === "string" && claims.tenant_id ? claims.tenant_id : null,
    exp: typeof claims.exp === "number" ? claims.exp : undefined,
  };
}

export const { handlers, auth, signIn, signOut } = NextAuth(() => {
  const clientSecret = process.env.AUTH_KEYCLOAK_SECRET;
  return {
    providers: [
      Keycloak({
        issuer: keycloakIssuer(),
        clientId: keycloakClientId(),
        clientSecret,
        // delivery-app is a public client: PKCE replaces the client secret at the token endpoint.
        client: clientSecret ? undefined : { token_endpoint_auth_method: "none" },
        checks: ["pkce", "state"],
      }),
    ],
    session: {
      strategy: "jwt",
      // Matches the realm's ssoSessionMaxLifespan (10 h); idle expiry is enforced by Keycloak on refresh.
      maxAge: 10 * 60 * 60,
    },
    // HttpOnly + SameSite=Lax are Auth.js defaults; Secure (and the __Secure- prefix) whenever served over https.
    useSecureCookies: sessionCookieIsSecure(),
    pages: { signIn: "/login", error: "/login" },
    callbacks: {
      async jwt({ token, account }) {
        let next = token;
        if (account?.access_token) {
          const claims = claimsFromAccessToken(account.access_token);
          next = {
            ...token,
            sub: claims.sub ?? token.sub,
            accessToken: account.access_token,
            refreshToken: account.refresh_token,
            idToken: account.id_token,
            expiresAt: account.expires_at ?? claims.exp,
            roles: claims.roles,
            tenantId: claims.tenantId,
            error: undefined,
          };
        } else if (token.expiresAt && Date.now() / 1000 < token.expiresAt - EXPIRY_SKEW_SECONDS) {
          next = token;
        } else if (token.refreshToken && !token.error) {
          try {
            const refreshed = await refreshKeycloakTokens(token.refreshToken);
            const claims = claimsFromAccessToken(refreshed.accessToken);
            next = {
              ...token,
              accessToken: refreshed.accessToken,
              refreshToken: refreshed.refreshToken,
              idToken: refreshed.idToken ?? token.idToken,
              expiresAt: refreshed.expiresAt,
              roles: claims.roles,
              tenantId: claims.tenantId,
              error: undefined,
            };
          } catch {
            // SSO session ended (idle/max lifespan, logout elsewhere, revoked token): force a new sign-in.
            next = { ...token, accessToken: undefined, refreshToken: undefined, error: "RefreshTokenError" };
          }
        }
        publishServerToken(next);
        return next;
      },
      session({ session, token }) {
        // NEVER copy accessToken/refreshToken/idToken here — this object is served to the browser.
        return {
          ...session,
          sub: token.sub,
          roles: token.roles ?? [],
          tenantId: token.tenantId ?? null,
          error: token.error,
        };
      },
    },
  };
});
