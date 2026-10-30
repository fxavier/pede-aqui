import axios from 'axios'
import type { CheckoutQuote } from './api/types'

/** Store time zone used by the backend opening hours (app.store-availability.timezone). */
export const STORE_TIME_ZONE = 'Africa/Maputo'

interface CartErrorBody {
  code?: string
  details?: { nextOpeningAt?: string | null; maxDistanceKm?: number | null; quote?: CheckoutQuote | null }
}

/** Fresh quote carried by a 409 quote_changed (W6-A), or null for any other error. */
export function freshQuoteFrom(err: unknown): CheckoutQuote | null {
  const body: CartErrorBody | undefined = axios.isAxiosError(err) ? err.response?.data : undefined
  return body?.code === 'quote_changed' ? body.details?.quote ?? null : null
}

/** "às 08:00" today, otherwise "seg., 08:00"-style day + time, in the store time zone. */
export function formatReopening(nextOpeningAt: string, now: Date = new Date()): string {
  const at = new Date(nextOpeningAt)
  const day = (d: Date) => d.toLocaleDateString('pt-PT', { timeZone: STORE_TIME_ZONE })
  const time = at.toLocaleTimeString('pt-PT', { timeZone: STORE_TIME_ZONE, hour: '2-digit', minute: '2-digit' })
  if (day(at) === day(now)) return `às ${time}`
  const weekday = at.toLocaleDateString('pt-PT', { timeZone: STORE_TIME_ZONE, weekday: 'long', day: 'numeric', month: 'long' })
  return `${weekday} às ${time}`
}

/**
 * Portuguese message for cart / checkout rejections (LAC-27 store hours, LAC-64 pharmacy gate, SKU of another
 * vendor). Returns the fallback for any other error so callers keep their generic text.
 */
export function cartErrorMessage(err: unknown, fallback: string, now: Date = new Date()): string {
  const body: CartErrorBody | undefined = axios.isAxiosError(err) ? err.response?.data : undefined
  switch (body?.code) {
    case 'store_closed': {
      const next = body.details?.nextOpeningAt
      return next
        ? `Esta loja está fechada de momento. Abre ${formatReopening(next, now)}.`
        : 'Esta loja está fechada de momento e não está a aceitar encomendas.'
    }
    case 'pharmacy_not_available':
      return 'Os produtos de farmácia ainda não estão disponíveis para encomenda.'
    case 'prescription_required':
      return 'Este produto exige receita médica e não pode ser encomendado online.'
    case 'sku_vendor_mismatch':
      return 'Este produto não pertence a esta loja. Recarregue a página e tente novamente.'
    case 'out_of_service_area':
      return 'Esta loja não entrega nesta morada. Escolha outra morada ou outra loja.'
    case 'address_too_far': {
      const max = body.details?.maxDistanceKm
      return max != null
        ? `Esta morada fica fora do raio de entrega da loja (máximo ${max} km).`
        : 'Esta morada fica fora do raio de entrega da loja.'
    }
    case 'quote_changed':
      return 'Os preços ou as taxas mudaram desde que viu o total. Reveja o novo total e confirme novamente.'
    case 'quote_mismatch':
      return 'O resumo do pedido está desatualizado. Recarregue a página e tente novamente.'
    case 'price_changed':
      return 'Os preços de alguns produtos mudaram. Reveja o carrinho e confirme novamente.'
    default:
      return fallback
  }
}
