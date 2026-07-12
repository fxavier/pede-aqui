import { useParams, Link } from 'react-router-dom'
import { CheckCircle2 } from 'lucide-react'
import type { OrderResponse } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'

export default function OrderConfirmationPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const orders: OrderResponse[] = JSON.parse(localStorage.getItem('pede-aqui:orders') ?? '[]')
  const order = orders.find((o) => o.id === orderId)

  return (
    <div className="max-w-lg mx-auto px-4 sm:px-6 space-y-6 py-10 text-center">
      <div className="w-20 h-20 rounded-full bg-emerald-50 flex items-center justify-center mx-auto">
        <CheckCircle2 className="w-12 h-12 text-emerald-500" />
      </div>
      <div>
        <h1 className="font-display font-extrabold text-2xl text-slate-800 tracking-tight">Encomenda confirmada!</h1>
        {order?.reference && <p className="text-slate-400 text-sm mt-1">Referência: {order.reference}</p>}
      </div>

      {order && (
        <div className="bg-white rounded-3xl border border-slate-100 shadow-sm p-5 text-left space-y-3">
          {order.vendorName && <p className="font-display font-bold text-slate-800 capitalize">{order.vendorName}</p>}
          {order.items?.map((item) => (
            <div key={item.id} className="flex justify-between text-sm text-slate-600">
              <span>{item.quantity}× {item.productName}</span>
              <span className="font-semibold text-slate-800">{formatMZN(item.lineTotal)}</span>
            </div>
          ))}
          <div className="border-t border-slate-100 pt-2 flex justify-between font-extrabold text-slate-800">
            <span>Total</span><span className="text-brand-600">{formatMZN(order.total)}</span>
          </div>
        </div>
      )}

      <p className="text-sm text-slate-400">O restaurante confirmará o seu pedido em breve.</p>

      <div className="flex flex-col gap-3">
        <Link to={`/orders/${orderId}`} className="w-full bg-brand-600 hover:bg-brand-500 text-white font-bold h-12 rounded-xl flex items-center justify-center shadow-lg shadow-brand-500/20 transition-all">Acompanhar encomenda</Link>
        <Link to="/orders" className="w-full border border-slate-200 text-slate-700 font-bold h-11 rounded-xl flex items-center justify-center hover:bg-slate-50 transition-all">Ver as minhas encomendas</Link>
        <Link to="/" className="w-full text-slate-400 font-bold h-10 rounded-xl flex items-center justify-center hover:text-slate-600 transition-all text-sm">Continuar a comprar</Link>
      </div>
    </div>
  )
}
