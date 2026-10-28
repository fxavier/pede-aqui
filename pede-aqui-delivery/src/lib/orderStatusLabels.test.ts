import { describe, it, expect } from 'vitest'
import { isSellerRejected, statusLabel } from './orderStatusLabels'

describe('statusLabel', () => {
  it('labels a seller rejection (CANCELLED + SELLER_REJECTED)', () => {
    expect(statusLabel('CANCELLED', 'SELLER_REJECTED')).toBe('Rejeitado pelo vendedor')
  })

  it('keeps "Cancelado" for other cancellations', () => {
    expect(statusLabel('CANCELLED')).toBe('Cancelado')
    expect(statusLabel('CANCELLED', null)).toBe('Cancelado')
    expect(statusLabel('CANCELLED', 'SOMETHING_ELSE')).toBe('Cancelado')
  })

  it('labels legacy REJECTED_BY_VENDOR rows', () => {
    expect(statusLabel('REJECTED_BY_VENDOR')).toBe('Rejeitado')
  })

  it('ignores the reason on non-cancelled statuses', () => {
    expect(statusLabel('DELIVERED', 'SELLER_REJECTED')).toBe('Entregue')
  })

  it('falls back to the raw status when unknown', () => {
    expect(statusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })
})

describe('isSellerRejected', () => {
  it('requires both CANCELLED and SELLER_REJECTED', () => {
    expect(isSellerRejected('CANCELLED', 'SELLER_REJECTED')).toBe(true)
    expect(isSellerRejected('CANCELLED', null)).toBe(false)
    expect(isSellerRejected('REJECTED_BY_VENDOR', 'SELLER_REJECTED')).toBe(false)
  })
})
