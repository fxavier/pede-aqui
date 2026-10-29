import { useState } from 'react'
import { Clock, XCircle } from 'lucide-react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import type { CustomerOrderDetail } from '@/lib/api/types'
import { cancellationAction, cancellationErrorMessage, useCancelMyOrder } from '@/lib/orderCancellation'
import { formatMZN } from '@/lib/utils'

/**
 * Cancellation block of the customer's order detail (LAC-26): "Cancelar encomenda" before the store accepts (free,
 * full refund), "Pedir cancelamento" while accepted/preparing (operations decide; a fee is withheld if approved), a
 * pending notice while a request awaits a decision, nothing from "ready for pickup" on. Every action asks for
 * confirmation first.
 */
export function OrderCancellationCard({ order }: { order: CustomerOrderDetail }) {
  const action = cancellationAction(order)
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const mutation = useCancelMyOrder(order.id)

  if (action.kind === 'none') return null

  if (action.kind === 'pending') {
    return (
      <div className="bg-amber-50 rounded-3xl border border-amber-100 p-5 shadow-sm text-left space-y-1" role="status">
        <div className="flex items-center gap-2 text-amber-800">
          <Clock className="w-4 h-4" />
          <h3 className="font-display font-extrabold text-base">Pedido de cancelamento em análise</h3>
        </div>
        <p className="text-sm text-amber-900/80">
          A nossa equipa vai decidir em breve. Se for aprovado, é retida uma taxa de {formatMZN(action.feeAmount)} e o
          restante é reembolsado. Até lá a encomenda continua.
        </p>
      </div>
    )
  }

  const isRequest = action.kind === 'request'
  const confirm = () => {
    mutation.mutate(reason.trim() || undefined, { onSuccess: () => setOpen(false) })
  }

  return (
    <div className="bg-white rounded-3xl border border-slate-100 p-5 shadow-sm text-left space-y-3">
      <h3 className="font-display font-extrabold text-slate-800 text-base">Cancelamento</h3>
      {isRequest ? (
        <p className="text-sm text-slate-500">
          A loja já aceitou a encomenda. Pode pedir o cancelamento; se for aprovado, é retida uma taxa
          {action.feeEstimate != null ? ` de ${formatMZN(action.feeEstimate)}` : ''} e o restante é reembolsado.
        </p>
      ) : (
        <p className="text-sm text-slate-500">A loja ainda não aceitou a encomenda: pode cancelá-la sem custos.</p>
      )}
      {isRequest && action.previousRejected && (
        <p className="text-xs text-slate-400">O seu pedido anterior não foi aprovado.</p>
      )}
      <button
        type="button"
        onClick={() => { mutation.reset(); setOpen(true) }}
        className="w-full px-4 py-2.5 rounded-xl border border-rose-200 text-rose-700 font-bold text-sm hover:bg-rose-50 flex items-center justify-center gap-1.5"
      >
        <XCircle className="w-4 h-4" /> {isRequest ? 'Pedir cancelamento' : 'Cancelar encomenda'}
      </button>

      <Dialog open={open} onOpenChange={(next) => { if (!mutation.isPending) setOpen(next) }}>
        <DialogContent className="bg-white rounded-2xl">
          <DialogHeader>
            <DialogTitle>{isRequest ? 'Pedir cancelamento?' : 'Cancelar encomenda?'}</DialogTitle>
            <DialogDescription>
              {isRequest
                ? `O pedido será analisado pela nossa equipa. Se for aprovado, é retida uma taxa${action.feeEstimate != null ? ` de ${formatMZN(action.feeEstimate)}` : ''}.`
                : 'A encomenda será cancelada e o valor pago será reembolsado na totalidade.'}
            </DialogDescription>
          </DialogHeader>
          {isRequest && (
            <label className="block text-sm text-slate-600 space-y-1">
              <span>Motivo (opcional)</span>
              <textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                maxLength={500}
                rows={3}
                className="w-full rounded-xl border border-slate-200 p-2 text-sm"
              />
            </label>
          )}
          {mutation.isError && (
            <p className="text-sm text-rose-700" role="alert">{cancellationErrorMessage(mutation.error)}</p>
          )}
          <DialogFooter className="gap-2">
            <button type="button" onClick={() => setOpen(false)} disabled={mutation.isPending}
              className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 font-bold text-sm">
              Voltar
            </button>
            <button type="button" onClick={confirm} disabled={mutation.isPending}
              className="px-4 py-2 rounded-xl bg-rose-600 text-white font-bold text-sm disabled:opacity-60">
              {mutation.isPending ? 'A enviar…' : isRequest ? 'Confirmar pedido' : 'Confirmar cancelamento'}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
