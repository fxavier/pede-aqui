import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import type { DeletionRequest, ProfileSettings } from '@/lib/api/types'

vi.mock('@/lib/api/services', () => ({
  profileService: { get: vi.fn(), update: vi.fn() },
  privacyService: { getDeletionRequest: vi.fn(), requestDeletion: vi.fn(), cancelDeletion: vi.fn(), exportData: vi.fn() },
}))

import { privacyService, profileService } from '@/lib/api/services'
import {
  dataExportFilename,
  deletionErrorMessage,
  deletionRequestQueryKey,
  downloadDataExport,
  exportErrorMessage,
  fetchDeletionRequest,
  problemCode,
  profilePatch,
  profileSettingsQueryKey,
  useCancelDeletion,
  useDeletionRequest,
  useRequestDeletion,
  useUpdateProfile,
} from './profileSettings'
import { AccountDeletionCard, ProfileSettingsCard } from '@/components/AccountSettings'

function profile(overrides: Partial<ProfileSettings> = {}): ProfileSettings {
  return {
    id: 'c1', email: 'ana@example.com', displayName: 'Ana', phoneE164: '+258841234567', phoneVerifiedAt: null,
    preferredLocale: 'pt-PT', notificationPreferences: { push: true, sms: false, email: true },
    ...overrides,
  }
}

function deletion(overrides: Partial<DeletionRequest> = {}): DeletionRequest {
  return {
    id: 'd1', status: 'PENDING', requestedAt: '2026-10-31T10:00:00Z', scheduledFor: '2026-11-30T10:00:00Z',
    cancelledAt: null, completedAt: null, ...overrides,
  }
}

function axiosError(status: number, code: string): AxiosError {
  return new AxiosError('fail', String(status), undefined, undefined, {
    status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { code },
  })
}

function newClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
}

function wrapperFor(client: QueryClient) {
  return ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

beforeEach(() => { vi.resetAllMocks() })
afterEach(cleanup)

describe('deletion request', () => {
  it('maps 404 deletion_request_not_found to null', async () => {
    vi.mocked(privacyService.getDeletionRequest).mockRejectedValue(axiosError(404, 'deletion_request_not_found'))
    await expect(fetchDeletionRequest()).resolves.toBeNull()

    const { result } = renderHook(() => useDeletionRequest(), { wrapper: wrapperFor(newClient()) })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toBeNull()
  })

  it('rethrows other errors', async () => {
    vi.mocked(privacyService.getDeletionRequest).mockRejectedValue(axiosError(500, 'internal_error'))
    await expect(fetchDeletionRequest()).rejects.toBeInstanceOf(AxiosError)
  })

  it('request with an order in progress surfaces active_orders_present', async () => {
    vi.mocked(privacyService.requestDeletion).mockRejectedValue(axiosError(409, 'active_orders_present'))
    const { result } = renderHook(() => useRequestDeletion(), { wrapper: wrapperFor(newClient()) })
    act(() => { result.current.mutate() })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(problemCode(result.current.error)).toBe('active_orders_present')
    expect(deletionErrorMessage(result.current.error)).toBe(
      'Tem encomendas em curso. Pode pedir a eliminação depois de concluídas ou canceladas.',
    )
  })

  it('request caches the pending request', async () => {
    vi.mocked(privacyService.requestDeletion).mockResolvedValue(deletion())
    const client = newClient()
    const { result } = renderHook(() => useRequestDeletion(), { wrapper: wrapperFor(client) })
    await act(() => result.current.mutateAsync())
    expect(client.getQueryData(deletionRequestQueryKey)).toEqual(deletion())
  })

  it('cancel updates the cached request to CANCELLED', async () => {
    const cancelled = deletion({ status: 'CANCELLED', cancelledAt: '2026-11-01T10:00:00Z' })
    vi.mocked(privacyService.cancelDeletion).mockResolvedValue(cancelled)
    const client = newClient()
    client.setQueryData(deletionRequestQueryKey, deletion())
    const { result } = renderHook(() => useCancelDeletion(), { wrapper: wrapperFor(client) })
    await act(() => result.current.mutateAsync())
    expect(client.getQueryData<DeletionRequest>(deletionRequestQueryKey)?.status).toBe('CANCELLED')
  })

  it('cancel failure invalidates the cached request', async () => {
    vi.mocked(privacyService.cancelDeletion).mockRejectedValue(axiosError(404, 'deletion_request_not_found'))
    const client = newClient()
    const spy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useCancelDeletion(), { wrapper: wrapperFor(client) })
    act(() => { result.current.mutate() })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(spy).toHaveBeenCalledWith({ queryKey: deletionRequestQueryKey })
  })
})

describe('profile update', () => {
  it('builds a patch with only the changed fields', () => {
    const saved = profile()
    const same = { displayName: 'Ana', preferredLocale: 'pt-PT', notificationPreferences: { push: true, sms: false, email: true } }
    expect(profilePatch(saved, same)).toBeNull()
    expect(profilePatch(saved, { ...same, displayName: '  Ana Maria ' })).toEqual({ displayName: 'Ana Maria' })
    expect(profilePatch(saved, { ...same, notificationPreferences: { push: false, sms: false, email: true } }))
      .toEqual({ notificationPreferences: { push: false } })
    expect(profilePatch(saved, { ...same, preferredLocale: 'en' })).toEqual({ preferredLocale: 'en' })
  })

  it('useUpdateProfile caches the returned profile', async () => {
    vi.mocked(profileService.update).mockResolvedValue(profile({ displayName: 'Ana Maria' }))
    const client = newClient()
    const { result } = renderHook(() => useUpdateProfile(), { wrapper: wrapperFor(client) })
    await act(() => result.current.mutateAsync({ displayName: 'Ana Maria' }))
    expect(profileService.update).toHaveBeenCalledWith({ displayName: 'Ana Maria' })
    expect(client.getQueryData<ProfileSettings>(profileSettingsQueryKey)?.displayName).toBe('Ana Maria')
  })

  it('form sends only the changed field', async () => {
    vi.mocked(profileService.get).mockResolvedValue(profile())
    vi.mocked(profileService.update).mockResolvedValue(profile({ preferredLocale: 'pt-MZ' }))
    render(<QueryClientProvider client={newClient()}><ProfileSettingsCard /></QueryClientProvider>)

    const select = await screen.findByRole('combobox')
    fireEvent.change(select, { target: { value: 'pt-MZ' } })
    fireEvent.click(screen.getByRole('button', { name: /Guardar alterações/ }))

    await waitFor(() => expect(profileService.update).toHaveBeenCalledWith({ preferredLocale: 'pt-MZ' }))
    expect(screen.getByText('ana@example.com')).toBeTruthy()
  })
})

describe('data export', () => {
  it('builds the file name from the local date', () => {
    expect(dataExportFilename(new Date(2026, 0, 5, 12))).toBe('pede-aqui-dados-2026-01-05.json')
  })

  it('downloads the JSON through an object URL', async () => {
    vi.mocked(privacyService.exportData).mockResolvedValue({ profile: { id: 'c1' } })
    const createObjectURL = vi.fn(() => 'blob:x')
    const revokeObjectURL = vi.fn()
    Object.assign(URL, { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const name = await downloadDataExport(new Date(2026, 10, 30, 9))

    expect(name).toBe('pede-aqui-dados-2026-11-30.json')
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
    expect(click).toHaveBeenCalledTimes(1)
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('pede-aqui-dados-2026-11-30.json')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:x')
    click.mockRestore()
  })

  it('explains the rate limit', () => {
    expect(exportErrorMessage(axiosError(429, 'rate_limited'))).toBe('Já pediu uma exportação recentemente. Tente mais tarde.')
    expect(exportErrorMessage(new Error('network'))).toMatch(/Não foi possível/)
  })
})

describe('AccountDeletionCard', () => {
  it('asks for confirmation before requesting', async () => {
    vi.mocked(privacyService.getDeletionRequest).mockRejectedValue(axiosError(404, 'deletion_request_not_found'))
    vi.mocked(privacyService.requestDeletion).mockResolvedValue(deletion())
    render(<QueryClientProvider client={newClient()}><AccountDeletionCard /></QueryClientProvider>)

    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar a minha conta' }))
    expect(privacyService.requestDeletion).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar eliminação' }))

    await waitFor(() => expect(privacyService.requestDeletion).toHaveBeenCalledTimes(1))
    expect(await screen.findByRole('button', { name: 'Cancelar eliminação' })).toBeTruthy()
    expect(screen.getByText('30/11/2026')).toBeTruthy()
  })

  it('shows the active-orders conflict in the dialog', async () => {
    vi.mocked(privacyService.getDeletionRequest).mockRejectedValue(axiosError(404, 'deletion_request_not_found'))
    vi.mocked(privacyService.requestDeletion).mockRejectedValue(axiosError(409, 'active_orders_present'))
    render(<QueryClientProvider client={newClient()}><AccountDeletionCard /></QueryClientProvider>)

    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar a minha conta' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar eliminação' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/Tem encomendas em curso/)
  })
})
