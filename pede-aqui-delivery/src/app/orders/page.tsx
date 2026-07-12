import { Link } from 'react-router-dom'
import { CheckCircle, XCircle, ChevronRight, HelpCircle } from 'lucide-react'
import { statusLabel } from '@/lib/orderStatusLabels'
import type { OrderResponse } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'

export default function OrdersPage() {
  const orders: OrderResponse[] = JSON.parse(localStorage.getItem('pede-aqui:orders') ?? '[]')

  if (orders.length === 0) {
    return (
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col items-center gap-6 py-20 text-center">
        <span className="text-5xl">📋</span>
        <div>
          <h1 className="font-display text-xl font-bold text-slate-800">Ainda não fez nenhuma encomenda</h1>
          <p className="text-slate-400 mt-1 text-sm">Quando fizer um pedido, aparecerá aqui.</p>
        </div>
        <Link to="/" className="px-5 py-2.5 bg-brand-600 hover:bg-brand-500 text-white font-bold text-xs rounded-full shadow-md shadow-brand-500/10 transition-all">Pedir agora</Link>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 space-y-8 py-6 sm:py-10 text-left">
      <div className="space-y-2">
        <h1 className="font-display font-extrabold text-2xl sm:text-4xl text-slate-800 tracking-tight leading-none">Histórico de Encomendas</h1>
        <p className="text-slate-400 text-xs sm:text-sm font-medium">Acompanhe as suas encomendas anteriores</p>
      </div>

      {/* Stats — first tile is real, others cosmetic */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatTile label="TOTAL DE ENCOMENDAS" value={`${orders.length}`} sub="A sua conta está ativa" subClass="text-emerald-600" />
        <StatTile label="PONTOS GANHOS" value="—" sub="Programa em breve" subClass="text-slate-400" valueClass="text-brand-600" />
        <StatTile label="POUPANÇA ESTIMADA" value="—" sub="Com descontos e entregas" subClass="text-brand-500" />
      </div>

      <div className="space-y-6">
        {orders.map((order) => {
          const delivered = order.status === 'DELIVERED'
          const cancelled = order.status === 'CANCELLED'
          const date = order.createdAt ? new Date(order.createdAt).toLocaleDateString('pt-MZ') : null
          return (
            <div key={order.id} className="bg-white rounded-3xl border border-slate-100 shadow-sm p-5 sm:p-6 space-y-4">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3.5 border-b border-slate-50">
                <div className="flex gap-3 items-center">
                  <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-slate-100 to-slate-50 flex items-center justify-center text-2xl">🏪</div>
                  <div>
                    <h3 className="font-display font-extrabold text-slate-800 text-sm sm:text-base leading-tight capitalize">{order.vendorName ?? 'Encomenda'}</h3>
                    <p className="text-slate-400 text-[11px] font-medium mt-0.5">{date ? `${date} • ` : ''}Ref: {order.reference}</p>
                  </div>
                </div>
                {delivered && <Pill className="bg-emerald-50 text-emerald-800 border-emerald-100"><CheckCircle className="w-3.5 h-3.5" /> Entregue</Pill>}
                {cancelled && <Pill className="bg-rose-50 text-rose-800 border-rose-100"><XCircle className="w-3.5 h-3.5" /> Cancelada</Pill>}
                {!delivered && !cancelled && <Pill className="bg-brand-50 text-brand-700 border-brand-100">{statusLabel(order.status)}</Pill>}
              </div>

              {order.items && order.items.length > 0 && (
                <div className="space-y-3 py-1">
                  {order.items.map((item) => (
                    <div key={item.id} className="flex gap-3 items-center justify-between">
                      <div className="flex gap-3 items-center min-w-0 flex-1">
                        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-slate-100 to-slate-50 flex items-center justify-center text-xl flex-shrink-0">🍽️</div>
                        <p className="font-bold text-slate-800 text-xs sm:text-sm truncate">{item.quantity}× {item.productName}</p>
                      </div>
                      <span className="font-display font-extrabold text-slate-800 text-xs sm:text-sm flex-shrink-0">{formatMZN(item.lineTotal)}</span>
                    </div>
                  ))}
                </div>
              )}

              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pt-4 border-t border-slate-50">
                <div className="text-slate-500 font-medium text-xs">
                  <span>Total: </span>
                  <span className="font-display font-extrabold text-slate-800 text-sm">{formatMZN(order.total)}</span>
                </div>
                <div className="flex gap-2 w-full sm:w-auto">
                  <button className="flex-1 sm:flex-none px-3.5 py-2 bg-slate-50 hover:bg-slate-100 text-slate-600 border border-slate-200 font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 transition-all">
                    <HelpCircle className="w-3.5 h-3.5" /> Ajuda
                  </button>
                  <Link to={`/orders/${order.id}`} className="flex-1 sm:flex-none px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 shadow-md shadow-brand-500/10 transition-all">
                    Acompanhar <ChevronRight className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function StatTile({ label, value, sub, subClass, valueClass }: { label: string; value: string; sub: string; subClass: string; valueClass?: string }) {
  return (
    <div className="bg-white border border-slate-100 p-4 rounded-2xl shadow-sm">
      <p className="text-[10px] text-slate-400 font-bold tracking-wider uppercase leading-none">{label}</p>
      <p className={`font-display font-extrabold text-xl sm:text-2xl mt-1 ${valueClass ?? 'text-slate-800'}`}>{value}</p>
      <p className={`text-[10px] font-bold mt-1 ${subClass}`}>{sub}</p>
    </div>
  )
}

function Pill({ children, className }: { children: React.ReactNode; className: string }) {
  return <span className={`inline-flex items-center gap-1 px-3 py-1 text-[10px] font-extrabold rounded-full border ${className}`}>{children}</span>
}
