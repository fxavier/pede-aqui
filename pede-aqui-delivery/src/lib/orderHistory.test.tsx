import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { CursorPage, CustomerOrderDetail, CustomerOrderSummary } from '@/lib/api/types'

vi.mock('@/lib/api/services', () => ({
  orderService: { listMine: vi.fn(), getMine: vi.fn() },
}))

import { orderService } from '@/lib/api/services'
import {
  ACTIVE_ORDER_POLL_MS,
  LEGACY_ORDER_CACHE_KEY,
  MY_ORDERS_PAGE_SIZE,
  flattenOrderPages,
  nextOrdersCursor,
  orderPollInterval,
  purgeLegacyOrderCache,
  useMyOrder,
  useMyOrders,
} from './orderHistory'

function summary(id: string): CustomerOrderSummary {
  return {
    id, reference: `REF-${id}`, status: 'DELIVERED', cancellationReason: null, vendorId: 'v1', vendorName: 'Loja',
    itemCount: 1, subtotal: 90, fees: 10, taxes: 0, discountTotal: 0, total: 100, createdAt: '2026-10-01T10:00:00Z',
  }
}

function page(ids: string[], nextCursor: string | null): CursorPage<CustomerOrderSummary> {
  return { items: ids.map(summary), nextCursor }
}

function wrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

describe('order history helpers', () => {
  it('nextOrdersCursor stops on the last page', () => {
    expect(nextOrdersCursor(page(['a'], 'c1'))).toBe('c1')
    expect(nextOrdersCursor(page(['a'], null))).toBeUndefined()
  })

  it('flattenOrderPages keeps server order and drops duplicates', () => {
    expect(flattenOrderPages(undefined)).toEqual([])
    const flat = flattenOrderPages([page(['a', 'b'], 'c1'), page(['b', 'c'], null)])
    expect(flat.map((o) => o.id)).toEqual(['a', 'b', 'c'])
  })

  it('polls only while the order is in progress', () => {
    expect(orderPollInterval('PREPARING')).toBe(ACTIVE_ORDER_POLL_MS)
    expect(orderPollInterval('DELIVERED')).toBe(false)
    expect(orderPollInterval('CANCELLED')).toBe(false)
    expect(orderPollInterval(undefined)).toBe(false)
  })

  it('purgeLegacyOrderCache removes only the legacy order key', () => {
    localStorage.setItem(LEGACY_ORDER_CACHE_KEY, '[{"deliveryCode":"123456"}]')
    localStorage.setItem('pede-aqui:cart', '{"items":[]}')
    purgeLegacyOrderCache()
    expect(localStorage.getItem(LEGACY_ORDER_CACHE_KEY)).toBeNull()
    expect(localStorage.getItem('pede-aqui:cart')).toBe('{"items":[]}')
  })

  it('purgeLegacyOrderCache tolerates blocked storage', () => {
    const blocked = { removeItem: () => { throw new Error('SecurityError') } }
    expect(() => purgeLegacyOrderCache(blocked)).not.toThrow()
  })
})

describe('useMyOrders', () => {
  beforeEach(() => vi.mocked(orderService.listMine).mockReset())

  it('loads the first page without a cursor and the next one with nextCursor', async () => {
    vi.mocked(orderService.listMine)
      .mockResolvedValueOnce(page(['a', 'b'], 'cursor-1'))
      .mockResolvedValueOnce(page(['c'], null))

    const { result } = renderHook(() => useMyOrders(), { wrapper: wrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(orderService.listMine).toHaveBeenNthCalledWith(1, { limit: MY_ORDERS_PAGE_SIZE, cursor: null })
    expect(result.current.hasNextPage).toBe(true)

    await act(() => result.current.fetchNextPage())
    await waitFor(() => expect(result.current.data?.pages).toHaveLength(2))
    expect(orderService.listMine).toHaveBeenNthCalledWith(2, { limit: MY_ORDERS_PAGE_SIZE, cursor: 'cursor-1' })
    expect(result.current.hasNextPage).toBe(false)
    expect(flattenOrderPages(result.current.data?.pages).map((o) => o.id)).toEqual(['a', 'b', 'c'])
  })
})

describe('useMyOrder', () => {
  beforeEach(() => vi.mocked(orderService.getMine).mockReset())

  it('fetches the detail from the API, seller rejection included', async () => {
    const detail = {
      ...summary('x'), status: 'CANCELLED', cancellationReason: 'SELLER_REJECTED', items: [], deliveryAddress: null,
      deliveryCode: null, deliveryFee: null, serviceFee: null, deliveryFeeBasis: null, distanceKm: null,
    } as unknown as CustomerOrderDetail
    vi.mocked(orderService.getMine).mockResolvedValueOnce(detail)

    const { result } = renderHook(() => useMyOrder('x'), { wrapper: wrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(orderService.getMine).toHaveBeenCalledWith('x')
    expect(result.current.data?.cancellationReason).toBe('SELLER_REJECTED')
  })

  it('does not call the API without an order id', () => {
    renderHook(() => useMyOrder(undefined), { wrapper: wrapper() })
    expect(orderService.getMine).not.toHaveBeenCalled()
  })
})
