"use client";

import * as React from "react";
import { Provider as ReduxProvider } from "react-redux";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { makeStore, type AppStore } from "@/store/store";
import { loadFromSession, setLoading } from "@/store/slices/auth-slice";
import { fetchSession, toAuthUser } from "@/lib/auth/client";

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

  // Rehydrates auth state from the BFF session (HttpOnly cookie; LAC-10). No token is read or stored here.
  React.useEffect(() => {
    const store = storeRef.current;
    if (!store) return;
    fetchSession()
      .then((info) => store.dispatch(loadFromSession(info ? toAuthUser(info) : null)))
      .catch(() => store.dispatch(setLoading(false)));
  }, []);

  return (
    <ReduxProvider store={storeRef.current}>
      <QueryClientProvider client={queryClientRef.current}>{children}</QueryClientProvider>
    </ReduxProvider>
  );
}
