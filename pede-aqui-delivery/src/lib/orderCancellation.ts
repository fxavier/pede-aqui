import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { orderService } from '@/lib/api/services'
import type { CustomerOrderDetail } from '@/lib/api/types'
import { myOrderQueryKey, myOrdersQueryKey } from '@/lib/orderHistory'

/**
 * What the order detail offers for cancellation (LAC-26 policy, decided by the backend's `cancellationPhase`):
 * - `cancel`  — before the store accepts: cancelled at once, full refund of what was paid;
 * - `request` — accepted/preparing: the customer asks, operations decide, a fee is withheld if approved;
 * - `pending` — a request is already awaiting a decision;
 * - `none`    — nothing to offer (ready for pickup onwards, finished, already cancelled).
 */
export type CancellationAction =
  | { kind: 'cancel' }
  | { kind: 'request'; feeEstimate: number | null; previousRejected: boolean }
  | { kind: 'pending'; feeAmount: number }
  | { kind: 'none' }

export function cancellationAction(order: Pick<CustomerOrderDetail, 'cancellationPhase' | 'cancellationFeeEstimate' | 'cancellationRequest'>): CancellationAction {
  const request = order.cancellationRequest
  switch (order.cancellationPhase) {
    case 'FREE':
      return { kind: 'cancel' }
    case 'REQUEST':
      if (request?.status === 'REQUESTED') return { kind: 'pending', feeAmount: request.feeAmount }
      return {
        kind: 'request',
        feeEstimate: order.cancellationFeeEstimate,
        previousRejected: request?.status === 'REJECTED' && request.closureReason === 'OPS_REJECTED',
      }
    default:
      return { kind: 'none' }
  }
}

/** Portuguese message for a failed cancellation call (stable backend `code` first). */
export function cancellationErrorMessage(err: unknown): string {
  const code = axios.isAxiosError(err) ? (err.response?.data as { code?: string } | undefined)?.code : undefined
  switch (code) {
    case 'order_not_cancellable':
      return 'A encomenda já não pode ser cancelada (já está pronta ou a caminho).'
    case 'concurrent_modification':
    case 'invalid_order_transition':
      return 'O estado da encomenda acabou de mudar. Verifique o estado atual e tente de novo.'
    case 'rate_limited':
      return 'Demasiados pedidos. Aguarde um momento e tente de novo.'
    default:
      return 'Não foi possível cancelar a encomenda. Tente de novo.'
  }
}

/** POST /me/orders/{id}/cancel; refreshes the order detail and the history on success or conflict. */
export function useCancelMyOrder(orderId: string) {
  const queryClient = useQueryClient()
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: myOrderQueryKey(orderId) }),
    queryClient.invalidateQueries({ queryKey: myOrdersQueryKey }),
  ])
  return useMutation({
    mutationFn: (reason?: string) => orderService.cancelMine(orderId, reason),
    onSettled: refresh,
  })
}
