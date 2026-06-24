import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useQueries } from '@tanstack/react-query'
import { useDispatch, useSelector } from 'react-redux'
import { ArrowLeft, Star, Clock, Bike } from 'lucide-react'
import { vendorService, catalogService, cartService } from '@/lib/api/services'
import { ProductCard } from '@/components/ProductCard'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { setCartFromResponse, replaceCart } from '@/store/cart-slice'
import { VERTICALS, MOCK_VENDORS, MOCK_PRODUCTS, mockVendorVertical } from '@/lib/mockData'
import { formatMZN } from '@/lib/utils'
import type { RootState, AppDispatch } from '@/store'
import type { Product, VendorSearchResult } from '@/lib/api/types'

type PendingAdd = { product: Product; vendor: VendorSearchResult }

export default function CatalogoPage() {
  const { verticalId = '' } = useParams<{ verticalId: string }>()
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const auth = useSelector((s: RootState) => s.auth)
  const cart = useSelector((s: RootState) => s.cart)
  const [pendingAdd, setPendingAdd] = useState<PendingAdd | null>(null)

  const vertical = VERTICALS.find((v) => v.id === verticalId)

  /* Fetch vendors in this vertical */
  const { data: searchData, isError: vendorsError } = useQuery({
    queryKey: ['vendors', verticalId],
    queryFn: () => vendorService.search({ category: verticalId }),
    retry: 1,
    enabled: !!verticalId,
  })

  const vendorList: VendorSearchResult[] = vendorsError
    ? MOCK_VENDORS.filter((v) => mockVendorVertical(v.name) === verticalId)
    : (searchData?.vendors ?? [])

  /* Fetch products for each vendor in parallel (cap at 8 vendors) */
  const cappedVendors = vendorList.slice(0, 8)

  const productQueries = useQueries({
    queries: cappedVendors.map((vendor) => ({
      queryKey: ['products', vendor.vendorId],
      queryFn: () => catalogService.getProducts(vendor.vendorId),
      retry: 1,
    })),
  })

  async function doAdd(product: Product, vendor: VendorSearchResult, force = false) {
    if (auth.status !== 'authenticated') {
      navigate(`/login?redirect=/catalogo/${verticalId}`)
      return
    }

    if (!force && cart.vendorId && cart.vendorId !== vendor.vendorId && cart.items.length > 0) {
      setPendingAdd({ product, vendor })
      return
    }

    const sku = product.skus.find((s) => s.active) ?? product.skus[0]
    if (!sku) return

    const response = await cartService.addItem(auth.sub!, {
      vendorId: vendor.vendorId,
      skuId: sku.id,
      quantity: 1,
    })
    const action = force ? replaceCart : setCartFromResponse
    dispatch(action({
      cartId: response.id,
      vendorId: vendor.vendorId,
      vendorName: vendor.name,
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

  if (!vertical) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-20 text-center">
        <p className="text-4xl">🔍</p>
        <p className="mt-3 text-muted-foreground">Categoria não encontrada.</p>
        <Link to="/" className="mt-4 inline-block text-sm font-semibold underline">Voltar ao início</Link>
      </div>
    )
  }

  const isLoading = vendorList.length > 0 && productQueries.some((q) => q.isLoading)

  return (
    <>
      {/* Header */}
      <div className="bg-forest px-6 pb-6 pt-5">
        <div className="mx-auto max-w-5xl">
          <button
            onClick={() => navigate(-1)}
            className="mb-4 flex items-center gap-1 text-sm font-medium text-white/60 hover:text-white"
          >
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </button>
          <div className="flex items-center gap-3">
            <span className="text-5xl">{vertical.emoji}</span>
            <div>
              <h1 className="font-display text-2xl font-black text-white">{vertical.label}</h1>
              <p className="text-sm text-white/60">
                {vendorList.length > 0
                  ? `${vendorList.length} ${vendorList.length === 1 ? 'estabelecimento' : 'estabelecimentos'}`
                  : 'A carregar…'}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="mx-auto max-w-5xl px-4 py-6">
        {/* Initial loading — no vendors yet */}
        {!vendorsError && vendorList.length === 0 && (
          <div className="space-y-8">
            {Array.from({ length: 2 }).map((_, i) => (
              <div key={i}>
                <div className="mb-3 h-6 w-48 animate-pulse rounded-lg bg-muted" />
                <div className="space-y-3">
                  {Array.from({ length: 3 }).map((_, j) => (
                    <div key={j} className="h-28 animate-pulse rounded-2xl bg-muted" />
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* No vendors in this vertical */}
        {(vendorsError || vendorList.length === 0) && !isLoading && (
          <div className="py-20 text-center">
            <p className="text-4xl">{vertical.emoji}</p>
            <p className="mt-3 text-muted-foreground">Nenhum estabelecimento disponível em {vertical.label}.</p>
            <Link to="/" className="mt-4 inline-block text-sm font-semibold underline">Ver todos</Link>
          </div>
        )}

        {/* Vendors + their products */}
        <div className="space-y-10">
          {cappedVendors.map((vendor, idx) => {
            const query = productQueries[idx]
            const products: Product[] = query?.isError || (query?.data?.length === 0)
              ? MOCK_PRODUCTS.map((p) => ({ ...p, vendorId: vendor.vendorId }))
              : (query?.data ?? [])

            return (
              <section key={vendor.vendorId}>
                {/* Vendor header */}
                <Link to={`/vendor/${vendor.vendorId}`} className="group mb-4 flex items-center gap-3">
                  <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-secondary text-2xl">
                    🏪
                  </div>
                  <div className="flex-1 min-w-0">
                    <h2 className="font-display text-lg font-bold text-foreground group-hover:underline line-clamp-1">
                      {vendor.name}
                    </h2>
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
                      <span className="flex items-center gap-1 font-semibold text-amber-600">
                        <Star className="h-3 w-3 fill-amber-400 text-amber-400" />
                        {vendor.rating.toFixed(1)}
                      </span>
                      {vendor.estimatedDeliveryMinutes && (
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {vendor.estimatedDeliveryMinutes} min
                        </span>
                      )}
                      {vendor.deliveryFee !== null && (
                        <span className="flex items-center gap-1">
                          <Bike className="h-3 w-3" />
                          {vendor.deliveryFee === 0
                            ? <span className="font-semibold text-green-600">Grátis</span>
                            : formatMZN(vendor.deliveryFee)}
                        </span>
                      )}
                    </div>
                  </div>
                  <span className="shrink-0 text-xs font-semibold text-muted-foreground group-hover:text-foreground">
                    Ver tudo →
                  </span>
                </Link>

                {/* Products */}
                {query?.isLoading ? (
                  <div className="space-y-3">
                    {Array.from({ length: 3 }).map((_, i) => (
                      <div key={i} className="h-28 animate-pulse rounded-2xl bg-muted" />
                    ))}
                  </div>
                ) : (
                  <div className="space-y-3">
                    {products.slice(0, 5).map((product) => (
                      <ProductCard
                        key={product.id}
                        product={product}
                        vendorId={vendor.vendorId}
                        vendorName={vendor.name}
                        onAdd={(p) => doAdd(p, vendor)}
                      />
                    ))}
                    {products.length > 5 && (
                      <Link
                        to={`/vendor/${vendor.vendorId}`}
                        className="block rounded-xl bg-secondary py-2.5 text-center text-sm font-semibold text-foreground hover:bg-secondary/70"
                      >
                        Ver mais {products.length - 5} produto{products.length - 5 !== 1 ? 's' : ''} →
                      </Link>
                    )}
                  </div>
                )}
              </section>
            )
          })}
        </div>
      </div>

      {/* Replace cart dialog */}
      <Dialog open={!!pendingAdd} onOpenChange={() => setPendingAdd(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Substituir carrinho?</DialogTitle>
            <DialogDescription>
              Já tens itens de outro restaurante. Queres limpá-lo e começar aqui?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingAdd(null)}>Cancelar</Button>
            <Button
              onClick={() => {
                const pa = pendingAdd
                setPendingAdd(null)
                if (pa) doAdd(pa.product, pa.vendor, true)
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
