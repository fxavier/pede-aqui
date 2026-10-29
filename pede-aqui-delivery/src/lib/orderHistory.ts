import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { orderService } from '@/lib/api/services'
import type { CursorPage, CustomerOrderSummary } from '@/lib/api/types'
import { isActiveOrder } from '@/lib/orderStatusLabels'

/** Orders per "Carregar mais" page. */
export const MY_ORDERS_PAGE_SIZE = 20

/** Detail polling while the order is in progress (no WebSocket yet). */
export const ACTIVE_ORDER_POLL_MS = 30_000

/** Key of the order history cache that older builds wrote; read by nothing anymore, only purged. */
export const LEGACY_ORDER_CACHE_KEY = 'pede-aqui:orders'

export const myOrdersQueryKey = ['my-orders'] as const
export const myOrderQueryKey = (orderId: string) => ['my-order', orderId] as const

/** Cursor for the page after `lastPage`, or undefined when it was the last one (TanStack stops there). */
export function nextOrdersCursor(lastPage: CursorPage<CustomerOrderSummary>): string | undefined {
  return lastPage.nextCursor ?? undefined
}

/** All loaded pages as one list, in server order, without duplicates. */
export function flattenOrderPages(pages: CursorPage<CustomerOrderSummary>[] | undefined): CustomerOrderSummary[] {
  if (!pages) return []
  const seen = new Set<string>()
  const orders: CustomerOrderSummary[] = []
  for (const page of pages) {
    for (const order of page.items) {
      if (seen.has(order.id)) continue
      seen.add(order.id)
      orders.push(order)
    }
  }
  return orders
}

/** Refetch interval for an order detail: poll while the order is in progress, stop once it is finished. */
export function orderPollInterval(status: string | undefined): number | false {
  return status && isActiveOrder(status) ? ACTIVE_ORDER_POLL_MS : false
}

/**
 * Removes the order history that older builds kept in localStorage (it held addresses and delivery PINs).
 * Orders now come only from the API; other keys (e.g. the cart) are left alone.
 */
export function purgeLegacyOrderCache(storage: Pick<Storage, 'removeItem'> | undefined = globalThis.localStorage): void {
  try {
    storage?.removeItem(LEGACY_ORDER_CACHE_KEY)
  } catch {
    // Storage blocked (private mode, disabled site data): nothing to purge.
  }
}

/** The signed-in customer's order history with cursor-based "load more" (GET /me/orders). */
export function useMyOrders() {
  return useInfiniteQuery({
    queryKey: myOrdersQueryKey,
    queryFn: ({ pageParam }) => orderService.listMine({ limit: MY_ORDERS_PAGE_SIZE, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: nextOrdersCursor,
  })
}

/** One of the signed-in customer's orders (GET /me/orders/{id}), polled while it is in progress. */
export function useMyOrder(orderId: string | undefined) {
  return useQuery({
    queryKey: myOrderQueryKey(orderId ?? ''),
    queryFn: () => orderService.getMine(orderId!),
    enabled: !!orderId,
    refetchInterval: (query) => orderPollInterval(query.state.data?.status),
  })
}
