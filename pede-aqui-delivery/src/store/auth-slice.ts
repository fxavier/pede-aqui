import { createSlice, createAsyncThunk } from '@reduxjs/toolkit'
import { userManager } from '@/features/auth/oidcConfig'

interface AuthState {
  token: string | null
  sub: string | null
  displayName: string | null
  email: string | null
  status: 'idle' | 'loading' | 'authenticated' | 'unauthenticated'
}

const initialState: AuthState = {
  token: null,
  sub: null,
  displayName: null,
  email: null,
  status: 'idle',
}

function parseJwtPayload(token: string) {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(b64)) as Record<string, unknown>
  } catch {
    return null
  }
}

export const initAuth = createAsyncThunk('auth/init', async () => {
  // Try OIDC user first (PKCE flow)
  let user = await userManager.getUser()
  if (user?.expired && user.refresh_token) {
    try { user = await userManager.signinSilent() } catch { user = null }
  }
  if (user && !user.expired) return user

  // Fall back to direct ROPC token stored in sessionStorage
  const token = sessionStorage.getItem('auth_token')
  if (token) {
    const profile = parseJwtPayload(token)
    const exp = profile?.exp as number | undefined
    if (profile && (!exp || exp * 1000 > Date.now())) {
      return { access_token: token, profile, expired: false }
    }
    sessionStorage.removeItem('auth_token')
  }

  return null
})

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setUser(state, action) {
      const { access_token, profile } = action.payload
      state.token = access_token
      state.sub = profile.sub ?? null
      state.displayName = (profile.name ?? profile.preferred_username) as string | null
      state.email = (profile.email ?? null) as string | null
      state.status = 'authenticated'
      sessionStorage.setItem('auth_token', access_token)
    },
    clearUser(state) {
      state.token = null
      state.sub = null
      state.displayName = null
      state.email = null
      state.status = 'unauthenticated'
      sessionStorage.removeItem('auth_token')
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(initAuth.pending, (state) => { state.status = 'loading' })
      .addCase(initAuth.fulfilled, (state, action) => {
        const user = action.payload
        if (user && !user.expired) {
          state.token = user.access_token
          state.sub = (user.profile.sub as string | null) ?? null
          state.displayName = ((user.profile.name ?? user.profile.preferred_username) as string | null) ?? null
          state.email = (user.profile.email as string | null) ?? null
          state.status = 'authenticated'
          sessionStorage.setItem('auth_token', user.access_token)
        } else {
          state.status = 'unauthenticated'
          sessionStorage.removeItem('auth_token')
        }
      })
      .addCase(initAuth.rejected, (state) => { state.status = 'unauthenticated' })
  },
})

export const { setUser, clearUser } = authSlice.actions
export default authSlice.reducer
