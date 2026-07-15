import axios from 'axios'
import { store } from '@/store'
import { clearUser } from '@/store/auth-slice'

const BASE_URL = import.meta.env.VITE_API_BASE_URL as string

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('auth_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Central 401 handling: an expired/invalid session clears local auth state
// (Redux + sessionStorage auth_token) and sends the user back to login.
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      store.dispatch(clearUser())
      if (!window.location.pathname.startsWith('/login')) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.assign(`/login?redirect=${redirect}`)
      }
    }
    return Promise.reject(error)
  },
)
