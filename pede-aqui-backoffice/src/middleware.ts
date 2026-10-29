import { NextResponse, type NextRequest } from "next/server";
import { canAccess } from "@/lib/auth/roles";
import { readSessionToken } from "@/lib/auth/session-cookie";

/**
 * Server-side route guard (LAC-10). Reads the encrypted Auth.js session cookie (decrypt only — token
 * refresh happens in the Node route handlers, never here) and enforces the backoffice route table:
 * no session → /login, wrong role → 403 page. API routes enforce their own auth (BFF returns 401).
 */

const PUBLIC_PREFIXES = ["/login", "/register", "/403", "/api/", "/_next/", "/favicon.ico", "/imported-screens/", "/reference-screens/"];

/** The /screens HTML prototype viewer is a design reference; hidden in production unless explicitly enabled. */
const screensEnabled = process.env.NODE_ENV !== "production" || process.env.NEXT_PUBLIC_ENABLE_SCREENS === "true";

export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (!screensEnabled && (pathname === "/screens" || pathname.startsWith("/screens/"))) {
    return NextResponse.rewrite(new URL("/404", request.url), { status: 404 });
  }

  if (PUBLIC_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(prefix))) {
    return NextResponse.next();
  }

  const token = await readSessionToken(request);
  if (!token || token.error || !token.refreshToken) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("callbackUrl", `${pathname}${search}`);
    if (token?.error) loginUrl.searchParams.set("expired", "1");
    return NextResponse.redirect(loginUrl, 307);
  }

  if (!canAccess(pathname, token.roles ?? [], token.tenantId)) {
    return NextResponse.rewrite(new URL("/403", request.url), { status: 403 });
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
