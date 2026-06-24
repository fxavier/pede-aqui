import { Link } from 'react-router-dom'
import { Star, Clock, Bike } from 'lucide-react'
import type { VendorSearchResult } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'

function vendorVisual(name: string): { emoji: string; from: string; to: string } {
  const n = name.toLowerCase()
  if (n.includes('burger') || n.includes('fast'))      return { emoji: '🍔', from: '#FFF7E6', to: '#FDE68A' }
  if (n.includes('pizza'))                             return { emoji: '🍕', from: '#FFF5F5', to: '#FECACA' }
  if (n.includes('sushi') || n.includes('zen'))        return { emoji: '🍣', from: '#F0F9FF', to: '#BAE6FD' }
  if (n.includes('green') || n.includes('garden')
    || n.includes('saudável') || n.includes('bowl'))   return { emoji: '🥗', from: '#F0FDF4', to: '#BBF7D0' }
  if (n.includes('farmácia') || n.includes('saúde'))   return { emoji: '💊', from: '#EFF6FF', to: '#BFDBFE' }
  if (n.includes('café') || n.includes('continental')) return { emoji: '☕', from: '#FFFBEB', to: '#FDE68A' }
  if (n.includes('pastelaria') || n.includes('doce'))  return { emoji: '🎂', from: '#FDF2F8', to: '#F9A8D4' }
  if (n.includes('super') || n.includes('mercado'))    return { emoji: '🛒', from: '#F7FEE7', to: '#D9F99D' }
  return                                                { emoji: '🍲', from: '#FFF7ED', to: '#FED7AA' }
}

export function VendorCard({ vendor }: { vendor: VendorSearchResult }) {
  const { emoji, from, to } = vendorVisual(vendor.name)

  return (
    <Link to={`/vendor/${vendor.vendorId}`} className="group block">
      <div className="overflow-hidden rounded-2xl bg-white shadow-warm ring-1 ring-border/60 transition-all duration-300 group-hover:-translate-y-1.5 group-hover:shadow-warm-md group-hover:ring-border/80">
        {/* Cover */}
        <div
          className="relative flex h-36 items-center justify-center text-5xl"
          style={{ background: `linear-gradient(135deg, ${from} 0%, ${to} 100%)` }}
        >
          <span className="drop-shadow-sm transition-transform duration-300 group-hover:scale-115">
            {emoji}
          </span>

          {/* Closed overlay */}
          {!vendor.available && (
            <div className="absolute inset-0 flex items-center justify-center bg-white/80 backdrop-blur-[2px]">
              <span className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-muted-foreground shadow-sm ring-1 ring-border">
                Fechado
              </span>
            </div>
          )}

          {/* Fast delivery badge */}
          {vendor.available && vendor.estimatedDeliveryMinutes && vendor.estimatedDeliveryMinutes <= 20 && (
            <span className="absolute right-2 top-2 rounded-full bg-forest px-2 py-0.5 text-[10px] font-bold text-white shadow-sm">
              Rápido 🔥
            </span>
          )}

          {/* Open indicator */}
          {vendor.available && (
            <span className="absolute left-2 top-2 flex items-center gap-1 rounded-full bg-white/90 px-2 py-0.5 text-[10px] font-semibold text-green-700 shadow-sm backdrop-blur-sm">
              <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-green-500" />
              Aberto
            </span>
          )}
        </div>

        {/* Info */}
        <div className="p-3">
          <h3 className="text-sm font-semibold leading-tight line-clamp-1 text-foreground">
            {vendor.name}
          </h3>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
            <span className="flex items-center gap-0.5 font-semibold text-amber-600">
              <Star className="h-3 w-3 fill-amber-400 text-amber-400" />
              {vendor.rating.toFixed(1)}
            </span>
            {vendor.estimatedDeliveryMinutes && (
              <span className="flex items-center gap-0.5">
                <Clock className="h-3 w-3" />
                {vendor.estimatedDeliveryMinutes} min
              </span>
            )}
            {vendor.deliveryFee !== null && (
              <span className="flex items-center gap-0.5">
                <Bike className="h-3 w-3" />
                {vendor.deliveryFee === 0 ? (
                  <span className="font-semibold text-green-600">Grátis</span>
                ) : (
                  formatMZN(vendor.deliveryFee)
                )}
              </span>
            )}
          </div>
        </div>
      </div>
    </Link>
  )
}
