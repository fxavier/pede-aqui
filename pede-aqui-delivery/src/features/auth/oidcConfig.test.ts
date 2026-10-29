import { describe, expect, it } from 'vitest'
import { InMemoryWebStorage, UserManager, User } from 'oidc-client-ts'
import { buildOidcSettings } from './oidcConfig'

const env = { keycloakUrl: 'http://kc.test', realm: 'delivery', clientId: 'pede-aqui-web' }

describe('buildOidcSettings', () => {
  it('uses the code flow against the realm with callback/silent/logout URIs on the app origin', () => {
    const s = buildOidcSettings(env, 'http://localhost:5173')
    expect(s).toMatchObject({
      authority: 'http://kc.test/realms/delivery',
      client_id: 'pede-aqui-web',
      response_type: 'code',
      redirect_uri: 'http://localhost:5173/auth/callback',
      silent_redirect_uri: 'http://localhost:5173/auth/silent-callback',
      post_logout_redirect_uri: 'http://localhost:5173/',
      automaticSilentRenew: false,
    })
  })

  it('keeps tokens in memory only — nothing lands in sessionStorage or localStorage', async () => {
    sessionStorage.clear()
    localStorage.clear()
    const manager = new UserManager(buildOidcSettings(env, 'http://localhost:5173', sessionStorage))
    expect((manager.settings.userStore as unknown as { _store: unknown })._store).toBeInstanceOf(InMemoryWebStorage)

    await manager.storeUser(
      new User({ access_token: 'AT-secret', refresh_token: 'RT-secret', id_token: 'IT-secret', token_type: 'Bearer', profile: { sub: 'u', iss: 'i', aud: 'a', exp: 0, iat: 0 } }),
    )

    expect((await manager.getUser())?.access_token).toBe('AT-secret')
    const dump = JSON.stringify({ ...sessionStorage }) + JSON.stringify({ ...localStorage }) + document.cookie
    expect(dump).not.toContain('secret')
    expect(sessionStorage.length + localStorage.length).toBe(0)
  })
})
