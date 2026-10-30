/**
 * Security headers for the customer SPA (LAC-10). Used by the Vite dev/preview servers and kept as
 * the reference for the production CDN response-headers policy (see docs/local-development.md).
 * Pure module (no DOM/Vite imports) so it can run inside vite.config.ts and in unit tests.
 */

export interface HeaderOptions {
  /** API base URL (VITE_API_BASE_URL); its origin goes into connect-src. */
  apiBaseUrl?: string
  /** Keycloak base URL (VITE_KEYCLOAK_URL); token/end-session calls + the silent-renew iframe. */
  keycloakUrl?: string
  /** Dev server: allows Vite's inline react-refresh preamble, HMR websocket and local MinIO images. */
  dev?: boolean
}

function originOf(url: string | undefined): string | null {
  if (!url) return null
  try {
    return new URL(url).origin
  } catch {
    return null
  }
}

/** Builds the Content-Security-Policy value. */
export function buildCsp({ apiBaseUrl, keycloakUrl, dev = false }: HeaderOptions): string {
  const api = originOf(apiBaseUrl)
  const kc = originOf(keycloakUrl)
  const list = (...xs: (string | null | false)[]) => xs.filter(Boolean).join(' ')

  const directives: Record<string, string> = {
    'default-src': "'self'",
    // Dev only: @vitejs/plugin-react injects an inline preamble script for fast refresh.
    'script-src': list("'self'", dev && "'unsafe-inline'"),
    // Inline style attributes/tags are used by Radix (scroll lock) and React style props.
    'style-src': "'self' 'unsafe-inline' https://fonts.googleapis.com",
    'font-src': "'self' https://fonts.gstatic.com",
    // Product/vendor images come from S3 presigned/public URLs (https) or local MinIO in dev.
    'img-src': list("'self'", 'data:', 'blob:', 'https:', dev && 'http://localhost:9000'),
    'connect-src': list("'self'", api, kc, dev && 'ws:'),
    // Keycloak is framed only for the silent SSO check (prompt=none) on reload.
    'frame-src': list("'self'", kc),
    // 'self' (not 'none'): the silent-renew callback page is the app framed by itself.
    'frame-ancestors': "'self'",
    'form-action': "'self'",
    'base-uri': "'self'",
    'object-src': "'none'",
  }
  return Object.entries(directives)
    .map(([k, v]) => `${k} ${v}`)
    .join('; ')
}

/** Full header set for responses serving the SPA. HSTS is added only outside dev (HTTPS). */
export function securityHeaders(opts: HeaderOptions): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Security-Policy': buildCsp(opts),
    'X-Content-Type-Options': 'nosniff',
    // SAMEORIGIN rather than DENY: the silent-renew callback is loaded in a same-origin iframe.
    'X-Frame-Options': 'SAMEORIGIN',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'Permissions-Policy': 'geolocation=(self), camera=(), microphone=(), payment=()',
  }
  if (!opts.dev) headers['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains'
  return headers
}
