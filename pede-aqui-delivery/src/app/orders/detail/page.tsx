import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, MapPin, Bike, Phone, MessageSquare, KeyRound } from 'lucide-react'
import { statusLabel, isActiveOrder } from '@/lib/orderStatusLabels'
import { useMyOrder } from '@/lib/orderHistory'
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
  const { data: order, isPending, isError } = useMyOrder(orderId)

  if (isPending) {
    return <div className="max-w-lg mx-auto px-4 py-20 text-center text-slate-400 text-sm">A carregar encomenda…</div>
  }

  if (isError || !order) {
    return (
      <div className="max-w-lg mx-auto px-4 py-20 text-center space-y-4">
        <p className="text-slate-500">Encomenda não encontrada.</p>
        <Link to="/orders" className="inline-block px-5 py-2.5 bg-brand-600 text-white font-bold text-xs rounded-full">Ver encomendas</Link>
      </div>
    )
  }

  const currentStatus = order.status
  const currentIdx = STATUS_TIMELINE.indexOf(currentStatus)
  const active = isActiveOrder(currentStatus)
  const address = order.deliveryAddress

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8 py-6 sm:py-10">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 text-left">
        <div>
          <Link to="/orders" className="inline-flex items-center gap-1 text-xs font-bold text-slate-400 hover:text-slate-700 mb-2">
            <ArrowLeft className="w-3.5 h-3.5" /> Encomendas
          </Link>
          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase ${active ? 'bg-emerald-100 text-emerald-800 animate-pulse' : 'bg-slate-100 text-slate-600'}`}>
            ● {active ? 'EM ANDAMENTO' : statusLabel(currentStatus, order.cancellationReason)}
          </span>
          <h1 className="font-display font-extrabold text-2xl sm:text-4xl text-slate-800 tracking-tight leading-none mt-1.5 capitalize">
            {order.vendorName}
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 font-semibold uppercase mt-1">Ref: {order.reference}</p>
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

          {/* Items and totals (from GET /me/orders/{id}) */}
          <div className="bg-white rounded-3xl border border-slate-100 p-5 sm:p-6 shadow-sm text-left space-y-3">
            <h3 className="font-display font-extrabold text-slate-800 text-base pb-2 border-b border-slate-50">Itens</h3>
            {order.items.map((item) => (
              <div key={item.id} className="flex justify-between text-sm text-slate-600">
                <span>{item.quantity}× {item.productName}</span>
                <span className="font-semibold text-slate-800">{formatMZN(item.lineTotal)}</span>
              </div>
            ))}
            <div className="border-t border-slate-100 pt-2 space-y-1 text-sm text-slate-500">
              <Row label="Subtotal" value={formatMZN(order.subtotal)} />
              {order.deliveryFee != null && <Row label="Taxa de entrega" value={formatMZN(order.deliveryFee)} />}
              {order.serviceFee != null && <Row label="Taxa de serviço" value={formatMZN(order.serviceFee)} />}
              {order.deliveryFee == null && <Row label="Taxas" value={formatMZN(order.fees)} />}
              {order.taxes > 0 && <Row label="IVA" value={formatMZN(order.taxes)} />}
              {order.discountTotal > 0 && <Row label="Desconto" value={`−${formatMZN(order.discountTotal)}`} />}
            </div>
            <div className="border-t border-slate-100 pt-2 flex justify-between font-extrabold text-slate-800">
              <span>Total</span><span className="text-brand-600">{formatMZN(order.total)}</span>
            </div>
          </div>
        </div>

        {/* Right — map placeholder (illustrative, no live GPS available) */}
        <div className="space-y-6">
          {order.deliveryCode && (
            <div className="bg-white rounded-3xl border border-brand-100 p-5 shadow-sm text-left space-y-2">
              <div className="flex items-center gap-2 text-brand-700">
                <KeyRound className="w-4 h-4" />
                <h3 className="font-display font-extrabold text-base">Código de entrega</h3>
              </div>
              <p className="font-display font-extrabold text-3xl tracking-[0.3em] text-slate-800">{order.deliveryCode}</p>
              <p className="text-xs text-slate-400">Diga este código ao estafeta apenas quando receber a encomenda.</p>
            </div>
          )}

          {address && (
            <div className="bg-white rounded-3xl border border-slate-100 p-5 shadow-sm text-left space-y-1">
              <h3 className="font-display font-extrabold text-slate-800 text-base pb-2">Morada de entrega</h3>
              <p className="text-sm font-semibold text-slate-700">{address.recipientName}</p>
              <p className="text-sm text-slate-500">{[address.line1, address.line2].filter(Boolean).join(', ')}</p>
              <p className="text-sm text-slate-500">{[address.city, address.state, address.postalCode].filter(Boolean).join(', ')}</p>
              {address.instructions && <p className="text-xs text-slate-400 pt-1">{address.instructions}</p>}
            </div>
          )}

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

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span>{label}</span><span className="font-semibold text-slate-700">{value}</span>
    </div>
  )
}
