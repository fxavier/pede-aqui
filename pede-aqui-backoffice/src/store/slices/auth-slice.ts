import { createSlice, PayloadAction } from "@reduxjs/toolkit";

interface AuthUser {
  name: string;
  role: string;
  tenant: string;
  tenantId: string | null;
  email?: string;
  token?: string;
}

interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

const initialState: AuthState = {
  user: null,
  isAuthenticated: false,
  isLoading: false,
};

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
  },
});

export const { setUser, setLoading, logout, loadFromSession } = authSlice.actions;
export type { AuthUser, AuthState };
export default authSlice.reducer;
