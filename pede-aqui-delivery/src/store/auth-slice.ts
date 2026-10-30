import { createSlice, createAsyncThunk, type PayloadAction } from '@reduxjs/toolkit'
import { authSession } from '@/features/auth/authSession'
import { profileFromUser, type SessionProfile } from '@/features/auth/session'

/**
 * UI-facing auth state. Holds identity only — the access/refresh tokens stay inside the in-memory
 * oidc-client-ts user store and are read by the API client on demand (never copied here or into
 * browser storage).
 */
interface AuthState extends SessionProfile {
  status: 'idle' | 'loading' | 'authenticated' | 'unauthenticated'
}

const initialState: AuthState = {
  sub: null,
  displayName: null,
  email: null,
  status: 'idle',
}

/** Restores the session on start-up via the Keycloak SSO cookie (silent prompt=none). */
export const initAuth = createAsyncThunk('auth/init', async (): Promise<SessionProfile | null> => {
  const user = await authSession.restore()
  return user ? profileFromUser(user) : null
})

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setUser(state, action: PayloadAction<SessionProfile>) {
      state.sub = action.payload.sub
      state.displayName = action.payload.displayName
      state.email = action.payload.email
      state.status = 'authenticated'
    },
    clearUser(state) {
      state.sub = null
      state.displayName = null
      state.email = null
      state.status = 'unauthenticated'
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(initAuth.pending, (state) => {
        state.status = 'loading'
      })
      .addCase(initAuth.fulfilled, (state, action) => {
        // A login callback may already have set the user while the restore was in flight.
        if (state.status === 'authenticated' && !action.payload) return
        if (action.payload) {
          Object.assign(state, action.payload, { status: 'authenticated' as const })
        } else {
          state.status = 'unauthenticated'
        }
      })
      .addCase(initAuth.rejected, (state) => {
        if (state.status !== 'authenticated') state.status = 'unauthenticated'
      })
  },
})

export const { setUser, clearUser } = authSlice.actions
export default authSlice.reducer
