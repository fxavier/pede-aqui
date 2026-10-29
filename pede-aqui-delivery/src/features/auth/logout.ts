import { userManager } from './oidcConfig'
import { store } from '@/store'
import { clearUser } from '@/store/auth-slice'

/**
 * Ends the customer session: clears the UI auth state and the in-memory tokens, then redirects to
 * the Keycloak end-session endpoint (with `id_token_hint`) so the SSO session is terminated too —
 * otherwise the next silent restore would sign the user straight back in.
 *
 * Returns true when the browser is being redirected to Keycloak (callers should not navigate
 * afterwards), false when only the local session could be cleared.
 */
export async function logout(): Promise<boolean> {
  const user = await userManager.getUser().catch(() => null)
  store.dispatch(clearUser())

  if (user?.id_token) {
    try {
      // signoutRedirect also removes the user from the (in-memory) user store.
      await userManager.signoutRedirect({ id_token_hint: user.id_token })
      return true
    } catch {
      // End-session redirect failed — fall through to local-only cleanup.
    }
  }

  await userManager.removeUser().catch(() => {})
  return false
}
