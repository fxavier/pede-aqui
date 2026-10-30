import { createSlice, PayloadAction } from "@reduxjs/toolkit";

/**
 * Client-side mirror of the BFF session (LAC-10): identity and roles only. No token ever lives in the
 * browser; the impersonated tenant is authoritative server-side (signed HttpOnly cookie) and only
 * mirrored here for display.
 */
interface AuthUser {
  name: string;
  /** Most privileged role, for pages that still branch on a single role. Prefer `roles`. */
  role: string;
  /** All marketplace realm roles of the user. */
  roles: string[];
  tenant: string;
  tenantId: string | null;       // from JWT — null for platform super-admin
  activeTenantId: string | null; // currently impersonating (platform admin only)
  activeTenantName: string | null;
  email?: string;
}

interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

// Loading until AppProviders has asked the BFF for the session (GET /api/session).
const initialState: AuthState = { user: null, isAuthenticated: false, isLoading: true };

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setUser(state, action: PayloadAction<AuthUser | null>) {
      state.user = action.payload;
      state.isAuthenticated = action.payload !== null;
    },
    setLoading(state, action: PayloadAction<boolean>) {
      state.isLoading = action.payload;
    },
    logout(state) {
      state.user = null;
      state.isAuthenticated = false;
    },
    loadFromSession(state, action: PayloadAction<AuthUser | null>) {
      state.user = action.payload;
      state.isAuthenticated = action.payload !== null;
      state.isLoading = false;
    },
    /** Platform admin enters a tenant context (after PUT /api/session/tenant succeeded — see useTenantSwitch). */
    enterTenant(state, action: PayloadAction<{ tenantId: string; tenantName: string }>) {
      if (state.user) {
        state.user.activeTenantId = action.payload.tenantId;
        state.user.activeTenantName = action.payload.tenantName;
      }
    },
    /** Platform admin exits tenant context back to platform view. */
    exitTenant(state) {
      if (state.user) {
        state.user.activeTenantId = null;
        state.user.activeTenantName = null;
      }
    },
  },
});

export const { setUser, setLoading, logout, loadFromSession, enterTenant, exitTenant } = authSlice.actions;
export type { AuthUser, AuthState };
export default authSlice.reducer;
