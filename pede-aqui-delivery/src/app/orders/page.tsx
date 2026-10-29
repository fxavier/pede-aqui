import { Link } from 'react-router-dom'
import { CheckCircle, XCircle, ChevronRight, HelpCircle, Loader2 } from 'lucide-react'
import { statusLabel, isSellerRejected } from '@/lib/orderStatusLabels'
import { flattenOrderPages, useMyOrders } from '@/lib/orderHistory'
import { formatMZN } from '@/lib/utils'

export default function OrdersPage() {
  const { data, isPending, isError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useMyOrders()
  const orders = flattenOrderPages(data?.pages)

  if (isPending) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 flex justify-center text-slate-400">
        <Loader2 className="w-6 h-6 animate-spin" aria-label="A carregar encomendas" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="max-w-lg mx-auto px-4 py-20 text-center space-y-4">
        <p className="text-slate-500">Não foi possível carregar as suas encomendas.</p>
        <button onClick={() => refetch()} className="inline-block px-5 py-2.5 bg-brand-600 text-white font-bold text-xs rounded-full">Tentar novamente</button>
      </div>
    )
  }

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

      <div className="space-y-6">
        {orders.map((order) => {
          const delivered = order.status === 'DELIVERED'
          const cancelled = order.status === 'CANCELLED'
          const date = new Date(order.createdAt).toLocaleDateString('pt-MZ')
          return (
            <div key={order.id} className="bg-white rounded-3xl border border-slate-100 shadow-sm p-5 sm:p-6 space-y-4">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3.5 border-b border-slate-50">
                <div className="flex gap-3 items-center">
                  <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-slate-100 to-slate-50 flex items-center justify-center text-2xl">🏪</div>
                  <div>
                    <h3 className="font-display font-extrabold text-slate-800 text-sm sm:text-base leading-tight capitalize">{order.vendorName}</h3>
                    <p className="text-slate-400 text-[11px] font-medium mt-0.5">{date} • Ref: {order.reference}</p>
                  </div>
                </div>
                {delivered && <Pill className="bg-emerald-50 text-emerald-800 border-emerald-100"><CheckCircle className="w-3.5 h-3.5" /> Entregue</Pill>}
                {cancelled && <Pill className="bg-rose-50 text-rose-800 border-rose-100"><XCircle className="w-3.5 h-3.5" /> {isSellerRejected(order.status, order.cancellationReason) ? statusLabel(order.status, order.cancellationReason) : 'Cancelada'}</Pill>}
                {!delivered && !cancelled && <Pill className="bg-brand-50 text-brand-700 border-brand-100">{statusLabel(order.status)}</Pill>}
              </div>

              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pt-4 border-t border-slate-50">
                <div className="text-slate-500 font-medium text-xs">
                  <span>{order.itemCount} {order.itemCount === 1 ? 'artigo' : 'artigos'} • Total: </span>
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

      {hasNextPage && (
        <div className="flex justify-center">
          <button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            className="px-5 py-2.5 bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 font-bold text-xs rounded-full flex items-center gap-2 disabled:opacity-60 transition-all"
          >
            {isFetchingNextPage && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
            Carregar mais
          </button>
        </div>
      )}
    </div>
  )
}

function Pill({ children, className }: { children: React.ReactNode; className: string }) {
  return <span className={`inline-flex items-center gap-1 px-3 py-1 text-[10px] font-extrabold rounded-full border ${className}`}>{children}</span>
}
