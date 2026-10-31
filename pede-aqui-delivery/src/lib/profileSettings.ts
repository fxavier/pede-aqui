import axios from 'axios'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { privacyService, profileService } from '@/lib/api/services'
import type {
  DeletionRequest,
  NotificationPreferences,
  PreferredLocale,
  ProfileSettings,
  ProfileUpdateRequest,
} from '@/lib/api/types'

export const profileSettingsQueryKey = ['me', 'profile'] as const
export const deletionRequestQueryKey = ['me', 'deletion-request'] as const

/** Languages the customer can choose (PATCH /me rejects anything else with unsupported_locale). */
export const LOCALE_OPTIONS: ReadonlyArray<{ value: PreferredLocale; label: string }> = [
  { value: 'pt-PT', label: 'Português (Portugal)' },
  { value: 'pt-MZ', label: 'Português (Moçambique)' },
  { value: 'en', label: 'English' },
]

/** What the profile form edits; e-mail and phone are read-only (Keycloak flows). */
export interface ProfileDraft {
  displayName: string
  preferredLocale: string
  notificationPreferences: NotificationPreferences
}

/** Stable `code` of an RFC 9457 problem response, if any. */
export function problemCode(err: unknown): string | undefined {
  return axios.isAxiosError(err) ? (err.response?.data as { code?: string } | undefined)?.code : undefined
}

/**
 * PATCH /me body holding only what changed between the saved profile and the draft (display name trimmed).
 * Returns null when nothing changed, so the form can skip the request.
 */
export function profilePatch(saved: ProfileSettings, draft: ProfileDraft): ProfileUpdateRequest | null {
  const patch: ProfileUpdateRequest = {}
  const name = draft.displayName.trim()
  if (name !== saved.displayName) patch.displayName = name
  if (draft.preferredLocale !== saved.preferredLocale) patch.preferredLocale = draft.preferredLocale as PreferredLocale
  const prefs: Partial<NotificationPreferences> = {}
  for (const key of ['push', 'sms', 'email'] as const) {
    if (draft.notificationPreferences[key] !== saved.notificationPreferences[key]) {
      prefs[key] = draft.notificationPreferences[key]
    }
  }
  if (Object.keys(prefs).length > 0) patch.notificationPreferences = prefs
  return Object.keys(patch).length > 0 ? patch : null
}

/** Portuguese message for a failed profile update. */
export function profileErrorMessage(err: unknown): string {
  switch (problemCode(err)) {
    case 'validation_failed':
      return 'Verifique os dados: o nome deve ter entre 1 e 120 caracteres.'
    case 'unsupported_locale':
      return 'Idioma não suportado.'
    default:
      return 'Não foi possível guardar o perfil. Tente de novo.'
  }
}

/** Portuguese message for a failed deletion request / cancellation. */
export function deletionErrorMessage(err: unknown): string {
  switch (problemCode(err)) {
    case 'active_orders_present':
      return 'Tem encomendas em curso. Pode pedir a eliminação depois de concluídas ou canceladas.'
    case 'deletion_request_not_found':
      return 'Não existe nenhum pedido de eliminação pendente.'
    default:
      return 'Não foi possível concluir o pedido. Tente de novo.'
  }
}

/** Portuguese message for a failed data export. */
export function exportErrorMessage(err: unknown): string {
  return problemCode(err) === 'rate_limited'
    ? 'Já pediu uma exportação recentemente. Tente mais tarde.'
    : 'Não foi possível exportar os seus dados. Tente de novo.'
}

/** Date in pt-PT (e.g. "30/11/2026"). */
export function formatDatePt(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-PT', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

/** File name of the data export: pede-aqui-dados-YYYY-MM-DD.json (local date). */
export function dataExportFilename(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `pede-aqui-dados-${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}.json`
}

/**
 * Fetches GET /me/data-export through the API client (bearer token attached) and saves it as a JSON file via a
 * temporary <a download>. Returns the file name; rejects with the Axios error (e.g. 429 rate_limited).
 */
export async function downloadDataExport(now: Date = new Date()): Promise<string> {
  const data = await privacyService.exportData()
  const filename = dataExportFilename(now)
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  try {
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.style.display = 'none'
    document.body.appendChild(a)
    a.click()
    a.remove()
  } finally {
    URL.revokeObjectURL(url)
  }
  return filename
}

/** GET /me/deletion-request, with 404 deletion_request_not_found mapped to null ("no request yet"). */
export async function fetchDeletionRequest(): Promise<DeletionRequest | null> {
  try {
    return await privacyService.getDeletionRequest()
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 404) return null
    throw err
  }
}

/** The signed-in customer's profile settings (GET /me/profile). */
export function useProfileSettings(enabled = true) {
  return useQuery({ queryKey: profileSettingsQueryKey, queryFn: () => profileService.get(), enabled })
}

/** PATCH /me with a partial body; the response replaces the cached profile. */
export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (patch: ProfileUpdateRequest) => profileService.update(patch),
    onSuccess: (profile) => queryClient.setQueryData(profileSettingsQueryKey, profile),
  })
}

/** Latest account-deletion request, or null when there never was one. */
export function useDeletionRequest(enabled = true) {
  return useQuery({ queryKey: deletionRequestQueryKey, queryFn: fetchDeletionRequest, enabled })
}

/** POST /me/deletion-request; caches the returned (pending) request. */
export function useRequestDeletion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => privacyService.requestDeletion(),
    onSuccess: (request) => queryClient.setQueryData(deletionRequestQueryKey, request),
  })
}

/** DELETE /me/deletion-request; caches the cancelled request, refetches on a conflict (e.g. nothing pending). */
export function useCancelDeletion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => privacyService.cancelDeletion(),
    onSuccess: (request) => queryClient.setQueryData(deletionRequestQueryKey, request),
    onError: () => queryClient.invalidateQueries({ queryKey: deletionRequestQueryKey }),
  })
}
