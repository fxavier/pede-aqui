import axios from 'axios'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ratingService } from '@/lib/api/services'
import type { SubmitRatingRequest } from '@/lib/api/types'

/** Maximum comment length accepted by the backend (LAC-28). */
export const RATING_COMMENT_MAX = 500

export const myOrderRatingQueryKey = (orderId: string) => ['my-order-rating', orderId] as const

/**
 * Store rating as shown on cards and the store page: one decimal plus the number of ratings ("4.3 (12)"), or "Novo"
 * while the store has no rating yet. `count` is optional for older APIs that only send the average.
 */
export function formatRating(rating: number, count?: number | null): string {
  if (count === 0 || !(rating > 0)) return 'Novo'
  const average = rating.toFixed(1)
  return count != null ? `${average} (${count})` : average
}

/** Portuguese message for a failed rating call (stable backend `code` first). */
export function ratingErrorMessage(err: unknown): string {
  const code = axios.isAxiosError(err) ? (err.response?.data as { code?: string } | undefined)?.code : undefined
  switch (code) {
    case 'rating_already_submitted':
      return 'Esta encomenda já foi avaliada.'
    case 'rating_window_closed':
      return 'O prazo para avaliar esta encomenda terminou.'
    case 'rating_edit_window_closed':
      return 'A avaliação já não pode ser alterada (só nas primeiras 24 horas).'
    case 'order_not_delivered':
      return 'Só pode avaliar depois de receber a encomenda.'
    case 'courier_not_rateable':
      return 'Esta encomenda não teve estafeta para avaliar.'
    case 'rate_limited':
      return 'Demasiados pedidos. Aguarde um momento e tente de novo.'
    default:
      return 'Não foi possível guardar a avaliação. Tente de novo.'
  }
}

/** Builds the request body: trims the comment and leaves out empty values. */
export function ratingRequest(vendorStars: number, courierStars: number | null, comment: string): SubmitRatingRequest {
  const trimmed = comment.trim()
  return {
    vendorStars,
    ...(courierStars != null ? { courierStars } : {}),
    ...(trimmed ? { comment: trimmed } : {}),
  }
}

/** GET /me/orders/{id}/rating — only fetched once the order is delivered (otherwise there is nothing to show). */
export function useMyOrderRating(orderId: string, enabled: boolean) {
  return useQuery({
    queryKey: myOrderRatingQueryKey(orderId),
    queryFn: () => ratingService.getMine(orderId),
    enabled,
    retry: 1,
  })
}

/** POST (first rating) or PUT (edit) of the caller's rating; refreshes the rating state either way. */
export function useSaveMyOrderRating(orderId: string, mode: 'submit' | 'edit') {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (req: SubmitRatingRequest) =>
      mode === 'submit' ? ratingService.submit(orderId, req) : ratingService.edit(orderId, req),
    onSettled: () => queryClient.invalidateQueries({ queryKey: myOrderRatingQueryKey(orderId) }),
  })
}
