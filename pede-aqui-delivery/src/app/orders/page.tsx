import { Link } from 'react-router-dom'
import { OrderCard } from '@/components/OrderCard'
import { Button } from '@/components/ui/button'
import type { OrderResponse } from '@/lib/api/types'

export default function OrdersPage() {
  const orders: OrderResponse[] = JSON.parse(localStorage.getItem('pede-aqui:orders') ?? '[]')

  if (orders.length === 0) {
    return (
      <div className="flex flex-col items-center gap-6 py-20 text-center">
        <span className="text-5xl">📋</span>
        <div>
          <h1 className="text-xl font-semibold">Ainda não fizeste nenhum pedido</h1>
          <p className="text-muted-foreground mt-1">Quando fizeres um pedido, aparecerá aqui.</p>
        </div>
        <Button asChild>
          <Link to="/">Pedir agora</Link>
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Os meus pedidos</h1>
      {orders.map((order) => (
        <OrderCard key={order.id} order={order} />
      ))}
    </div>
  )
}
