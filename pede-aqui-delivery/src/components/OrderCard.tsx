import { Link } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { statusLabel } from '@/lib/orderStatusLabels'
import type { OrderResponse } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'

export function OrderCard({ order }: { order: OrderResponse }) {
  const date = order.createdAt ? new Date(order.createdAt).toLocaleDateString('pt-MZ') : null

  return (
    <Link to={`/orders/${order.id}`}>
      <Card className="cursor-pointer transition-shadow hover:shadow-md">
        <CardContent className="p-4 space-y-2">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="font-medium">{order.vendorName ?? 'Pedido'}</p>
              <p className="text-xs text-muted-foreground">{order.reference}</p>
            </div>
            <Badge variant="outline">{statusLabel(order.status)}</Badge>
          </div>
          <div className="flex items-center justify-between text-sm text-muted-foreground">
            <span>{formatMZN(order.total)}</span>
            {date && <span>{date}</span>}
          </div>
        </CardContent>
      </Card>
    </Link>
  )
}
