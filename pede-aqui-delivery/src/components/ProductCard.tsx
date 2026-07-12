import { useState } from 'react'
import { Plus, Check, Loader2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { Product } from '@/lib/api/types'
import { cn, formatMZN } from '@/lib/utils'
import { productEmoji } from '@/lib/covers'

type BtnState = 'idle' | 'adding' | 'added'

interface Props {
  product: Product
  vendorId: string
  vendorName: string
  onAdd: (product: Product) => Promise<void> | void
}

export function ProductCard({ product, onAdd }: Props) {
  const [btnState, setBtnState] = useState<BtnState>('idle')
  const activeSku = product.skus.find((s) => s.active) ?? product.skus[0]

  async function handleAdd() {
    if (btnState !== 'idle' || !activeSku) return
    setBtnState('adding')
    try {
      await onAdd(product)
      setBtnState('added')
      setTimeout(() => setBtnState('idle'), 1600)
    } catch {
      setBtnState('idle')
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-100 p-4 shadow-sm hover:shadow-md hover:border-brand-100 transition-all flex justify-between gap-4 text-left group">
      <div className="space-y-1.5 flex-1 min-w-0">
        <div className="flex items-start gap-2">
          <h4 className="flex-1 font-display font-extrabold text-slate-800 text-sm leading-snug group-hover:text-brand-600 transition-colors">{product.name}</h4>
          {product.requiresPrescriptionMetadata && <Badge variant="secondary" className="shrink-0 text-[10px]">Receita</Badge>}
        </div>
        {product.description && <p className="text-xs text-slate-400 font-medium line-clamp-2 leading-relaxed">{product.description}</p>}
        <p className="pt-1 font-display font-extrabold text-sm text-slate-800">{activeSku ? formatMZN(activeSku.price) : '—'}</p>
      </div>

      <div className="relative w-24 h-24 rounded-xl bg-gradient-to-br from-slate-100 to-slate-50 flex items-center justify-center text-4xl flex-shrink-0 overflow-hidden">
        {product.primaryImageUrl
          ? <img src={product.primaryImageUrl} alt={product.name} className="absolute inset-0 w-full h-full object-cover" loading="lazy" />
          : productEmoji(product.categoryId)}
        <button
          onClick={handleAdd}
          disabled={!activeSku || btnState !== 'idle'}
          aria-label="Adicionar ao carrinho"
          className={cn(
            'absolute bottom-2 right-2 h-8 min-w-8 px-2 rounded-full flex items-center justify-center gap-1 text-xs font-bold shadow-md border transition-all active:scale-90 disabled:cursor-not-allowed',
            btnState === 'added'
              ? 'bg-emerald-500 border-emerald-400 text-white'
              : btnState === 'adding'
              ? 'bg-white border-slate-100 text-slate-400 cursor-wait'
              : 'bg-white border-slate-100 text-slate-800 hover:bg-brand-50 hover:text-brand-600',
          )}
        >
          {btnState === 'adding' ? <Loader2 className="w-4 h-4 animate-spin" />
            : btnState === 'added' ? <Check className="w-4 h-4 animate-check" />
            : <Plus className="w-4 h-4" />}
        </button>
      </div>
    </div>
  )
}
