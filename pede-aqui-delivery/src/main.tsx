import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { store } from './store'
import { clearUser, initAuth, setUser } from './store/auth-slice'
import { router } from './app/router'
import { purgeLegacyOrderCache } from './lib/orderHistory'
import { SIGNIN_CALLBACK_PATH, SILENT_CALLBACK_PATH, userManager } from './features/auth/oidcConfig'
import { authSession } from './features/auth/authSession'
import { profileFromUser } from './features/auth/session'
import './index.css'

const path = window.location.pathname

if (path === SILENT_CALLBACK_PATH) {
  // Inside the hidden renew iframe: hand the authorization response to the parent window and stop.
  void userManager.signinSilentCallback().catch(() => {})
} else {
  startApp()
}

function startApp() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { staleTime: 30_000, retry: 1 } },
  })

  // Keep the UI identity in sync with the in-memory OIDC session.
  userManager.events.addUserLoaded((user) => {
    store.dispatch(setUser(profileFromUser(user)))
  })
  userManager.events.addUserUnloaded(() => {
    store.dispatch(clearUser())
  })
  // Proactive renewal ~60 s before expiry, through the same single-flight path as the 401 handler
  // (refresh tokens rotate, so parallel renewals would invalidate the session).
  userManager.events.addAccessTokenExpiring(() => void authSession.renew())

  // The callback route performs its own code exchange; elsewhere restore the session silently
  // (tokens are not persisted, so a reload relies on the Keycloak SSO cookie).
  if (path !== SIGNIN_CALLBACK_PATH) store.dispatch(initAuth())
  // Older builds cached the order history (with addresses and PINs) in localStorage; it now lives only in the API.
  purgeLegacyOrderCache()
  // Older builds kept the raw access token here (ROPC flow); make sure no copy survives an upgrade.
  sessionStorage.removeItem('auth_token')

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Provider store={store}>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </Provider>
    </StrictMode>
  )
}
