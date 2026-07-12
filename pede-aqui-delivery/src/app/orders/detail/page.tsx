import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, MapPin, Bike, Phone, MessageSquare } from 'lucide-react'
import { orderService } from '@/lib/api/services'
import { statusLabel, isActiveOrder } from '@/lib/orderStatusLabels'
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
  const cached = orders.find((o) => o.id === orderId)

  const { data: tracking, isError } = useQuery({
    queryKey: ['order-tracking', orderId],
    queryFn: () => orderService.track(orderId!),
    enabled: !!orderId,
    refetchInterval: (query) => {
      const status = query.state.data?.orderStatus
      return status && isActiveOrder(status) ? 30_000 : false
    },
  })

  if (isError && !cached) {
    return (
      <div className="max-w-lg mx-auto px-4 py-20 text-center space-y-4">
        <p className="text-slate-500">Encomenda não encontrada.</p>
        <Link to="/orders" className="inline-block px-5 py-2.5 bg-brand-600 text-white font-bold text-xs rounded-full">Ver encomendas</Link>
      </div>
    )
  }

  const currentStatus = tracking?.orderStatus ?? cached?.status ?? ''
  const currentIdx = STATUS_TIMELINE.indexOf(currentStatus)
  const active = isActiveOrder(currentStatus)
  const reference = tracking?.reference ?? cached?.reference

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8 py-6 sm:py-10">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 text-left">
        <div>
          <Link to="/orders" className="inline-flex items-center gap-1 text-xs font-bold text-slate-400 hover:text-slate-700 mb-2">
            <ArrowLeft className="w-3.5 h-3.5" /> Encomendas
          </Link>
          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase ${active ? 'bg-emerald-100 text-emerald-800 animate-pulse' : 'bg-slate-100 text-slate-600'}`}>
            ● {active ? 'EM ANDAMENTO' : statusLabel(currentStatus)}
          </span>
          <h1 className="font-display font-extrabold text-2xl sm:text-4xl text-slate-800 tracking-tight leading-none mt-1.5 capitalize">
            {cached?.vendorName ?? 'Acompanhar Encomenda'}
          </h1>
          {reference && <p className="text-xs sm:text-sm text-slate-400 font-semibold uppercase mt-1">Ref: {reference}</p>}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left — stepper + items (real) */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white rounded-3xl border border-slate-100 p-6 shadow-sm text-left space-y-6">
            <h3 className="font-display font-extrabold text-slate-800 text-base pb-3 border-b border-slate-50">Progresso da Encomenda</h3>
            <div className="relative pl-8 space-y-6 before:absolute before:left-3.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-100">
              {STATUS_TIMELINE.map((status, idx) => {
                const done = currentIdx >= 0 && idx <= currentIdx
                const isCurrent = idx === currentIdx
                return (
                  <div key={status} className="relative flex justify-between gap-4">
                    <div className={`absolute -left-[27px] top-1.5 w-6 h-6 rounded-full flex items-center justify-center border-2 z-10 transition-all ${
                      isCurrent ? 'bg-brand-600 border-brand-600 shadow-md shadow-brand-500/30'
                      : done ? 'bg-emerald-500 border-emerald-500 text-white' : 'bg-white border-slate-200'
                    }`}>
                      {done && !isCurrent ? <span className="text-white text-[10px] font-bold">✓</span>
                        : <span className={`w-2 h-2 rounded-full ${isCurrent ? 'bg-white animate-ping' : 'bg-slate-300'}`} />}
                    </div>
                    <div className="text-left flex-1">
                      <h4 className={`font-display font-bold text-sm tracking-tight ${isCurrent ? 'text-brand-600 font-extrabold' : done ? 'text-slate-800' : 'text-slate-400'}`}>
                        {statusLabel(status)}
                      </h4>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Courier card — placeholder: backend exposes no courier assignment */}
          <div className="bg-white rounded-3xl border border-slate-100 p-5 sm:p-6 shadow-sm text-left flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
            <div className="flex gap-4 items-center">
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-slate-100 to-slate-50 flex items-center justify-center text-3xl">🛵</div>
              <div className="space-y-1">
                <span className="px-2 py-0.5 rounded bg-brand-50 text-brand-600 text-[9px] font-extrabold tracking-wide uppercase">Estafeta</span>
                <h3 className="font-display font-extrabold text-slate-800 text-base leading-none">
                  {active ? 'A ser atribuído' : 'Entrega concluída'}
                </h3>
                <p className="text-xs font-medium text-slate-400">Os dados do estafeta aparecerão quando disponíveis.</p>
              </div>
            </div>
            <div className="flex gap-2 w-full sm:w-auto opacity-50 pointer-events-none" title="Disponível quando o estafeta for atribuído">
              <span className="flex-1 sm:flex-none px-4 py-2.5 bg-slate-50 text-slate-500 font-bold text-xs rounded-xl border border-slate-100 flex items-center justify-center gap-1.5"><MessageSquare className="w-4 h-4" /> Mensagem</span>
              <span className="flex-1 sm:flex-none px-4 py-2.5 bg-brand-50 text-brand-700 font-bold text-xs rounded-xl border border-brand-100 flex items-center justify-center gap-1.5"><Phone className="w-4 h-4" /> Ligar</span>
            </div>
          </div>

          {/* Items (real, from cache) */}
          {cached?.items && cached.items.length > 0 && (
            <div className="bg-white rounded-3xl border border-slate-100 p-5 sm:p-6 shadow-sm text-left space-y-3">
              <h3 className="font-display font-extrabold text-slate-800 text-base pb-2 border-b border-slate-50">Itens</h3>
              {cached.items.map((item) => (
                <div key={item.id} className="flex justify-between text-sm text-slate-600">
                  <span>{item.quantity}× {item.productName}</span>
                  <span className="font-semibold text-slate-800">{formatMZN(item.lineTotal)}</span>
                </div>
              ))}
              <div className="border-t border-slate-100 pt-2 flex justify-between font-extrabold text-slate-800">
                <span>Total</span><span className="text-brand-600">{formatMZN(cached.total)}</span>
              </div>
            </div>
          )}
        </div>

        {/* Right — map placeholder (illustrative, no live GPS available) */}
        <div className="space-y-6">
          <div className="bg-white rounded-3xl border border-slate-100 p-4 shadow-sm space-y-4 text-left">
            <h3 className="font-display font-extrabold text-slate-800 text-base">Localização</h3>
            <div className="relative h-80 w-full rounded-2xl bg-gradient-to-br from-slate-100 to-slate-200 border border-slate-200 overflow-hidden flex items-center justify-center">
              <div className="absolute inset-0 opacity-[0.15] bg-[radial-gradient(circle_at_30%_40%,#334155_0,transparent_40%),radial-gradient(circle_at_70%_75%,#334155_0,transparent_40%)]" />
              <div className="absolute" style={{ left: '30%', top: '38%' }}>
                <div className="bg-slate-900 border-2 border-white px-2 py-1 rounded-lg text-[9px] font-bold text-white shadow-md flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-brand-500" /> Loja</div>
              </div>
              <div className="absolute" style={{ left: '66%', top: '72%' }}>
                <div className="bg-emerald-600 border-2 border-white px-2 py-1 rounded-lg text-[9px] font-bold text-white shadow-md flex items-center gap-1"><MapPin className="w-2.5 h-2.5" /> A sua casa</div>
              </div>
              <div className="relative z-10 flex flex-col items-center gap-2 text-slate-500">
                <Bike className="w-8 h-8" />
                <p className="text-[11px] font-semibold">Mapa em tempo real em breve</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
