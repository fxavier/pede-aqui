import { describe, expect, it } from 'vitest'
import { buildCsp, securityHeaders } from './headers'

const opts = { apiBaseUrl: 'http://localhost:8080/api/v1', keycloakUrl: 'http://localhost:8081' }

function directive(csp: string, name: string): string {
  return csp.split('; ').find((d) => d.startsWith(`${name} `)) ?? ''
}

describe('buildCsp', () => {
  it('production policy allows no inline/eval scripts and only the API + Keycloak for XHR', () => {
    const csp = buildCsp(opts)
    expect(directive(csp, 'script-src')).toBe("script-src 'self'")
    expect(directive(csp, 'connect-src')).toBe("connect-src 'self' http://localhost:8080 http://localhost:8081")
    expect(directive(csp, 'frame-src')).toBe("frame-src 'self' http://localhost:8081")
    expect(directive(csp, 'object-src')).toBe("object-src 'none'")
    expect(csp).not.toContain('unsafe-eval')
    expect(csp).not.toContain('ws:')
  })

  it('dev policy only adds what Vite needs (inline preamble, HMR socket, MinIO images)', () => {
    const csp = buildCsp({ ...opts, dev: true })
    expect(directive(csp, 'script-src')).toBe("script-src 'self' 'unsafe-inline'")
    expect(directive(csp, 'connect-src')).toContain('ws:')
    expect(directive(csp, 'img-src')).toContain('http://localhost:9000')
  })

  it('ignores missing or malformed origins', () => {
    expect(directive(buildCsp({ apiBaseUrl: 'not a url' }), 'connect-src')).toBe("connect-src 'self'")
  })
})

describe('securityHeaders', () => {
  it('sets HSTS only outside dev and allows same-origin framing for the silent-renew iframe', () => {
    expect(securityHeaders({ ...opts, dev: true })['Strict-Transport-Security']).toBeUndefined()
    const prod = securityHeaders(opts)
    expect(prod['Strict-Transport-Security']).toMatch(/max-age=\d+/)
    expect(prod['X-Frame-Options']).toBe('SAMEORIGIN')
    expect(prod['X-Content-Type-Options']).toBe('nosniff')
  })
})
