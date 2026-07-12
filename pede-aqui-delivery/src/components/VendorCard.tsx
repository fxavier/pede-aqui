import { Link } from 'react-router-dom'
import { Star, Clock, MapPin, Bike } from 'lucide-react'
import type { VendorSearchResult } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'
import { vendorCover } from '@/lib/covers'

export function VendorCard({ vendor }: { vendor: VendorSearchResult }) {
  const { emoji, from, to } = vendorCover(vendor.name)
  const fast = vendor.estimatedDeliveryMinutes != null && vendor.estimatedDeliveryMinutes <= 20

  return (
    <Link
      to={`/vendor/${vendor.vendorId}`}
      className="group flex h-full flex-col justify-between overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-warm transition-all duration-300 hover:-translate-y-1 hover:shadow-warm-lg"
    >
      {/* Cover (emoji/gradient — backend has no images) */}
      <div className="relative flex h-44 select-none items-center justify-center text-6xl"
           style={{ background: `linear-gradient(135deg, ${from} 0%, ${to} 100%)` }}>
        {/* Soft top-light so covers read as lit surfaces rather than flat fills */}
        <div className="pointer-events-none absolute inset-0" style={{ background: 'radial-gradient(circle at 30% 20%, rgba(255,255,255,0.55), transparent 55%)' }} />
        <span className="relative drop-shadow-md transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-110">{emoji}</span>

        {fast && vendor.available && (
          <span className="absolute left-3 top-3 z-10 rounded-full bg-brand-600 px-2.5 py-1 text-[10px] font-extrabold uppercase tracking-wider text-white shadow-lg shadow-brand-500/20">
            Rápido 🔥
          </span>
        )}
        {vendor.available ? (
          <span className="absolute right-3 top-3 flex items-center gap-1 rounded-full bg-white/90 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 shadow-sm backdrop-blur-sm">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-500" /> Aberto
          </span>
        ) : (
          <div className="absolute inset-0 flex items-center justify-center bg-white/75 backdrop-blur-[3px]">
            <span className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-slate-500 shadow-sm ring-1 ring-slate-200">Fechado</span>
          </div>
        )}
      </div>

      <div className="flex-1 space-y-1.5 p-5 text-left">
        <h3 className="line-clamp-1 font-display text-lg font-extrabold leading-snug tracking-tight text-slate-800 transition-colors group-hover:text-brand-600">
          {vendor.name}
        </h3>
      </div>

      {/* Footer meta */}
      <div className="flex items-center justify-between border-t border-slate-50 bg-slate-50/70 px-5 py-4 text-xs font-semibold text-slate-500">
        <div className="flex items-center gap-1.5 rounded-md border border-slate-100 bg-white px-2 py-1 shadow-sm">
          <Star className="h-3.5 w-3.5 fill-amber-500 text-amber-500" />
          <span className="font-bold text-slate-800">{vendor.rating > 0 ? vendor.rating.toFixed(1) : 'Novo'}</span>
        </div>

        <div className="flex items-center gap-3">
          {vendor.estimatedDeliveryMinutes != null && (
            <div className="flex items-center gap-1">
              <Clock className="h-3.5 w-3.5 text-slate-400" />
              <span>{vendor.estimatedDeliveryMinutes} min</span>
            </div>
          )}
          <div className="flex items-center gap-1">
            <MapPin className="h-3.5 w-3.5 text-slate-400" />
            <span>{vendor.distanceKm.toFixed(1)} km</span>
          </div>
        </div>

        <div className="flex items-center gap-1 text-right">
          <Bike className="h-3.5 w-3.5 text-slate-400" />
          <span className="font-bold text-slate-700">
            {vendor.deliveryFee == null ? '—' : vendor.deliveryFee === 0 ? 'Grátis' : formatMZN(vendor.deliveryFee)}
          </span>
        </div>
      </div>
    </Link>
  )
}
