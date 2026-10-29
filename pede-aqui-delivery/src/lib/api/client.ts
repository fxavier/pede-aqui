import axios from 'axios'
import { store } from '@/store'
import { clearUser } from '@/store/auth-slice'
import { authSession } from '@/features/auth/authSession'
import { installAuthInterceptors } from './authInterceptors'

const BASE_URL = import.meta.env.VITE_API_BASE_URL as string

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

let sessionLostHandled = false

// Token comes from the in-memory oidc-client-ts session (never from browser storage).
// 401 → one single-flight renewal + replay; if that fails the session is cleared and the user is
// sent to the Keycloak login, returning to the current page afterwards.
installAuthInterceptors(apiClient, {
  getAccessToken: () => authSession.getAccessToken(),
  renew: () => authSession.renew().then((user) => user?.access_token ?? null),
  onSessionLost: () => {
    if (sessionLostHandled) return
    sessionLostHandled = true
    store.dispatch(clearUser())
    const here = window.location.pathname + window.location.search
    void authSession
      .clear()
      .then(() => authSession.login(here))
      .catch(() => {
        sessionLostHandled = false
      })
  },
})
