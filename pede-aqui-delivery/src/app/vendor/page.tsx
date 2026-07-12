import { useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useDispatch, useSelector } from 'react-redux'
import {
  ArrowLeft, Heart, Star, Clock, MapPin, Search,
  Plus, Minus, X, AlertCircle,
} from 'lucide-react'
import { catalogService, cartService, vendorService } from '@/lib/api/services'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
  DialogDescription, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { setCartFromResponse, replaceCart } from '@/store/cart-slice'
import type { RootState, AppDispatch } from '@/store'
import type { Product } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'
import { vendorCover, productEmoji } from '@/lib/covers'

export default function VendorPage() {
  const { vendorId } = useParams<{ vendorId: string }>()
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const auth = useSelector((s: RootState) => s.auth)
  const cart = useSelector((s: RootState) => s.cart)

  const [activeCategory, setActiveCategory] = useState<string | null>(null)
  const [menuSearch, setMenuSearch] = useState('')
  const [liked, setLiked] = useState(false)
  const [selected, setSelected] = useState<Product | null>(null)
  const [detailQty, setDetailQty] = useState(1)
  const [notes, setNotes] = useState('')
  const [pendingProduct, setPendingProduct] = useState<{ product: Product; qty: number; notes: string } | null>(null)
  const [cartError, setCartError] = useState<string | null>(null)

  const { data: products = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['products', vendorId],
    queryFn: () => catalogService.getProducts(vendorId!),
    enabled: !!vendorId,
    retry: 1,
  })

  const { data: vendor } = useQuery({
    queryKey: ['vendor', vendorId],
    queryFn: () => vendorService.get(vendorId!),
    enabled: !!vendorId,
    retry: 1,
  })

  const { data: allCategories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => catalogService.getCategories(),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  })
  const categoryName = (id?: string | null) => allCategories.find((c) => c.id === id)?.name ?? 'Outros'

  const categories = useMemo(() => {
    const seen: string[] = []
    for (const p of products) {
      const key = p.categoryId ?? 'Outros'
      if (!seen.includes(key)) seen.push(key)
    }
    return seen
  }, [products])

  const currentCategory = activeCategory ?? categories[0] ?? null

  const filtered = useMemo(() => {
    const q = menuSearch.trim().toLowerCase()
    return products.filter((p) => {
      const inCat = (p.categoryId ?? 'Outros') === currentCategory
      const matches = !q || p.name.toLowerCase().includes(q) || (p.description ?? '').toLowerCase().includes(q)
      return inCat && matches
    })
  }, [products, currentCategory, menuSearch])

  const qtyInCart = (skuId: string) => cart.items.find((i) => i.skuId === skuId)?.quantity ?? 0

  async function addToServer(product: Product, qty: number, note: string, force = false) {
    if (auth.status !== 'authenticated') {
      navigate(`/login?redirect=/vendor/${vendorId}`)
      return
    }
    if (!force && cart.vendorId && cart.vendorId !== vendorId && cart.items.length > 0) {
      setPendingProduct({ product, qty, notes: note })
      return
    }
    const sku = product.skus.find((s) => s.active) ?? product.skus[0]
    if (!sku) return

    try {
      const response = await cartService.addItem(auth.sub!, { vendorId: vendorId!, skuId: sku.id, quantity: qty })
      const action = force ? replaceCart : setCartFromResponse
      // Attach the client-side note to this sku (best-effort; not persisted server-side).
      const items = response.items.map((i) => ({
        skuId: i.skuId,
        productId: product.id,
        productName: i.productName,
        skuName: i.skuName,
        unitPrice: i.unitPrice,
        quantity: i.quantity,
        notes: i.skuId === sku.id ? (note || undefined) : cart.items.find((c) => c.skuId === i.skuId)?.notes,
      }))
      dispatch(action({ cartId: response.id, vendorId: vendorId!, vendorName, items }))
      setCartError(null)
    } catch {
      setCartError('Não foi possível adicionar ao carrinho. Tente novamente.')
    }
  }

  function decrement(skuId: string) {
    // Backend has no decrement endpoint — optimistic local update (mirrors CartDrawer).
    const current = cart.items.find((i) => i.skuId === skuId)
    if (!current || !cart.cartId || !cart.vendorId) return
    const nextItems =
      current.quantity <= 1
        ? cart.items.filter((i) => i.skuId !== skuId)
        : cart.items.map((i) => (i.skuId === skuId ? { ...i, quantity: i.quantity - 1 } : i))
    dispatch(setCartFromResponse({ cartId: cart.cartId, vendorId: cart.vendorId, vendorName: cart.vendorName ?? vendorName, items: nextItems }))
  }

  function openItem(product: Product) {
    setSelected(product)
    setDetailQty(1)
    setNotes('')
  }
  async function confirmAdd() {
    if (!selected) return
    const p = selected
    const q = detailQty
    const n = notes
    setSelected(null)
    await addToServer(p, q, n)
  }

  const vendorName = vendor?.name ?? 'Estabelecimento'
  const cover = vendorCover(vendorName)

  return (
    <div className="pb-10">
      {/* ── Cover ── */}
      <section
        className="relative h-64 sm:h-80 w-full overflow-hidden flex items-center justify-center text-[9rem] select-none"
        style={{ background: `linear-gradient(135deg, ${cover.from} 0%, ${cover.to} 100%)` }}
      >
        <span className="opacity-70 drop-shadow-sm">{cover.emoji}</span>
        <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 via-slate-900/20 to-transparent" />

        <div className="absolute top-6 left-6 right-6 flex items-center justify-between z-10">
          <button onClick={() => navigate(-1)} className="p-3 bg-white/90 backdrop-blur-md hover:bg-white rounded-full text-slate-800 shadow-md transition-all hover:scale-105">
            <ArrowLeft className="w-5 h-5" />
          </button>
          <button onClick={() => setLiked(!liked)} className="p-3 bg-white/90 backdrop-blur-md hover:bg-white rounded-full text-brand-600 shadow-md transition-all hover:scale-105">
            <Heart className={`w-5 h-5 ${liked ? 'fill-brand-600 stroke-brand-600' : ''}`} />
          </button>
        </div>

        <div className="absolute bottom-0 left-0 right-0 p-6 sm:px-8 text-white text-left z-10">
          <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-start md:items-end justify-between gap-4">
            <div className="flex gap-4 items-center">
              <div className="w-20 h-20 bg-white rounded-2xl shadow-xl border border-slate-100 flex-shrink-0 flex items-center justify-center text-4xl overflow-hidden">
                {vendor?.logoUrl ? <img src={vendor.logoUrl} alt={vendorName} className="w-full h-full object-cover" /> : cover.emoji}
              </div>
              <div className="space-y-1">
                <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase text-white shadow-md ${vendor?.available ? 'bg-emerald-600 shadow-emerald-500/20' : 'bg-slate-600 shadow-slate-500/20'}`}>
                  {vendor ? (vendor.available ? 'Aberto' : 'Fechado') : 'Estabelecimento'}
                </span>
                <h1 className="font-display font-extrabold text-2xl sm:text-3xl tracking-tight leading-none capitalize">{vendorName}</h1>
                {vendor?.description && <p className="text-xs text-white/70 font-medium max-w-md line-clamp-1">{vendor.description}</p>}
              </div>
            </div>
            <div className="flex flex-wrap gap-3 text-xs font-bold text-white">
              <div className="px-3 py-1.5 bg-white/10 backdrop-blur-md border border-white/10 rounded-xl flex items-center gap-1.5">
                <Star className="w-4 h-4 text-amber-400 fill-amber-400" /> {vendor && vendor.rating > 0 ? vendor.rating.toFixed(1) : 'Novo'}
              </div>
              {vendor?.estimatedDeliveryMinutes ? (
                <div className="px-3 py-1.5 bg-white/10 backdrop-blur-md border border-white/10 rounded-xl flex items-center gap-1.5">
                  <Clock className="w-4 h-4 text-brand-300" /> {vendor.estimatedDeliveryMinutes} min
                </div>
              ) : null}
              {vendor?.address && (
                <div className="px-3 py-1.5 bg-white/10 backdrop-blur-md border border-white/10 rounded-xl flex items-center gap-1.5">
                  <MapPin className="w-4 h-4 text-brand-300" /> {vendor.address}
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* ── Menu nav ── */}
      {categories.length > 0 && (
        <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 -mt-2 relative z-20">
          <div className="bg-white rounded-2xl border border-slate-100 p-3 sm:p-4 shadow-sm flex flex-col md:flex-row gap-4 items-center justify-between">
            <div className="flex overflow-x-auto gap-1.5 w-full md:w-auto pb-1 md:pb-0 scrollbar-none">
              {categories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setActiveCategory(cat)}
                  className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex-shrink-0 ${
                    currentCategory === cat ? 'bg-brand-600 text-white shadow-md shadow-brand-500/10' : 'bg-slate-50 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  {categoryName(cat)}
                </button>
              ))}
            </div>
            <div className="relative w-full md:w-64">
              <input
                type="text"
                placeholder="Procurar no menu..."
                value={menuSearch}
                onChange={(e) => setMenuSearch(e.target.value)}
                className="w-full bg-slate-50 border border-slate-200 rounded-xl py-2 pl-9 pr-4 text-xs focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition-all text-slate-700"
              />
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-3" />
            </div>
          </div>
        </section>
      )}

      {/* ── Cart error ── */}
      {cartError && (
        <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 mt-4">
          <div className="flex items-center justify-between gap-3 rounded-2xl border border-red-100 bg-red-50 px-4 py-3 text-xs font-semibold text-red-600">
            <span className="flex items-center gap-2"><AlertCircle className="h-4 w-4 flex-shrink-0" /> {cartError}</span>
            <button onClick={() => setCartError(null)} className="p-1 rounded-full hover:bg-red-100 transition-colors" aria-label="Fechar">
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        </section>
      )}

      {/* ── Menu grid ── */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 mt-6">
        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-36 animate-pulse rounded-2xl bg-slate-100" />)}
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center gap-4 py-20 text-center">
            <AlertCircle className="h-10 w-10 text-slate-300" />
            <p className="text-slate-500">Não foi possível carregar o menu.</p>
            <button onClick={() => refetch()} className="rounded-full bg-brand-600 px-5 py-2 text-sm font-bold text-white hover:bg-brand-500">Tentar novamente</button>
          </div>
        ) : filtered.length === 0 ? (
          <div className="bg-white rounded-3xl p-12 text-center border border-slate-100 shadow-sm max-w-sm mx-auto space-y-2">
            <div className="w-16 h-16 bg-slate-50 rounded-full flex items-center justify-center mx-auto text-3xl">🍽️</div>
            <h3 className="font-display font-bold text-slate-800 text-lg">Sem itens nesta secção</h3>
            <p className="text-slate-400 text-xs font-medium">Tente outra categoria ou limpe a busca.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {filtered.map((item) => {
              const sku = item.skus.find((s) => s.active) ?? item.skus[0]
              const qty = sku ? qtyInCart(sku.id) : 0
              return (
                <div
                  key={item.id}
                  onClick={() => openItem(item)}
                  className="bg-white rounded-2xl border border-slate-100 p-4 sm:p-5 shadow-sm hover:shadow-md transition-all flex justify-between gap-4 text-left cursor-pointer hover:border-brand-100 group"
                >
                  <div className="space-y-2 flex-1 min-w-0">
                    <h3 className="font-display font-extrabold text-slate-800 text-base leading-tight group-hover:text-brand-600 transition-colors">{item.name}</h3>
                    {item.description && <p className="text-xs text-slate-400 font-medium line-clamp-2 leading-relaxed">{item.description}</p>}
                    <div className="pt-2">
                      <span className="font-display font-extrabold text-base text-slate-800">{sku ? formatMZN(sku.price) : '—'}</span>
                    </div>
                  </div>
                  <div className="relative w-24 h-24 sm:w-28 sm:h-28 rounded-xl bg-gradient-to-br from-slate-100 to-slate-50 flex items-center justify-center text-4xl flex-shrink-0 overflow-hidden">
                    {item.primaryImageUrl
                      ? <img src={item.primaryImageUrl} alt={item.name} className="absolute inset-0 w-full h-full object-cover" loading="lazy" />
                      : productEmoji(categoryName(item.categoryId))}
                    <div className="absolute bottom-2 right-2 z-10" onClick={(e) => e.stopPropagation()}>
                      {qty > 0 && sku ? (
                        <div className="bg-brand-600 text-white rounded-full flex items-center shadow-lg border border-brand-500 h-8 px-2 gap-2 text-xs font-bold">
                          <button onClick={() => decrement(sku.id)} className="p-1 hover:bg-brand-500 rounded-full transition-all" aria-label="Remover"><Minus className="w-3.5 h-3.5" /></button>
                          <span>{qty}</span>
                          <button onClick={() => addToServer(item, 1, '')} className="p-1 hover:bg-brand-500 rounded-full transition-all" aria-label="Adicionar"><Plus className="w-3.5 h-3.5" /></button>
                        </div>
                      ) : (
                        <button onClick={() => openItem(item)} className="w-8 h-8 rounded-full bg-white hover:bg-brand-50 hover:text-brand-600 text-slate-800 flex items-center justify-center shadow-md border border-slate-100 transition-all active:scale-90" aria-label="Adicionar">
                          <Plus className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </section>

      {/* ── Item detail modal ── */}
      {selected && (() => {
        const sku = selected.skus.find((s) => s.active) ?? selected.skus[0]
        const unit = sku?.price ?? 0
        return (
          <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-slate-900/60 backdrop-blur-sm animate-fade-in" onClick={() => setSelected(null)} />
            <div className="relative w-full max-w-lg bg-white rounded-3xl shadow-xl overflow-hidden z-10 flex flex-col max-h-[90vh] animate-scale-in">
              <button onClick={() => setSelected(null)} className="absolute top-4 right-4 p-2 bg-slate-900/40 hover:bg-slate-900/60 text-white rounded-full z-20 transition-all">
                <X className="w-4 h-4" />
              </button>
              <div className="h-48 sm:h-56 bg-gradient-to-br from-slate-100 to-slate-50 relative flex-shrink-0 flex items-center justify-center text-7xl overflow-hidden">
                {selected.primaryImageUrl
                  ? <img src={selected.primaryImageUrl} alt={selected.name} className="absolute inset-0 w-full h-full object-cover" />
                  : productEmoji(categoryName(selected.categoryId))}
                <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
                <h2 className="absolute bottom-4 left-6 font-display font-extrabold text-xl sm:text-2xl tracking-tight leading-none text-white">{selected.name}</h2>
              </div>
              <div className="p-6 text-left space-y-5 overflow-y-auto flex-1">
                {selected.description && (
                  <div className="space-y-1">
                    <p className="text-xs font-bold text-slate-400 uppercase tracking-wider">Descrição</p>
                    <p className="text-slate-500 text-xs sm:text-sm leading-relaxed font-medium">{selected.description}</p>
                  </div>
                )}
                <div className="space-y-2">
                  <label htmlFor="item_notes" className="text-xs font-bold text-slate-700 uppercase tracking-wider block">
                    Observações <span className="normal-case font-medium text-slate-400">(guardadas apenas no dispositivo)</span>
                  </label>
                  <textarea
                    id="item_notes"
                    rows={3}
                    placeholder="Ex: sem cebola, ponto da carne bem passado..."
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    className="w-full border border-slate-200 rounded-xl p-3 text-xs focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 text-slate-700"
                  />
                </div>
              </div>
              <div className="p-6 bg-slate-50 border-t border-slate-100 flex items-center justify-between gap-4 flex-shrink-0">
                <div className="bg-white border border-slate-200 rounded-full flex items-center h-11 px-3 gap-3 text-sm font-extrabold text-slate-800 shadow-sm">
                  <button onClick={() => detailQty > 1 && setDetailQty(detailQty - 1)} disabled={detailQty <= 1} className={`p-1 rounded-full transition-all ${detailQty <= 1 ? 'opacity-35 cursor-not-allowed' : 'hover:bg-slate-100'}`}><Minus className="w-4 h-4" /></button>
                  <span className="w-5 text-center select-none">{detailQty}</span>
                  <button onClick={() => setDetailQty(detailQty + 1)} className="p-1 hover:bg-slate-100 rounded-full transition-all"><Plus className="w-4 h-4" /></button>
                </div>
                <button onClick={confirmAdd} className="flex-1 bg-brand-600 hover:bg-brand-500 text-white font-bold h-11 rounded-full flex items-center justify-center gap-2 shadow-lg shadow-brand-500/10 transition-all active:scale-[0.98]">
                  Adicionar • {formatMZN(unit * detailQty)}
                </button>
              </div>
            </div>
          </div>
        )
      })()}

      {/* ── Replace-cart dialog ── */}
      <Dialog open={!!pendingProduct} onOpenChange={() => setPendingProduct(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Substituir carrinho?</DialogTitle>
            <DialogDescription>Já tens itens de outra loja. Queres limpá-lo e começar aqui?</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingProduct(null)}>Cancelar</Button>
            <Button onClick={() => {
              const pp = pendingProduct
              setPendingProduct(null)
              if (pp) addToServer(pp.product, pp.qty, pp.notes, true)
            }}>Substituir</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
