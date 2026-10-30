import { describe, expect, it, vi } from 'vitest'
import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { installAuthInterceptors, type AuthInterceptorDeps } from './authInterceptors'

/** Axios adapter that answers 200 only for the tokens in `valid`, 401 otherwise; records auth headers. */
function adapterAccepting(valid: string[]) {
  const seen: (string | undefined)[] = []
  const adapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
    const auth = config.headers.Authorization as string | undefined
    seen.push(auth)
    const ok = auth !== undefined && valid.includes(auth.replace('Bearer ', ''))
    const response = { data: ok ? 'ok' : 'nope', status: ok ? 200 : 401, statusText: '', headers: {}, config }
    if (ok) return response
    throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response)
  }
  return { adapter, seen }
}

function setup(valid: string[], deps: Partial<AuthInterceptorDeps> = {}) {
  const { adapter, seen } = adapterAccepting(valid)
  const client = axios.create({ adapter })
  const full: AuthInterceptorDeps = {
    getAccessToken: vi.fn().mockResolvedValue('old'),
    renew: vi.fn().mockResolvedValue('new'),
    onSessionLost: vi.fn(),
    ...deps,
  }
  installAuthInterceptors(client, full)
  return { client, seen, deps: full }
}

describe('installAuthInterceptors', () => {
  it('attaches the in-memory access token', async () => {
    const { client, seen } = setup(['old'])
    await expect(client.get('/x')).resolves.toMatchObject({ data: 'ok' })
    expect(seen).toEqual(['Bearer old'])
  })

  it('sends anonymous requests without an Authorization header', async () => {
    const { client, seen } = setup([], { getAccessToken: vi.fn().mockResolvedValue(null) })
    await client.get('/public').catch(() => {})
    expect(seen[0]).toBeUndefined()
  })

  it('on 401 renews once and replays the request with the new token', async () => {
    const { client, seen, deps } = setup(['new'])
    await expect(client.get('/x')).resolves.toMatchObject({ data: 'ok' })
    expect(deps.renew).toHaveBeenCalledTimes(1)
    expect(seen).toEqual(['Bearer old', 'Bearer new'])
    expect(deps.onSessionLost).not.toHaveBeenCalled()
  })

  it('a second 401 after the replay ends the session instead of looping', async () => {
    const { client, seen, deps } = setup([])
    await expect(client.get('/x')).rejects.toMatchObject({ response: { status: 401 } })
    expect(deps.renew).toHaveBeenCalledTimes(1)
    expect(seen).toHaveLength(2)
    expect(deps.onSessionLost).toHaveBeenCalledTimes(1)
  })

  it('a failed renewal ends the session without replaying', async () => {
    const { client, seen, deps } = setup([], { renew: vi.fn().mockResolvedValue(null) })
    await expect(client.get('/x')).rejects.toBeInstanceOf(AxiosError)
    expect(seen).toHaveLength(1)
    expect(deps.onSessionLost).toHaveBeenCalledTimes(1)
  })

  it('does not treat other errors as session loss', async () => {
    const client = axios.create({
      adapter: async (config) => {
        throw new AxiosError('boom', 'ERR_BAD_RESPONSE', config, null, {
          data: null, status: 500, statusText: '', headers: {}, config,
        })
      },
    })
    const deps = { getAccessToken: vi.fn().mockResolvedValue('t'), renew: vi.fn(), onSessionLost: vi.fn() }
    installAuthInterceptors(client, deps)
    await expect(client.get('/x')).rejects.toMatchObject({ response: { status: 500 } })
    expect(deps.renew).not.toHaveBeenCalled()
    expect(deps.onSessionLost).not.toHaveBeenCalled()
  })

  it('concurrent 401s share the single-flight renewal they are given', async () => {
    let resolveRenew!: (t: string) => void
    const shared = new Promise<string>((r) => (resolveRenew = r))
    const renew = vi.fn(() => shared)
    const { client, seen } = setup(['new'], { renew })
    const both = Promise.all([client.get('/a'), client.get('/b')])
    await vi.waitFor(() => expect(renew).toHaveBeenCalledTimes(2))
    resolveRenew('new')
    await expect(both).resolves.toHaveLength(2)
    expect(seen.filter((h) => h === 'Bearer new')).toHaveLength(2)
  })
})
