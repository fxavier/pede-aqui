import { type FormEvent, useEffect, useState } from 'react'
import { Bell, Download, Save, ShieldAlert, UserCog } from 'lucide-react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import type { ProfileSettings } from '@/lib/api/types'
import {
  LOCALE_OPTIONS,
  type ProfileDraft,
  deletionErrorMessage,
  downloadDataExport,
  exportErrorMessage,
  formatDatePt,
  profileErrorMessage,
  profilePatch,
  useCancelDeletion,
  useDeletionRequest,
  useProfileSettings,
  useRequestDeletion,
  useUpdateProfile,
} from '@/lib/profileSettings'

const card = 'bg-white rounded-3xl border border-slate-100 p-6 shadow-sm space-y-4 text-left'
const heading = 'font-display font-extrabold text-slate-800 text-base pb-2 border-b border-slate-50 flex items-center gap-1.5'
const inputCls = 'w-full rounded-xl border border-slate-200 px-3 py-2 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-500/30'

function draftOf(p: ProfileSettings): ProfileDraft {
  return {
    displayName: p.displayName,
    preferredLocale: p.preferredLocale,
    notificationPreferences: { ...p.notificationPreferences },
  }
}

/** Profile form: display name, language and notification channels; e-mail and phone shown read-only. */
export function ProfileSettingsCard() {
  const profile = useProfileSettings()
  const update = useUpdateProfile()
  const [draft, setDraft] = useState<ProfileDraft | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (profile.data) setDraft(draftOf(profile.data))
  }, [profile.data])

  if (profile.isPending) {
    return <div className={card}><p className="text-sm text-slate-400">A carregar o perfil…</p></div>
  }
  if (profile.isError || !profile.data || !draft) {
    return <div className={card}><p className="text-sm text-rose-700" role="alert">Não foi possível carregar o perfil.</p></div>
  }

  const data = profile.data
  const patch = profilePatch(data, draft)
  const nameInvalid = draft.displayName.trim().length < 1 || draft.displayName.trim().length > 120
  const phoneVerified = !!data.phoneVerifiedAt

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!patch || nameInvalid) return
    setSaved(false)
    update.mutate(patch, { onSuccess: () => setSaved(true) })
  }

  const toggle = (key: keyof ProfileDraft['notificationPreferences']) => {
    setSaved(false)
    setDraft({ ...draft, notificationPreferences: { ...draft.notificationPreferences, [key]: !draft.notificationPreferences[key] } })
  }

  const channels = [
    { key: 'push' as const, label: 'Notificações push' },
    { key: 'sms' as const, label: 'SMS' },
    { key: 'email' as const, label: 'E-mail' },
  ]

  return (
    <form className={card} onSubmit={submit} aria-label="Dados do perfil">
      <h3 className={heading}><UserCog className="w-4 h-4 text-brand-500" /> Dados do Perfil</h3>

      <label className="block space-y-1 text-sm text-slate-600">
        <span className="font-semibold">Nome</span>
        <input
          className={inputCls}
          value={draft.displayName}
          maxLength={120}
          onChange={(e) => { setSaved(false); setDraft({ ...draft, displayName: e.target.value }) }}
          aria-invalid={nameInvalid}
        />
      </label>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
        <div className="space-y-1">
          <p className="font-semibold text-slate-600">E-mail</p>
          <p className="text-slate-800">{data.email}</p>
        </div>
        <div className="space-y-1">
          <p className="font-semibold text-slate-600">Telefone</p>
          <p className="text-slate-800">{data.phoneE164 ?? 'Não definido'}{data.phoneE164 && !phoneVerified ? ' (não verificado)' : ''}</p>
        </div>
      </div>
      <p className="text-xs text-slate-400">
        O e-mail é alterado na página de conta (início de sessão) e o telefone através do início de sessão por SMS.
      </p>

      <label className="block space-y-1 text-sm text-slate-600">
        <span className="font-semibold">Idioma</span>
        <select
          className={inputCls}
          value={draft.preferredLocale}
          onChange={(e) => { setSaved(false); setDraft({ ...draft, preferredLocale: e.target.value }) }}
        >
          {LOCALE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </label>

      <fieldset className="space-y-2">
        <legend className="text-sm font-semibold text-slate-600 flex items-center gap-1.5 mb-1"><Bell className="w-4 h-4 text-slate-400" /> Notificações</legend>
        {channels.map(({ key, label }) => (
          <label key={key} className="flex items-center justify-between rounded-xl border border-slate-100 px-3 py-2 text-sm text-slate-700">
            <span>{label}</span>
            <input
              type="checkbox"
              role="switch"
              className="h-4 w-4 accent-brand-600"
              checked={draft.notificationPreferences[key]}
              onChange={() => toggle(key)}
            />
          </label>
        ))}
        <p className="text-xs text-slate-400">
          As notificações por SMS só são enviadas para um número de telefone verificado.
          {!phoneVerified && ' Ainda não tem um telefone verificado.'}
        </p>
      </fieldset>

      {update.isError && <p className="text-sm text-rose-700" role="alert">{profileErrorMessage(update.error)}</p>}
      {saved && !patch && <p className="text-sm text-emerald-700" role="status">Perfil guardado.</p>}

      <button
        type="submit"
        disabled={!patch || nameInvalid || update.isPending}
        className="px-5 py-2.5 bg-brand-600 hover:bg-brand-500 text-white font-bold text-xs rounded-full disabled:opacity-50 flex items-center gap-1.5"
      >
        <Save className="w-4 h-4" /> {update.isPending ? 'A guardar…' : 'Guardar alterações'}
      </button>
    </form>
  )
}

/** "Os meus dados": downloads the customer's personal data as JSON. */
export function DataExportCard() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const run = async () => {
    setBusy(true)
    setError(null)
    setDone(null)
    try {
      setDone(await downloadDataExport())
    } catch (err) {
      setError(exportErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={card}>
      <h3 className={heading}><Download className="w-4 h-4 text-brand-500" /> Os meus dados</h3>
      <p className="text-sm text-slate-500">
        Descarregue uma cópia dos dados pessoais que guardamos sobre si (perfil, moradas, encomendas) num ficheiro JSON.
      </p>
      {error && <p className="text-sm text-rose-700" role="alert">{error}</p>}
      {done && <p className="text-sm text-emerald-700" role="status">Ficheiro {done} descarregado.</p>}
      <button
        type="button"
        onClick={run}
        disabled={busy}
        className="px-5 py-2.5 rounded-full border border-slate-200 text-slate-700 font-bold text-xs hover:bg-slate-50 disabled:opacity-50 flex items-center gap-1.5"
      >
        <Download className="w-4 h-4" /> {busy ? 'A preparar…' : 'Exportar os meus dados'}
      </button>
    </div>
  )
}

/** "Eliminar conta": request (with confirmation) or cancel the account deletion. */
export function AccountDeletionCard() {
  const deletion = useDeletionRequest()
  const requestDeletion = useRequestDeletion()
  const cancelDeletion = useCancelDeletion()
  const [open, setOpen] = useState(false)

  const pending = deletion.data?.status === 'PENDING' ? deletion.data : null

  return (
    <div className={card}>
      <h3 className={heading}><ShieldAlert className="w-4 h-4 text-rose-500" /> Eliminar conta</h3>

      {deletion.isPending ? (
        <p className="text-sm text-slate-400">A carregar…</p>
      ) : deletion.isError ? (
        <p className="text-sm text-rose-700" role="alert">Não foi possível verificar o estado do pedido de eliminação.</p>
      ) : pending ? (
        <div className="space-y-3">
          <div className="rounded-2xl bg-amber-50 border border-amber-100 p-4 text-sm text-amber-900" role="status">
            A sua conta será eliminada em <strong>{formatDatePt(pending.scheduledFor)}</strong>. Até lá pode cancelar o pedido
            e continuar a usar a conta normalmente.
          </div>
          {cancelDeletion.isError && (
            <p className="text-sm text-rose-700" role="alert">{deletionErrorMessage(cancelDeletion.error)}</p>
          )}
          <button
            type="button"
            onClick={() => cancelDeletion.mutate()}
            disabled={cancelDeletion.isPending}
            className="px-5 py-2.5 rounded-full border border-slate-200 text-slate-700 font-bold text-xs hover:bg-slate-50 disabled:opacity-50"
          >
            {cancelDeletion.isPending ? 'A cancelar…' : 'Cancelar eliminação'}
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-slate-500">
            Ao pedir a eliminação, tem 30 dias para mudar de ideias e cancelar. Depois disso, a conta é anonimizada: o nome,
            o e-mail e o telefone são removidos, as moradas e os dispositivos são apagados e deixa de conseguir iniciar
            sessão. Os registos de encomendas e pagamentos são mantidos apenas na medida exigida pela lei fiscal e
            financeira.
          </p>
          <button
            type="button"
            onClick={() => { requestDeletion.reset(); setOpen(true) }}
            className="px-5 py-2.5 rounded-full border border-rose-200 text-rose-700 font-bold text-xs hover:bg-rose-50"
          >
            Eliminar a minha conta
          </button>
        </div>
      )}

      <Dialog open={open} onOpenChange={(next) => { if (!requestDeletion.isPending) setOpen(next) }}>
        <DialogContent className="bg-white rounded-2xl">
          <DialogHeader>
            <DialogTitle>Eliminar a sua conta?</DialogTitle>
            <DialogDescription>
              A eliminação fica agendada para daqui a 30 dias e pode cancelá-la até lá. Depois, os seus dados pessoais são
              removidos de forma definitiva; as encomendas e pagamentos ficam guardados apenas como exige a lei.
            </DialogDescription>
          </DialogHeader>
          {requestDeletion.isError && (
            <p className="text-sm text-rose-700" role="alert">{deletionErrorMessage(requestDeletion.error)}</p>
          )}
          <DialogFooter className="gap-2">
            <button type="button" onClick={() => setOpen(false)} disabled={requestDeletion.isPending}
              className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 font-bold text-sm">
              Voltar
            </button>
            <button type="button" onClick={() => requestDeletion.mutate(undefined, { onSuccess: () => setOpen(false) })}
              disabled={requestDeletion.isPending}
              className="px-4 py-2 rounded-xl bg-rose-600 text-white font-bold text-sm disabled:opacity-60">
              {requestDeletion.isPending ? 'A enviar…' : 'Confirmar eliminação'}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
