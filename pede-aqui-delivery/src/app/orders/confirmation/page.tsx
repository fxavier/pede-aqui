import { useParams, Link } from 'react-router-dom'
import { CheckCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { OrderResponse } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'

export default function OrderConfirmationPage() {
  const { orderId } = useParams<{ orderId: string }>()

  const orders: OrderResponse[] = JSON.parse(localStorage.getItem('pede-aqui:orders') ?? '[]')
  const order = orders.find((o) => o.id === orderId)

  return (
    <div className="mx-auto max-w-lg space-y-6 py-8 text-center">
      <CheckCircle className="mx-auto h-16 w-16 text-green-500" />
      <div>
        <h1 className="text-2xl font-bold">Pedido confirmado!</h1>
        {order?.reference && (
          <p className="text-muted-foreground">Referência: {order.reference}</p>
        )}
      </div>

      {order && (
        <div className="rounded-lg border p-4 text-left space-y-3">
          {order.vendorName && <p className="font-medium">{order.vendorName}</p>}
          {order.items?.map((item) => (
            <div key={item.id} className="flex justify-between text-sm">
              <span>{item.quantity}× {item.productName}</span>
              <span>{formatMZN(item.lineTotal)}</span>
            </div>
          ))}
          <div className="border-t pt-2 flex justify-between font-semibold">
            <span>Total</span>
            <span>{formatMZN(order.total)}</span>
          </div>
        </div>
      )}

      <p className="text-sm text-muted-foreground">
        O restaurante confirmará o teu pedido em breve. Podes acompanhar o estado abaixo.
      </p>

      <div className="flex flex-col gap-3">
        <Button asChild>
          <Link to={`/orders/${orderId}`}>Acompanhar pedido</Link>
        </Button>
        <Button variant="outline" asChild>
          <Link to="/orders">Ver meus pedidos</Link>
        </Button>
        <Button variant="ghost" asChild>
          <Link to="/">Continuar a comprar</Link>
        </Button>
      </div>
    </div>
  )
}
