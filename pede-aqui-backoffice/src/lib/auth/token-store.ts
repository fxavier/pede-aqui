import { AsyncLocalStorage } from "node:async_hooks";
import type { JWT } from "next-auth/jwt";

/**
 * Request-scoped side channel for the server-only token set (LAC-10).
 *
 * The Auth.js `session` callback output is what `/api/auth/session` returns to the browser, so the
 * access/refresh/id tokens must never be copied into it. Instead the `jwt` callback (which runs inside
 * the same async context as the BFF handler or server action that triggered it) publishes the resolved —
 * possibly just-refreshed — token here, and server code reads it back with {@link withServerToken}.
 */
type TokenSlot = { token: JWT | null };

const storage = new AsyncLocalStorage<TokenSlot>();

/** Called from the jwt callback: exposes the resolved token to the surrounding request, if any. */
export function publishServerToken(token: JWT | null): void {
  const slot = storage.getStore();
  if (slot) slot.token = token;
}

/**
 * Runs `fn` (which must trigger the Auth.js session resolution, e.g. `auth()` or an `auth(handler)` wrapper)
 * and returns its result together with the token the jwt callback resolved during it.
 */
export async function withServerToken<T>(fn: () => Promise<T>): Promise<{ result: T; token: JWT | null }> {
  const slot: TokenSlot = { token: null };
  const result = await storage.run(slot, fn);
  return { result, token: slot.token };
}

/** Reads the token resolved so far in the current request (inside an `auth(handler)` wrapper). */
export function currentServerToken(): JWT | null {
  return storage.getStore()?.token ?? null;
}

/** Runs `fn` with a fresh token slot so {@link currentServerToken} works inside it. */
export function runWithTokenSlot<T>(fn: () => T): T {
  return storage.run({ token: null }, fn);
}
