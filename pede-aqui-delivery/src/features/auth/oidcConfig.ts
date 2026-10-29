import { InMemoryWebStorage, UserManager, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'

/** Path Keycloak redirects back to after the interactive (top-level) login. */
export const SIGNIN_CALLBACK_PATH = '/auth/callback'
/** Path loaded inside the hidden iframe used to restore the SSO session (prompt=none). */
export const SILENT_CALLBACK_PATH = '/auth/silent-callback'

interface OidcEnv {
  keycloakUrl: string
  realm: string
  clientId: string
}

/**
 * Builds the oidc-client-ts settings for the customer web app (Authorization Code + PKCE S256).
 *
 * - Tokens (`userStore`) live only in memory: nothing token-bearing is written to
 *   sessionStorage/localStorage/cookies. A reload restores the session through the Keycloak SSO
 *   cookie (silent iframe), not from browser storage.
 * - Only the transient sign-in state (PKCE code_verifier + `state`, deleted after the callback) is
 *   kept in sessionStorage, because it must survive the redirect to Keycloak and back.
 * - Automatic renewal is OFF on purpose: refresh tokens rotate (single use), so every renewal goes
 *   through the single-flight `authSession.renew()` instead of racing timers.
 */
export function buildOidcSettings(env: OidcEnv, origin: string, stateStorage?: Storage): UserManagerSettings {
  return {
    authority: `${env.keycloakUrl}/realms/${env.realm}`,
    client_id: env.clientId,
    redirect_uri: `${origin}${SIGNIN_CALLBACK_PATH}`,
    silent_redirect_uri: `${origin}${SILENT_CALLBACK_PATH}`,
    post_logout_redirect_uri: `${origin}/`,
    response_type: 'code',
    scope: 'openid profile email',
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    ...(stateStorage ? { stateStore: new WebStorageStateStore({ store: stateStorage }) } : {}),
    automaticSilentRenew: false,
    silentRequestTimeoutInSeconds: 5,
    // Profile comes from the id_token; the userinfo round-trip adds nothing the app uses.
    loadUserInfo: false,
  }
}

export const userManager = new UserManager(
  buildOidcSettings(
    {
      keycloakUrl: import.meta.env.VITE_KEYCLOAK_URL as string,
      realm: import.meta.env.VITE_KEYCLOAK_REALM as string,
      clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID as string,
    },
    window.location.origin,
    window.sessionStorage,
  ),
)
