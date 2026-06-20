"use client";

import * as React from "react";
import { Provider as ReduxProvider } from "react-redux";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { makeStore, type AppStore } from "@/store/store";
import { loadFromSession, setLoading } from "@/store/slices/auth-slice";
import { authService } from "@/lib/api/services";

export function AppProviders({ children }: { children: React.ReactNode }) {
  const storeRef = React.useRef<AppStore | null>(null);
  const queryClientRef = React.useRef<QueryClient | null>(null);

  if (!storeRef.current) storeRef.current = makeStore();
  if (!queryClientRef.current) {
    queryClientRef.current = new QueryClient({
      defaultOptions: {
        queries: { staleTime: 60_000, refetchOnWindowFocus: false, retry: 1, gcTime: Infinity },
      },
    });
  }

  React.useEffect(() => {
    const token = sessionStorage.getItem('auth_token');
    if (!token || !storeRef.current) return;

    authService.getMe()
      .then((data) => {
        const jwtTenantId: string | null = data.tenantId ?? null;
        const isPlatformAdmin = !jwtTenantId && data.roles.includes('ADMIN');

        if (jwtTenantId) {
          // Regular tenant user — always sync tenant_id from JWT
          sessionStorage.setItem('tenant_id', jwtTenantId);
        } else if (!isPlatformAdmin) {
          // Non-admin without a tenant — clear stale value
          sessionStorage.removeItem('tenant_id');
        }
        // Platform admin: preserve any existing tenant_id (active impersonation)

        const activeTenantId = isPlatformAdmin
          ? (sessionStorage.getItem('tenant_id') ?? null)
          : jwtTenantId;

        storeRef.current!.dispatch(loadFromSession({
          name: data.displayName,
          role: data.roles[0] || '',
          tenant: data.tenantId ?? 'Pede Aqui Platform',
          tenantId: jwtTenantId,
          activeTenantId,
          activeTenantName: null, // tenant name not available from /me; restored on next enter
          email: data.email,
          token,
        }));
      })
      .catch(() => {
        sessionStorage.removeItem('auth_token');
        sessionStorage.removeItem('tenant_id');
        storeRef.current!.dispatch(setLoading(false));
      });
  }, []);

  return (
    <ReduxProvider store={storeRef.current}>
      <QueryClientProvider client={queryClientRef.current}>{children}</QueryClientProvider>
    </ReduxProvider>
  );
}
