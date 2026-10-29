import { describe, expect, it, vi } from 'vitest'
import type { User } from 'oidc-client-ts'
import { createAuthSession, profileFromUser, safeRedirect, type SessionUserManager } from './session'

function fakeUser(token: string, expired = false): User {
  return {
    access_token: token,
    expired,
    profile: { sub: 'u-1', name: 'Ana Silva', email: 'ana@example.com' },
  } as unknown as User
}

function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function fakeManager(overrides: Partial<SessionUserManager> = {}): SessionUserManager {
  return {
    getUser: vi.fn().mockResolvedValue(null),
    signinSilent: vi.fn().mockResolvedValue(fakeUser('new')),
    signinRedirect: vi.fn().mockResolvedValue(undefined),
    removeUser: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
}

describe('authSession.renew (single-flight)', () => {
  it('shares one signinSilent call between concurrent callers', async () => {
    const pending = deferred<User | null>()
    const manager = fakeManager({ signinSilent: vi.fn().mockReturnValue(pending.promise) })
    const session = createAuthSession(manager)

    const calls = [session.renew(), session.renew(), session.renew()]
    pending.resolve(fakeUser('rotated'))
    const users = await Promise.all(calls)

    expect(manager.signinSilent).toHaveBeenCalledTimes(1)
    expect(users.map((u) => u?.access_token)).toEqual(['rotated', 'rotated', 'rotated'])
  })

  it('starts a fresh renewal once the previous one settled', async () => {
    const manager = fakeManager()
    const session = createAuthSession(manager)
    await session.renew()
    await session.renew()
    expect(manager.signinSilent).toHaveBeenCalledTimes(2)
  })

  it('resolves null (never rejects) when renewal fails, and allows a retry afterwards', async () => {
    const manager = fakeManager({
      signinSilent: vi.fn().mockRejectedValueOnce(new Error('login_required')).mockResolvedValue(fakeUser('ok')),
    })
    const session = createAuthSession(manager)
    await expect(session.renew()).resolves.toBeNull()
    await expect(session.renew()).resolves.toMatchObject({ access_token: 'ok' })
  })
})

describe('authSession.renew (rotation conflict fallback)', () => {
  it('falls back to the SSO iframe once when the refresh token was rotated away', async () => {
    const held = { ...fakeUser('old', true), refresh_token: 'rt-used-by-other-tab' } as unknown as User
    const signinSilent = vi.fn()
      .mockRejectedValueOnce(new Error('invalid_grant'))
      .mockResolvedValueOnce(fakeUser('via-iframe'))
    const manager = fakeManager({ getUser: vi.fn().mockResolvedValue(held), signinSilent })
    await expect(createAuthSession(manager).renew()).resolves.toMatchObject({ access_token: 'via-iframe' })
    expect(signinSilent).toHaveBeenNthCalledWith(1, undefined)
    expect(signinSilent).toHaveBeenNthCalledWith(2, { forceIframeAuth: true })
  })

  it('does not retry the iframe when the first attempt already was the iframe (no refresh token)', async () => {
    const signinSilent = vi.fn().mockRejectedValue(new Error('login_required'))
    const manager = fakeManager({ signinSilent })
    await expect(createAuthSession(manager).renew()).resolves.toBeNull()
    expect(signinSilent).toHaveBeenCalledTimes(1)
  })
})

describe('authSession.getAccessToken', () => {
  it('returns null for anonymous visitors without trying to renew', async () => {
    const manager = fakeManager()
    await expect(createAuthSession(manager).getAccessToken()).resolves.toBeNull()
    expect(manager.signinSilent).not.toHaveBeenCalled()
  })

  it('returns the held token while it is valid', async () => {
    const manager = fakeManager({ getUser: vi.fn().mockResolvedValue(fakeUser('current')) })
    await expect(createAuthSession(manager).getAccessToken()).resolves.toBe('current')
    expect(manager.signinSilent).not.toHaveBeenCalled()
  })

  it('renews an expired token once even with many concurrent requests', async () => {
    const pending = deferred<User | null>()
    const manager = fakeManager({
      getUser: vi.fn().mockResolvedValue(fakeUser('stale', true)),
      signinSilent: vi.fn().mockReturnValue(pending.promise),
    })
    const session = createAuthSession(manager)
    const tokens = Promise.all([session.getAccessToken(), session.getAccessToken()])
    await Promise.resolve()
    await Promise.resolve()
    pending.resolve(fakeUser('fresh'))
    await expect(tokens).resolves.toEqual(['fresh', 'fresh'])
    expect(manager.signinSilent).toHaveBeenCalledTimes(1)
  })
})

describe('authSession.login', () => {
  it('starts the PKCE redirect with a safe target and the login hint', async () => {
    const manager = fakeManager()
    await createAuthSession(manager).login('/checkout', 'ana@example.com')
    expect(manager.signinRedirect).toHaveBeenCalledWith({ state: { redirect: '/checkout' }, login_hint: 'ana@example.com' })
  })

  it('never round-trips an external redirect target', async () => {
    const manager = fakeManager()
    await createAuthSession(manager).login('https://evil.example/x')
    expect(manager.signinRedirect).toHaveBeenCalledWith({ state: { redirect: '/' } })
  })
})

describe('helpers', () => {
  it('safeRedirect keeps relative app paths only', () => {
    expect(safeRedirect('/orders/1?x=1')).toBe('/orders/1?x=1')
    expect(safeRedirect('//evil.example')).toBe('/')
    expect(safeRedirect('/\\evil.example')).toBe('/')
    expect(safeRedirect('javascript:alert(1)')).toBe('/')
    expect(safeRedirect(null)).toBe('/')
  })

  it('profileFromUser exposes identity, not tokens', () => {
    const profile = profileFromUser(fakeUser('secret'))
    expect(profile).toEqual({ sub: 'u-1', displayName: 'Ana Silva', email: 'ana@example.com' })
    expect(JSON.stringify(profile)).not.toContain('secret')
  })
})
