import { NextResponse, NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  const pathname = request.nextUrl.pathname;

  // Define public path prefixes that are always allowed without a token
  const publicPaths = ['/login', '/register', '/api/', '/_next/', '/favicon.ico'];

  // Check if the current pathname starts with any public prefix
  const isPublicPath = publicPaths.some(path => pathname.startsWith(path));

  if (isPublicPath) {
    return NextResponse.next();
  }

  // Check for auth_token cookie
  const authToken = request.cookies.get('auth_token');

  if (!authToken || !authToken.value) {
    // Redirect to login if no auth token on protected route
    return NextResponse.redirect(new URL('/login', request.url), 307);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
};
