import { createSlice, PayloadAction } from "@reduxjs/toolkit";

interface AuthUser {
  name: string;
  role: string;
  tenant: string;
  tenantId: string | null;       // from JWT — null for platform super-admin
  activeTenantId: string | null; // currently impersonating (platform admin only)
  activeTenantName: string | null;
  email?: string;
  token?: string;
}

interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

const getInitialState = (): AuthState => {
  if (typeof window !== 'undefined') {
    const token = sessionStorage.getItem('auth_token');
    return { user: null, isAuthenticated: false, isLoading: token !== null };
  }
  return { user: null, isAuthenticated: false, isLoading: false };
};

const initialState: AuthState = getInitialState();

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
    /** Platform admin enters a tenant context. */
    enterTenant(state, action: PayloadAction<{ tenantId: string; tenantName: string }>) {
      if (state.user) {
        state.user.activeTenantId = action.payload.tenantId;
        state.user.activeTenantName = action.payload.tenantName;
      }
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('tenant_id', action.payload.tenantId);
      }
    },
    /** Platform admin exits tenant context back to platform view. */
    exitTenant(state) {
      if (state.user) {
        state.user.activeTenantId = null;
        state.user.activeTenantName = null;
      }
      if (typeof window !== 'undefined') {
        sessionStorage.removeItem('tenant_id');
      }
    },
  },
});

export const { setUser, setLoading, logout, loadFromSession, enterTenant, exitTenant } = authSlice.actions;
export type { AuthUser, AuthState };
export default authSlice.reducer;
