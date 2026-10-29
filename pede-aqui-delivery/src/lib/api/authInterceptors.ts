import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

/** Callbacks the interceptors need from the auth session. */
export interface AuthInterceptorDeps {
  /** Current access token (renewed first when expired), or null for anonymous calls. */
  getAccessToken(): Promise<string | null>
  /** Single-flight session renewal; resolves to the new access token or null when renewal failed. */
  renew(): Promise<string | null>
  /** Called when the session is gone (clear state + start a new login). */
  onSessionLost(): void
}

type RetriableConfig = InternalAxiosRequestConfig & { _authRetried?: boolean }

/**
 * Wires bearer-token auth into an Axios instance.
 *
 * - Request: attaches `Authorization: Bearer <token>` taken from the in-memory session.
 * - Response 401: performs ONE renewal (shared with any concurrent 401s through the single-flight
 *   `renew`) and replays the request with the new token; a second 401, or a failed renewal, ends
 *   the session via `onSessionLost` (which redirects to the Keycloak login).
 */
export function installAuthInterceptors(client: AxiosInstance, deps: AuthInterceptorDeps): void {
  client.interceptors.request.use(async (config) => {
    if (config.headers.Authorization) return config
    const token = await deps.getAccessToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  })

  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (!axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) {
        return Promise.reject(error)
      }
      const config = error.config as RetriableConfig
      if (config._authRetried) {
        deps.onSessionLost()
        return Promise.reject(error)
      }
      const token = await deps.renew()
      if (!token) {
        deps.onSessionLost()
        return Promise.reject(error)
      }
      config._authRetried = true
      config.headers.Authorization = `Bearer ${token}`
      return client.request(config)
    },
  )
}
