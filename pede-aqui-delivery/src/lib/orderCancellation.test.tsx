import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { CancellationRequest, CustomerOrderDetail } from '@/lib/api/types'

vi.mock('@/lib/api/services', () => ({
  orderService: { listMine: vi.fn(), getMine: vi.fn(), cancelMine: vi.fn() },
}))

import { orderService } from '@/lib/api/services'
import { cancellationAction, cancellationErrorMessage } from './orderCancellation'
import { OrderCancellationCard } from '@/components/OrderCancellationCard'
import { statusLabel } from './orderStatusLabels'

function request(overrides: Partial<CancellationRequest> = {}): CancellationRequest {
  return {
    id: 'r1', orderId: 'o1', orderReference: null, orderStatus: null, status: 'REQUESTED', closureReason: null,
    reason: null, feeRate: 0.1, feeAmount: 9, refundAmount: null, requestedAt: '2026-10-29T10:00:00Z', decidedAt: null,
    ...overrides,
  }
}

function order(overrides: Partial<CustomerOrderDetail> = {}): CustomerOrderDetail {
  return {
    id: 'o1', reference: 'REF-1', status: 'PAYMENT_CONFIRMED', cancellationReason: null, vendorId: 'v1', vendorName: 'Loja',
    createdAt: '2026-10-29T10:00:00Z', items: [], deliveryAddress: null, deliveryCode: '123456', subtotal: 90, fees: 10,
    deliveryFee: null, serviceFee: null, taxes: 0, discountTotal: 0, total: 100, deliveryFeeBasis: null, distanceKm: null,
    cancellationPhase: 'FREE', cancellationFeeEstimate: null, cancellationRequest: null,
    ...overrides,
  }
}

function axiosError(status: number, code: string): AxiosError {
  return new AxiosError('fail', String(status), undefined, undefined, {
    status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() }, data: { code },
  })
}

function renderCard(o: CustomerOrderDetail) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><OrderCancellationCard order={o} /></QueryClientProvider>)
}

describe('cancellationAction (LAC-26 policy per phase)', () => {
  it('maps every phase to what the detail offers', () => {
    expect(cancellationAction(order())).toEqual({ kind: 'cancel' })
    expect(cancellationAction(order({ cancellationPhase: 'REQUEST', cancellationFeeEstimate: 9 })))
      .toEqual({ kind: 'request', feeEstimate: 9, previousRejected: false })
    expect(cancellationAction(order({ cancellationPhase: 'REQUEST', cancellationRequest: request() })))
      .toEqual({ kind: 'pending', feeAmount: 9 })
    expect(cancellationAction(order({ cancellationPhase: 'REQUEST', cancellationFeeEstimate: 9,
      cancellationRequest: request({ status: 'REJECTED', closureReason: 'OPS_REJECTED' }) })))
      .toEqual({ kind: 'request', feeEstimate: 9, previousRejected: true })
    expect(cancellationAction(order({ cancellationPhase: 'NOT_CANCELLABLE' }))).toEqual({ kind: 'none' })
    expect(cancellationAction(order({ cancellationPhase: 'ALREADY_CANCELLED' }))).toEqual({ kind: 'none' })
  })

  it('explains conflicts by their stable code', () => {
    expect(cancellationErrorMessage(axiosError(409, 'order_not_cancellable'))).toMatch(/já não pode ser cancelada/)
    expect(cancellationErrorMessage(axiosError(409, 'concurrent_modification'))).toMatch(/acabou de mudar/)
    expect(cancellationErrorMessage(new Error('network'))).toMatch(/Não foi possível/)
  })

  it('labels customer cancellations', () => {
    expect(statusLabel('CANCELLED', 'CUSTOMER_CANCELLED')).toBe('Cancelado por si')
    expect(statusLabel('CANCELLED', 'CUSTOMER_CANCELLED_WITH_FEE')).toBe('Cancelado por si')
  })
})

describe('OrderCancellationCard', () => {
  // Braces: a function returned from beforeEach would be run by Vitest as a teardown hook.
  beforeEach(() => { vi.mocked(orderService.cancelMine).mockReset() })
  afterEach(cleanup)

  it('free phase: cancels only after confirmation', async () => {
    vi.mocked(orderService.cancelMine).mockResolvedValue({
      orderId: 'o1', orderStatus: 'CANCELLED', cancellationReason: 'CUSTOMER_CANCELLED', outcome: 'CANCELLED', refundAmount: 100, request: null,
    })
    renderCard(order())

    fireEvent.click(screen.getByRole('button', { name: /Cancelar encomenda/ }))
    expect(orderService.cancelMine).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar cancelamento' }))

    await waitFor(() => expect(orderService.cancelMine).toHaveBeenCalledWith('o1', undefined))
  })

  it('request phase: shows the fee and sends the optional reason', async () => {
    vi.mocked(orderService.cancelMine).mockResolvedValue({
      orderId: 'o1', orderStatus: 'PREPARING', cancellationReason: null, outcome: 'REQUESTED', refundAmount: null, request: request(),
    })
    renderCard(order({ status: 'PREPARING', cancellationPhase: 'REQUEST', cancellationFeeEstimate: 9 }))

    expect(screen.getByText(/A loja já aceitou/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /Pedir cancelamento/ }))
    fireEvent.change(await screen.findByRole('textbox'), { target: { value: '  Mudei de ideias ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar pedido' }))

    await waitFor(() => expect(orderService.cancelMine).toHaveBeenCalledWith('o1', 'Mudei de ideias'))
  })

  it('pending request: no button, a notice instead', () => {
    renderCard(order({ status: 'ACCEPTED_BY_VENDOR', cancellationPhase: 'REQUEST', cancellationRequest: request() }))
    expect(screen.getByText('Pedido de cancelamento em análise')).toBeTruthy()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('not cancellable: renders nothing', () => {
    const { container } = renderCard(order({ status: 'READY_FOR_PICKUP', cancellationPhase: 'NOT_CANCELLABLE' }))
    expect(container.innerHTML).toBe('')
  })

  it('shows the conflict message when the order moved on', async () => {
    vi.mocked(orderService.cancelMine).mockRejectedValue(axiosError(409, 'order_not_cancellable'))
    renderCard(order())
    fireEvent.click(screen.getByRole('button', { name: /Cancelar encomenda/ }))
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar cancelamento' }))
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toMatch(/já não pode ser cancelada/)
  })
})
