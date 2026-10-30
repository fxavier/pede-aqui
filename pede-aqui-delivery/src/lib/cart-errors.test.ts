import { describe, it, expect } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { cartErrorMessage, formatReopening } from './cart-errors'

function apiError(status: number, data: unknown) {
  const headers = new AxiosHeaders()
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', { headers }, null, {
    status, statusText: '', headers: {}, config: { headers }, data,
  })
}

const FALLBACK = 'Não foi possível adicionar ao carrinho.'
// 2026-11-02 10:00 in Maputo (UTC+2)
const NOW = new Date('2026-11-02T08:00:00Z')

describe('cartErrorMessage', () => {
  it('store_closed with a reopening later today shows the time in Maputo', () => {
    const err = apiError(422, { code: 'store_closed', details: { nextOpeningAt: '2026-11-02T16:00:00Z' } })
    expect(cartErrorMessage(err, FALLBACK, NOW)).toBe('Esta loja está fechada de momento. Abre às 18:00.')
  })

  it('store_closed with a reopening on another day names the day', () => {
    const err = apiError(422, { code: 'store_closed', details: { nextOpeningAt: '2026-11-03T06:00:00Z' } })
    const message = cartErrorMessage(err, FALLBACK, NOW)
    expect(message).toMatch(/^Esta loja está fechada de momento\. Abre .+ às 08:00\.$/)
    expect(message).toContain('3')
  })

  it('store_closed without a known reopening', () => {
    const err = apiError(422, { code: 'store_closed', details: { nextOpeningAt: null } })
    expect(cartErrorMessage(err, FALLBACK, NOW)).toBe('Esta loja está fechada de momento e não está a aceitar encomendas.')
  })

  it('maps the pharmacy and SKU codes', () => {
    expect(cartErrorMessage(apiError(422, { code: 'pharmacy_not_available' }), FALLBACK)).toContain('farmácia')
    expect(cartErrorMessage(apiError(422, { code: 'prescription_required' }), FALLBACK)).toContain('receita')
    expect(cartErrorMessage(apiError(422, { code: 'sku_vendor_mismatch' }), FALLBACK)).toContain('não pertence a esta loja')
  })

  it('falls back for other errors', () => {
    expect(cartErrorMessage(apiError(409, { code: 'insufficient_stock' }), FALLBACK)).toBe(FALLBACK)
    expect(cartErrorMessage(new Error('boom'), FALLBACK)).toBe(FALLBACK)
  })
})

describe('formatReopening', () => {
  it('uses the store time zone, not the browser one', () => {
    expect(formatReopening('2026-11-02T21:30:00Z', new Date('2026-11-02T20:00:00Z'))).toContain('23:30')
  })
})
