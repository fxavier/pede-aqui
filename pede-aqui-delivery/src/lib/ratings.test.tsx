import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { OrderRating, OrderRatingStatus } from '@/lib/api/types'

vi.mock('@/lib/api/services', () => ({
  ratingService: { getMine: vi.fn(), submit: vi.fn(), edit: vi.fn() },
}))

import { ratingService } from '@/lib/api/services'
import { formatRating, ratingErrorMessage, ratingRequest } from './ratings'
import { OrderRatingCard } from '@/components/OrderRatingCard'

function rating(overrides: Partial<OrderRating> = {}): OrderRating {
  return {
    id: 'r1', orderId: 'o1', vendorId: 'v1', courierId: 'c1', vendorStars: 4, courierStars: 5, comment: 'Bom',
    commentHidden: false, commentHiddenReason: null, vendorReply: null, vendorRepliedAt: null,
    createdAt: '2026-10-30T10:00:00Z', updatedAt: '2026-10-30T10:00:00Z', ...overrides,
  }
}

function status(overrides: Partial<OrderRatingStatus> = {}): OrderRatingStatus {
  return {
    orderId: 'o1', rateable: true, notRateableReason: null, rateableUntil: '2026-11-06T10:00:00Z', courierRateable: true,
    editable: false, editableUntil: null, rating: null, ...overrides,
  }
}

function axiosError(status: number, code: string): AxiosError {
  return new AxiosError('fail', String(status), undefined, undefined, {
    status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { code },
  })
}

function renderCard(delivered = true) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><OrderRatingCard orderId="o1" delivered={delivered} /></QueryClientProvider>)
}

describe('formatRating (LAC-28 real aggregates)', () => {
  it('shows one decimal and the count, or "Novo" without ratings', () => {
    expect(formatRating(4.333, 12)).toBe('4.3 (12)')
    expect(formatRating(5, 1)).toBe('5.0 (1)')
    expect(formatRating(0, 0)).toBe('Novo')
    expect(formatRating(4.2, 0)).toBe('Novo')
    expect(formatRating(4.2)).toBe('4.2') // older API without ratingCount
    expect(formatRating(0)).toBe('Novo')
  })
})

describe('ratingRequest / ratingErrorMessage', () => {
  it('trims the comment and omits empty values', () => {
    expect(ratingRequest(5, null, '   ')).toEqual({ vendorStars: 5 })
    expect(ratingRequest(3, 4, '  Ok  ')).toEqual({ vendorStars: 3, courierStars: 4, comment: 'Ok' })
  })

  it('explains failures by their stable code', () => {
    expect(ratingErrorMessage(axiosError(409, 'rating_already_submitted'))).toMatch(/já foi avaliada/)
    expect(ratingErrorMessage(axiosError(409, 'rating_window_closed'))).toMatch(/prazo/)
    expect(ratingErrorMessage(axiosError(409, 'rating_edit_window_closed'))).toMatch(/24 horas/)
    expect(ratingErrorMessage(new Error('x'))).toMatch(/Não foi possível/)
  })
})

describe('OrderRatingCard', () => {
  beforeEach(() => vi.mocked(ratingService.getMine).mockReset())
  afterEach(cleanup)

  it('does not fetch or render before delivery', () => {
    const { container } = renderCard(false)
    expect(container.innerHTML).toBe('')
    expect(ratingService.getMine).not.toHaveBeenCalled()
  })

  it('prompts for store and courier stars and submits the rating', async () => {
    vi.mocked(ratingService.getMine).mockResolvedValue(status())
    vi.mocked(ratingService.submit).mockResolvedValue(rating())
    renderCard()

    expect(await screen.findByText('Como correu a encomenda?')).toBeTruthy()
    const send = screen.getByRole('button', { name: 'Enviar avaliação' })
    expect((send as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('radio', { name: 'Loja: 4 estrelas' }))
    fireEvent.click(screen.getByRole('radio', { name: 'Estafeta: 5 estrelas' }))
    fireEvent.change(screen.getByRole('textbox'), { target: { value: ' Rápido ' } })
    fireEvent.click(send)

    await waitFor(() => expect(ratingService.submit).toHaveBeenCalledWith('o1', { vendorStars: 4, courierStars: 5, comment: 'Rápido' }))
  })

  it('hides the courier picker when no courier delivered the order', async () => {
    vi.mocked(ratingService.getMine).mockResolvedValue(status({ courierRateable: false }))
    renderCard()
    expect(await screen.findByRole('radiogroup', { name: 'Loja' })).toBeTruthy()
    expect(screen.queryByRole('radiogroup', { name: 'Estafeta' })).toBeNull()
  })

  it('shows the submitted rating with the store reply and lets the customer edit within 24 h', async () => {
    vi.mocked(ratingService.getMine).mockResolvedValue(status({
      rateable: false, notRateableReason: 'rating_already_submitted', editable: true,
      rating: rating({ vendorReply: 'Obrigado!' }),
    }))
    vi.mocked(ratingService.edit).mockResolvedValue(rating({ vendorStars: 2 }))
    renderCard()

    expect(await screen.findByText('A sua avaliação')).toBeTruthy()
    expect(screen.getByText('Obrigado!')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    fireEvent.click(screen.getByRole('radio', { name: 'Loja: 2 estrelas' }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(ratingService.edit).toHaveBeenCalledWith('o1', { vendorStars: 2, courierStars: 5, comment: 'Bom' }))
  })

  it('renders nothing when the window closed without a rating', async () => {
    vi.mocked(ratingService.getMine).mockResolvedValue(status({ rateable: false, notRateableReason: 'rating_window_closed' }))
    const { container } = renderCard()
    await waitFor(() => expect(ratingService.getMine).toHaveBeenCalled())
    expect(container.innerHTML).toBe('')
  })
})
