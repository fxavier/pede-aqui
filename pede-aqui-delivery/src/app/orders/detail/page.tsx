import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle, Circle } from 'lucide-react'
import { orderService } from '@/lib/api/services'
import { statusLabel, isActiveOrder } from '@/lib/orderStatusLabels'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { OrderResponse } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'

const STATUS_TIMELINE = [
  'PAYMENT_CONFIRMED',
  'ACCEPTED_BY_VENDOR',
  'PREPARING',
  'READY_FOR_PICKUP',
  'PICKED_UP',
  'ON_ROUTE_TO_CUSTOMER',
  'DELIVERED',
]

export default function OrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>()

  const orders: OrderResponse[] = JSON.parse(localStorage.getItem('pede-aqui:orders') ?? '[]')
  const cachedOrder = orders.find((o) => o.id === orderId)

  const { data: tracking, isError } = useQuery({
    queryKey: ['order-tracking', orderId],
    queryFn: () => orderService.track(orderId!),
    enabled: !!orderId,
    refetchInterval: (query) => {
      const status = query.state.data?.orderStatus
      return status && isActiveOrder(status) ? 30_000 : false
    },
  })

  if (isError && !cachedOrder) {
    return (
      <div className="flex flex-col items-center gap-4 py-20 text-center">
        <p className="text-muted-foreground">Pedido não encontrado</p>
        <Button variant="outline" asChild>
          <Link to="/orders">Ver pedidos</Link>
        </Button>
      </div>
    )
  }

  const currentStatus = tracking?.orderStatus ?? cachedOrder?.status ?? ''
  const currentIdx = STATUS_TIMELINE.indexOf(currentStatus)

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <Button variant="ghost" size="sm" asChild className="-ml-2">
        <Link to="/orders"><ArrowLeft className="h-4 w-4 mr-1" />Pedidos</Link>
      </Button>

      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold">{cachedOrder?.vendorName ?? 'Pedido'}</h1>
          {(tracking?.reference ?? cachedOrder?.reference) && (
            <p className="text-sm text-muted-foreground">{tracking?.reference ?? cachedOrder?.reference}</p>
          )}
        </div>
        <Badge>{statusLabel(currentStatus)}</Badge>
      </div>

      {/* Status timeline */}
      <div className="space-y-3">
        {STATUS_TIMELINE.map((status, idx) => {
          const done = idx <= currentIdx
          const active = idx === currentIdx
          return (
            <div key={status} className="flex items-center gap-3">
              {done
                ? <CheckCircle className={`h-5 w-5 shrink-0 ${active ? 'text-primary' : 'text-green-500'}`} />
                : <Circle className="h-5 w-5 shrink-0 text-muted-foreground/30" />}
              <span className={`text-sm ${done ? 'font-medium' : 'text-muted-foreground'}`}>
                {statusLabel(status)}
              </span>
            </div>
          )
        })}
      </div>

      {/* Order items from cache */}
      {cachedOrder?.items && cachedOrder.items.length > 0 && (
        <div className="rounded-lg border p-4 space-y-3">
          <h2 className="font-medium text-sm text-muted-foreground">Itens</h2>
          {cachedOrder.items.map((item) => (
            <div key={item.id} className="flex justify-between text-sm">
              <span>{item.quantity}× {item.productName}</span>
              <span>{formatMZN(item.lineTotal)}</span>
            </div>
          ))}
          <div className="border-t pt-2 flex justify-between font-semibold text-sm">
            <span>Total</span>
            <span>{formatMZN(cachedOrder.total)}</span>
          </div>
        </div>
      )}
    </div>
  )
}
