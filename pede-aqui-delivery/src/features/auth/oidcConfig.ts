import { UserManager, WebStorageStateStore } from 'oidc-client-ts'

const KC_URL = import.meta.env.VITE_KEYCLOAK_URL as string
const REALM = import.meta.env.VITE_KEYCLOAK_REALM as string
const CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID as string

export const userManager = new UserManager({
  authority: `${KC_URL}/realms/${REALM}`,
  client_id: CLIENT_ID,
  redirect_uri: `${window.location.origin}/auth/callback`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: sessionStorage }),
  automaticSilentRenew: false,
})
