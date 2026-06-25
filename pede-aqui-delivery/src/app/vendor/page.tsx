import { useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useDispatch, useSelector } from 'react-redux'
import { ArrowLeft, Star, Clock, Bike, AlertCircle } from 'lucide-react'
import { catalogService, cartService } from '@/lib/api/services'
import { ProductCard } from '@/components/ProductCard'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
  DialogDescription, DialogFooter,
} from '@/components/ui/dialog'
import { setCartFromResponse, replaceCart } from '@/store/cart-slice'
import type { RootState, AppDispatch } from '@/store'
import type { Product } from '@/lib/api/types'
import { formatMZN } from '@/lib/utils'


/* Derive a simple header emoji/style from the vendor name */
function vendorCover(name: string): { emoji: string; from: string; to: string } {
  const n = name.toLowerCase()
  if (n.includes('burger'))   return { emoji: '🍔', from: '#FFF7E6', to: '#FDE68A' }
  if (n.includes('pizza'))    return { emoji: '🍕', from: '#FFF5F5', to: '#FECACA' }
  if (n.includes('sushi'))    return { emoji: '🍣', from: '#F0F9FF', to: '#BAE6FD' }
  if (n.includes('green'))    return { emoji: '🥗', from: '#F0FDF4', to: '#BBF7D0' }
  if (n.includes('farmácia')) return { emoji: '💊', from: '#EFF6FF', to: '#BFDBFE' }
  if (n.includes('café'))     return { emoji: '☕', from: '#FFFBEB', to: '#FDE68A' }
  if (n.includes('pastel'))   return { emoji: '🎂', from: '#FDF2F8', to: '#F9A8D4' }
  return                       { emoji: '🍲', from: '#FFF7ED', to: '#FED7AA' }
}

export default function VendorPage() {
  const { vendorId } = useParams<{ vendorId: string }>()
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const auth = useSelector((s: RootState) => s.auth)
  const cart = useSelector((s: RootState) => s.cart)
  const [pendingProduct, setPendingProduct] = useState<Product | null>(null)
  const [activeCategory, setActiveCategory] = useState<string | null>(null)

  const { data: products = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['products', vendorId],
    queryFn: () => catalogService.getProducts(vendorId!),
    enabled: !!vendorId,
    retry: 1,
  })

  const grouped = useMemo(() => {
    const map = new Map<string, Product[]>()
    for (const p of products) {
      const key = p.categoryId ?? 'Outros'
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(p)
    }
    return map
  }, [products])

  const categories = [...grouped.keys()]

  async function doAdd(product: Product, force = false) {
    if (auth.status !== 'authenticated') {
      navigate(`/login?redirect=/vendor/${vendorId}`)
      return
    }

    if (!force && cart.vendorId && cart.vendorId !== vendorId && cart.items.length > 0) {
      setPendingProduct(product)
      return
    }

    const sku = product.skus.find((s) => s.active) ?? product.skus[0]
    if (!sku) return

    const response = await cartService.addItem(auth.sub!, {
      vendorId: vendorId!,
      skuId: sku.id,
      quantity: 1,
    })
    const action = force ? replaceCart : setCartFromResponse
    dispatch(action({
      cartId: response.id,
      vendorId: vendorId!,
      vendorName: '',
      items: response.items.map((i) => ({
        skuId: i.skuId,
        productId: product.id,
        productName: i.productName,
        skuName: i.skuName,
        unitPrice: i.unitPrice,
        quantity: i.quantity,
      })),
    }))
  }

  /* Derive a display name from vendorId when we don't have the full vendor object */
  const vendorName = vendorId?.replace('demo-', '').replace(/-/g, ' ') ?? 'Restaurante'
  const cover = vendorCover(vendorName)

  return (
    <>
      {/* Vendor header */}
      <div
        className="px-4 pb-6 pt-5"
        style={{ background: `linear-gradient(135deg, ${cover.from} 0%, ${cover.to} 100%)` }}
      >
        <div className="mx-auto max-w-5xl">
          <button
            onClick={() => navigate(-1)}
            className="mb-4 flex items-center gap-1 text-sm font-medium text-foreground/60 hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </button>

          <div className="flex items-center gap-4">
            <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-white shadow-warm text-4xl">
              {cover.emoji}
            </div>
            <div>
              <h1 className="font-display text-2xl font-bold capitalize text-foreground">
                {vendorName}
              </h1>
              <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
                <span className="flex items-center gap-1 font-semibold text-amber-600">
                  <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                  4.7
                </span>
                <span className="flex items-center gap-1">
                  <Clock className="h-3.5 w-3.5" />
                  30–40 min
                </span>
                <span className="flex items-center gap-1">
                  <Bike className="h-3.5 w-3.5" />
                  {formatMZN(120)} entrega
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Sticky category tabs */}
      {categories.length > 1 && (
        <div className="sticky top-14 z-30 border-b border-border bg-white/95 backdrop-blur">
          <div className="mx-auto max-w-5xl">
            <div className="flex gap-1 overflow-x-auto scrollbar-hide px-4 py-2">
              {categories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => {
                    setActiveCategory(cat)
                    document.getElementById(`cat-${cat}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                  }}
                  className={`shrink-0 rounded-full px-3 py-1 text-sm font-semibold transition-colors ${
                    activeCategory === cat
                      ? 'bg-forest text-white'
                      : 'bg-secondary text-foreground hover:bg-secondary/70'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Products */}
      <div className="mx-auto max-w-5xl px-4 py-6">
        {isLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-28 animate-pulse rounded-2xl bg-muted" />
            ))}
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center gap-4 py-20 text-center">
            <AlertCircle className="h-10 w-10 text-muted-foreground" />
            <p className="text-muted-foreground">Não foi possível carregar o cardápio.</p>
            <button
              onClick={() => refetch()}
              className="rounded-full bg-ember px-5 py-2 text-sm font-semibold text-white hover:bg-ember/90"
            >
              Tentar novamente
            </button>
          </div>
        ) : products.length === 0 ? (
          <div className="py-20 text-center">
            <p className="text-4xl">🍽️</p>
            <p className="mt-3 text-muted-foreground">Cardápio não disponível</p>
          </div>
        ) : (
          <div className="space-y-8">
            {[...grouped.entries()].map(([category, items]) => (
              <section key={category} id={`cat-${category}`} className="scroll-mt-28">
                <h2 className="font-display mb-3 text-xl font-bold text-foreground">{category}</h2>
                <div className="space-y-3">
                  {items.map((p) => (
                    <ProductCard
                      key={p.id}
                      product={p}
                      vendorId={vendorId!}
                      vendorName={vendorName}
                      onAdd={doAdd}
                    />
                  ))}
                </div>
              </section>
            ))}
          </div>
        )}
      </div>

      {/* Replace cart dialog */}
      <Dialog open={!!pendingProduct} onOpenChange={() => setPendingProduct(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Substituir carrinho?</DialogTitle>
            <DialogDescription>
              Já tens itens de outro restaurante. Queres limpá-lo e começar aqui?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingProduct(null)}>Cancelar</Button>
            <Button
              onClick={() => {
                const p = pendingProduct
                setPendingProduct(null)
                if (p) doAdd(p, true)
              }}
            >
              Substituir
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
