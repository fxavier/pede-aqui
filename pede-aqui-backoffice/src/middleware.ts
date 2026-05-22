import { NextResponse } from 'next/server';

export function middleware() {
  // Middleware cannot access sessionStorage (server-side).
  // Route protection is handled client-side in app-shell.tsx.
  // This middleware only handles the /login redirect for already-authenticated users
  // by checking a cookie (if we set one) — but since we use sessionStorage,
  // we cannot check auth here. Leave as pass-through for now.
  // TODO: migrate to httpOnly cookie for proper SSR auth guard.
  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
};
