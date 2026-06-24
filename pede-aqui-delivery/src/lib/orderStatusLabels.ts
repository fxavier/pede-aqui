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

export function statusLabel(status: string): string {
  return labels[status] ?? status
}

export function isActiveOrder(status: string): boolean {
  return ACTIVE_STATUSES.has(status)
}
