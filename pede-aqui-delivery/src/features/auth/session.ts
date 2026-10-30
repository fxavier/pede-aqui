import type { SigninRedirectArgs, User } from 'oidc-client-ts'

/** The slice of oidc-client-ts `UserManager` the session needs (kept narrow so tests can fake it). */
export interface SessionUserManager {
  getUser(): Promise<User | null>
  signinSilent(args?: { forceIframeAuth?: boolean }): Promise<User | null>
  signinRedirect(args?: SigninRedirectArgs): Promise<void>
  removeUser(): Promise<void>
}

/** Identity fields the UI shows; never the tokens themselves. */
export interface SessionProfile {
  sub: string | null
  displayName: string | null
  email: string | null
}

/** State round-tripped through Keycloak so the callback knows where to land. */
export interface LoginState {
  redirect?: string
}

/** Extracts the display profile from an OIDC user (id_token claims). */
export function profileFromUser(user: Pick<User, 'profile'>): SessionProfile {
  const p = user.profile
  return {
    sub: (p.sub as string | undefined) ?? null,
    displayName: ((p.name ?? p.preferred_username) as string | undefined) ?? null,
    email: (p.email as string | undefined) ?? null,
  }
}

/** Only same-app relative paths are accepted as post-login targets (no open redirect). */
export function safeRedirect(target: string | null | undefined): string {
  if (!target || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) return '/'
  return target
}

/**
 * Customer web session over an oidc-client-ts UserManager whose tokens live only in memory.
 *
 * Keycloak rotates refresh tokens (each one is single-use), so two concurrent refreshes would make
 * the second fail and kill the session. `renew()` is therefore single-flight: every caller (the
 * token-expiring timer, the request interceptor, the 401 handler) shares one in-flight renewal.
 */
export function createAuthSession(manager: SessionUserManager) {
  let inFlight: Promise<User | null> | null = null

  /**
   * Renews the session once, sharing the in-flight attempt with concurrent callers. Uses the
   * refresh token when one is held, otherwise a prompt=none iframe against the Keycloak SSO cookie.
   * Resolves to null (never rejects) when the session cannot be renewed.
   */
  function renew(): Promise<User | null> {
    if (!inFlight) {
      inFlight = doRenew().finally(() => {
        inFlight = null
      })
    }
    return inFlight
  }

  async function attempt(args?: { forceIframeAuth: boolean }): Promise<User | null> {
    try {
      const user = await manager.signinSilent(args)
      return user && !user.expired ? user : null
    } catch {
      return null
    }
  }

  async function doRenew(): Promise<User | null> {
    const held = await manager.getUser().catch(() => null)
    const renewed = await attempt()
    if (renewed || !held?.refresh_token) return renewed
    // The refresh token may have been rotated away by another tab sharing the same Keycloak client
    // session (invalid_grant); the SSO cookie can still mint a fresh token set via the iframe.
    return attempt({ forceIframeAuth: true })
  }

  /** Returns a usable access token, renewing first when the held one has expired; null when anonymous. */
  async function getAccessToken(): Promise<string | null> {
    const user = await manager.getUser().catch(() => null)
    if (!user) return null
    if (!user.expired) return user.access_token
    const renewed = await renew()
    return renewed?.access_token ?? null
  }

  /** Restores the session on app start (tokens are not persisted, so this relies on Keycloak SSO). */
  async function restore(): Promise<User | null> {
    const user = await manager.getUser().catch(() => null)
    if (user && !user.expired) return user
    return renew()
  }

  /** Starts the Authorization Code + PKCE redirect to the Keycloak login page. */
  function login(redirect?: string, loginHint?: string): Promise<void> {
    const state: LoginState = { redirect: safeRedirect(redirect) }
    return manager.signinRedirect({ state, ...(loginHint ? { login_hint: loginHint } : {}) })
  }

  /** Drops the in-memory user (local session end; Keycloak end-session is done by `logout`). */
  function clear(): Promise<void> {
    return manager.removeUser().catch(() => {})
  }

  return { renew, getAccessToken, restore, login, clear }
}

export type AuthSession = ReturnType<typeof createAuthSession>
