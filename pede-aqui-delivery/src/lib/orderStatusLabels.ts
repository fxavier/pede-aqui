const labels: Record<string, string> = {
  PAYMENT_CONFIRMED: 'Aguardando restaurante',
  ACCEPTED_BY_VENDOR: 'Confirmado pelo restaurante',
  PREPARING: 'Em preparação',
  READY_FOR_PICKUP: 'Pronto para retirada',
  ACCEPTED: 'Estafeta a caminho do restaurante',
  ARRIVED_AT_VENDOR: 'Estafeta no restaurante',
  PICKED_UP: 'Pedido retirado',
  ON_ROUTE_TO_CUSTOMER: 'A caminho de si',
  ARRIVED_AT_CUSTOMER: 'Estafeta chegou',
  DELIVERED: 'Entregue',
  FAILED_DELIVERY: 'Entrega falhada',
  PAYMENT_PENDING: 'Aguardando pagamento',
  CANCELLED: 'Cancelado',
  // Legacy rows only: new seller rejections are CANCELLED + cancellationReason SELLER_REJECTED.
  REJECTED_BY_VENDOR: 'Rejeitado',
}

export const SELLER_REJECTED_LABEL = 'Rejeitado pelo vendedor'
/** LAC-26: cancelled by the customer (free, or approved request with fee). */
export const CUSTOMER_CANCELLED_LABEL = 'Cancelado por si'

/** True when a CANCELLED order was cancelled because the seller rejected it. */
export function isSellerRejected(status: string, cancellationReason?: string | null): boolean {
  return status === 'CANCELLED' && cancellationReason === 'SELLER_REJECTED'
}

const ACTIVE_STATUSES = new Set([
  'PAYMENT_CONFIRMED',
  'ACCEPTED_BY_VENDOR',
  'PREPARING',
  'READY_FOR_PICKUP',
  'ACCEPTED',
  'ARRIVED_AT_VENDOR',
  'PICKED_UP',
  'ON_ROUTE_TO_CUSTOMER',
  'ARRIVED_AT_CUSTOMER',
])

/** Customer-facing label for an order status; pass `cancellationReason` to tell seller rejections apart. */
export function statusLabel(status: string, cancellationReason?: string | null): string {
  if (isSellerRejected(status, cancellationReason)) return SELLER_REJECTED_LABEL
  if (status === 'CANCELLED' && (cancellationReason === 'CUSTOMER_CANCELLED' || cancellationReason === 'CUSTOMER_CANCELLED_WITH_FEE')) {
    return CUSTOMER_CANCELLED_LABEL
  }
  return labels[status] ?? status
}

export function isActiveOrder(status: string): boolean {
  return ACTIVE_STATUSES.has(status)
}
