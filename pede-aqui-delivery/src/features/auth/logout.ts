import { userManager } from './oidcConfig'
import { store } from '@/store'
import { clearUser } from '@/store/auth-slice'

/**
 * Ends the user session consistently for both auth paths.
 *
 * - OIDC (PKCE) session with an id_token: clears local state and redirects to
 *   the Keycloak end-session endpoint so the SSO session is terminated too.
 * - ROPC session (primary flow): only an access token is stored, so there is
 *   no id_token_hint to drive end-session; all local artifacts (Redux auth
 *   state, sessionStorage auth_token, oidc-client user store) are cleared.
 *
 * Returns true when the browser is being redirected to Keycloak end-session
 * (callers should not navigate afterwards), false otherwise.
 */
export async function logout(): Promise<boolean> {
  const user = await userManager.getUser().catch(() => null)

  // Clears Redux auth state + sessionStorage auth_token.
  store.dispatch(clearUser())

  if (user?.id_token) {
    try {
      await userManager.signoutRedirect({ id_token_hint: user.id_token })
      return true
    } catch {
      // End-session redirect failed — fall through to local-only cleanup.
    }
  }

  await userManager.removeUser().catch(() => {})
  return false
}
