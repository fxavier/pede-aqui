import { useState } from 'react'
import { ShoppingCart, Check, Loader2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { Product } from '@/lib/api/types'
import { cn, formatMZN } from '@/lib/utils'

type BtnState = 'idle' | 'adding' | 'added'

interface Props {
  product: Product
  vendorId: string
  vendorName: string
  onAdd: (product: Product) => Promise<void> | void
}

const PRODUCT_EMOJI: Record<string, string> = {
  Entradas: '🥟',
  'Pratos Principais': '🍛',
  Bebidas: '🥤',
  Sobremesas: '🍮',
  Outros: '📦',
}

export function ProductCard({ product, onAdd }: Props) {
  const [btnState, setBtnState] = useState<BtnState>('idle')
  const activeSku = product.skus.find((s) => s.active) ?? product.skus[0]
  const emoji = PRODUCT_EMOJI[product.categoryId ?? ''] ?? '📦'

  async function handleAdd() {
    if (btnState !== 'idle' || !activeSku) return
    setBtnState('adding')
    try {
      await onAdd(product)
      setBtnState('added')
      setTimeout(() => setBtnState('idle'), 1800)
    } catch {
      setBtnState('idle')
    }
  }

  return (
    <div
      className={cn(
        'group flex gap-3 rounded-2xl bg-white p-4 ring-1 transition-all duration-300',
        btnState === 'added'
          ? 'ring-green-300 shadow-[0_0_0_3px_rgba(34,197,94,0.15),0_8px_24px_-4px_rgba(60,30,5,0.12)]'
          : 'ring-border/50 shadow-warm hover:shadow-warm-md',
      )}
    >
      {/* Thumbnail */}
      <div className="flex h-[76px] w-[76px] shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-secondary/70 to-secondary text-[2.25rem] transition-transform duration-200 group-hover:scale-105">
        {emoji}
      </div>

      {/* Content */}
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-start gap-2">
          <h4 className="flex-1 text-sm font-semibold leading-snug text-foreground">
            {product.name}
          </h4>
          {product.requiresPrescriptionMetadata && (
            <Badge variant="secondary" className="shrink-0 text-[10px]">Receita</Badge>
          )}
        </div>

        {product.description && (
          <p className="mt-0.5 line-clamp-1 text-xs leading-relaxed text-muted-foreground">
            {product.description}
          </p>
        )}

        <p className="mt-1.5 text-sm font-bold text-primary">
          {activeSku ? formatMZN(activeSku.price) : '—'}
        </p>

        {/* Comprar button */}
        <button
          onClick={handleAdd}
          disabled={!activeSku || btnState !== 'idle'}
          className={cn(
            'mt-2 flex w-full items-center justify-center gap-2 overflow-hidden rounded-xl py-2.5 text-sm font-bold text-white',
            'transition-all duration-300 active:scale-[0.97]',
            'disabled:cursor-not-allowed',
            btnState === 'added'
              ? 'bg-green-500 shadow-[0_4px_18px_rgba(34,197,94,0.55)]'
              : btnState === 'adding'
              ? 'cursor-wait bg-forest/70 shadow-none'
              : 'bg-forest shadow-[0_4px_14px_rgba(26,47,29,0.3)] hover:-translate-y-px hover:bg-forest-light hover:shadow-[0_6px_22px_rgba(26,47,29,0.45)]',
          )}
        >
          {btnState === 'adding' ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : btnState === 'added' ? (
            <>
              <Check className="h-4 w-4 animate-check" />
              <span>Adicionado!</span>
            </>
          ) : (
            <>
              <ShoppingCart className="h-4 w-4" />
              <span>Comprar</span>
            </>
          )}
        </button>
      </div>
    </div>
  )
}
