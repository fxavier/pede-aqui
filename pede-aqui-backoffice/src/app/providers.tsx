"use client";

import * as React from "react";
import { Provider as ReduxProvider } from "react-redux";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { makeStore, type AppStore } from "@/store/store";
import { loadFromSession } from "@/store/slices/auth-slice";
import { authService } from "@/lib/api/services";

export function AppProviders({ children }: { children: React.ReactNode }) {
  const storeRef = React.useRef<AppStore | null>(null);
  const queryClientRef = React.useRef<QueryClient | null>(null);

  if (!storeRef.current) {
    storeRef.current = makeStore();
  }

  if (!queryClientRef.current) {
    queryClientRef.current = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 60_000,
          refetchOnWindowFocus: false,
          retry: 1,
          gcTime: Infinity,
        },
      },
    });
  }

  // Session restoration effect
  React.useEffect(() => {
    const token = sessionStorage.getItem('auth_token');
    if (token && storeRef.current) {
      authService.getMe()
        .then((data) => {
          storeRef.current!.dispatch(loadFromSession({
            name: data.displayName,
            role: data.roles[0] || 'Admin',
            tenant: 'Pede Aqui',
            email: data.email,
            token,
          }));
        })
        .catch(() => {
          // Session expired or invalid, clear token
          sessionStorage.removeItem('auth_token');
        });
    }
  }, []);

  return (
    <ReduxProvider store={storeRef.current}>
      <QueryClientProvider client={queryClientRef.current}>{children}</QueryClientProvider>
    </ReduxProvider>
  );
}
