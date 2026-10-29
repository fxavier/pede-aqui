import { getToken, type JWT } from "next-auth/jwt";

/**
 * Read-only access to the Auth.js session cookie (decrypt only: no callbacks, no refresh, no cookie writes).
 * Edge-safe, so the middleware uses it for route guards; logout uses it to recover the id_token.
 * Must match the cookie settings in src/auth.ts (`useSecureCookies`).
 */
export function sessionCookieIsSecure(): boolean {
  return (process.env.AUTH_URL ?? "").startsWith("https://") || process.env.NODE_ENV === "production";
}

export function sessionCookieName(): string {
  return `${sessionCookieIsSecure() ? "__Secure-" : ""}authjs.session-token`;
}

export async function readSessionToken(req: Request): Promise<JWT | null> {
  const secret = process.env.AUTH_SECRET;
  if (!secret) return null;
  const secureCookie = sessionCookieIsSecure();
  const cookieName = sessionCookieName();
  // Cookie-only: strip any Authorization header so a bearer value can never stand in for the session cookie.
  const headers = new Headers({ cookie: req.headers.get("cookie") ?? "" });
  return getToken({ req: { headers }, secret, secureCookie, cookieName, salt: cookieName });
}
