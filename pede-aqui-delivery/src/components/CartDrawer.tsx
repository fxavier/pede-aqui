import { useState } from 'react'
import { useSelector, useDispatch } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import { AlertCircle, Minus, Plus, Trash2, ShoppingBag, ChevronRight } from 'lucide-react'
import { Sheet, SheetContent } from '@/components/ui/sheet'
import { clearCart, cartTotal, cartItemCount, setCartFromResponse } from '@/store/cart-slice'
import type { RootState, AppDispatch } from '@/store'
import { formatMZN } from '@/lib/utils'
import { cartService } from '@/lib/api/services'

interface Props {
  open: boolean
  onClose: () => void
}

export function CartDrawer({ open, onClose }: Props) {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const auth = useSelector((s: RootState) => s.auth)
  const cart = useSelector((s: RootState) => s.cart)
  const subtotal = cartTotal(cart.items)
  const count = cartItemCount(cart.items)
  const [error, setError] = useState<string | null>(null)

  async function increment(skuId: string, productId: string) {
    if (!auth.sub || !cart.vendorId) return
    try {
      const response = await cartService.addItem(auth.sub, { vendorId: cart.vendorId, skuId, quantity: 1 })
      dispatch(setCartFromResponse({
        cartId: response.id,
        vendorId: cart.vendorId,
        vendorName: cart.vendorName ?? '',
        items: response.items.map((i) => ({
          skuId: i.skuId,
          productId,
          productName: i.productName,
          skuName: i.skuName,
          unitPrice: i.unitPrice,
          quantity: i.quantity,
          notes: cart.items.find((c) => c.skuId === i.skuId)?.notes,
        })),
      }))
      setError(null)
    } catch {
      // Local state is left untouched (still in sync with the server cart);
      // surface the failure so the user knows the quantity was not updated.
      setError('Não foi possível atualizar o carrinho. Tente novamente.')
    }
  }

  // Backend has no decrement/remove endpoint — optimistic local update.
  function setQuantity(skuId: string, nextQty: number) {
    if (!cart.cartId || !cart.vendorId) return
    const nextItems = nextQty <= 0
      ? cart.items.filter((i) => i.skuId !== skuId)
      : cart.items.map((i) => (i.skuId === skuId ? { ...i, quantity: nextQty } : i))
    if (nextItems.length === 0) { dispatch(clearCart()); return }
    dispatch(setCartFromResponse({ cartId: cart.cartId, vendorId: cart.vendorId, vendorName: cart.vendorName ?? '', items: nextItems }))
  }

  function handleCheckout() {
    onClose()
    navigate(auth.status !== 'authenticated' ? '/login?redirect=/checkout' : '/checkout')
  }

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="w-full max-w-md p-0 flex flex-col">
        {/* Header */}
        <div className="p-5 border-b border-slate-100 flex items-center gap-2">
          <div className="w-9 h-9 rounded-xl bg-brand-50 flex items-center justify-center text-brand-600">
            <ShoppingBag className="w-5 h-5" />
          </div>
          <div className="text-left">
            <h2 className="font-display font-extrabold text-slate-800 text-lg leading-tight">O Seu Carrinho</h2>
            <p className="text-xs text-slate-400 font-semibold uppercase">{count} {count === 1 ? 'item' : 'itens'}</p>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-5 space-y-6">
          {error && (
            <div className="flex items-center gap-2 rounded-2xl border border-red-100 bg-red-50 px-4 py-3 text-xs font-semibold text-red-600 text-left">
              <AlertCircle className="h-4 w-4 flex-shrink-0" /> {error}
            </div>
          )}
          {cart.items.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center space-y-4 pt-16">
              <div className="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center text-4xl">🛒</div>
              <div className="space-y-1">
                <h3 className="font-display font-bold text-slate-800 text-lg">O seu carrinho está vazio</h3>
                <p className="text-slate-400 text-xs font-medium max-w-xs mx-auto">Explore os menus e adicione as suas refeições favoritas.</p>
              </div>
              <button onClick={onClose} className="px-5 py-2.5 bg-brand-600 hover:bg-brand-500 text-white font-bold text-xs rounded-full shadow-md shadow-brand-500/10 transition-all">Explorar Menus</button>
            </div>
          ) : (
            <>
              {cart.vendorName && (
                <div className="flex items-center gap-3 bg-slate-50 p-3.5 rounded-2xl border border-slate-100 text-left">
                  <div className="w-10 h-10 rounded-xl bg-white border border-slate-200 flex items-center justify-center text-lg capitalize">🏪</div>
                  <div>
                    <h4 className="font-display font-extrabold text-slate-800 text-sm leading-tight capitalize">{cart.vendorName}</h4>
                    <p className="text-[10px] text-emerald-600 font-bold flex items-center gap-1 mt-0.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" /> Pedido em andamento
                    </p>
                  </div>
                </div>
              )}

              <div className="space-y-4">
                {cart.items.map((item) => (
                  <div key={item.skuId} className="flex gap-3 py-3 border-b border-slate-100 text-left justify-between items-start">
                    <div className="w-14 h-14 rounded-xl bg-gradient-to-br from-slate-100 to-slate-50 flex items-center justify-center text-2xl flex-shrink-0">🍽️</div>
                    <div className="flex-1 space-y-1 min-w-0 pr-2">
                      <h4 className="font-display font-bold text-slate-800 text-xs sm:text-sm tracking-tight truncate">{item.productName}</h4>
                      {item.skuName !== item.productName && <p className="text-[10px] text-slate-400 font-medium truncate">{item.skuName}</p>}
                      {item.notes && <p className="text-[10px] text-brand-600 font-semibold bg-brand-50 inline-block px-1.5 py-0.5 rounded italic truncate max-w-full">Obs: {item.notes}</p>}
                      <div className="flex items-center gap-3 pt-1">
                        <div className="border border-slate-200 rounded-lg flex items-center h-7 px-2 gap-2 text-xs font-bold text-slate-700 bg-slate-50">
                          <button onClick={() => setQuantity(item.skuId, item.quantity - 1)} className="p-0.5 hover:bg-slate-200 rounded" aria-label="Diminuir"><Minus className="w-3 h-3" /></button>
                          <span className="w-4 text-center">{item.quantity}</span>
                          <button onClick={() => increment(item.skuId, item.productId)} className="p-0.5 hover:bg-slate-200 rounded" aria-label="Aumentar"><Plus className="w-3 h-3" /></button>
                        </div>
                        <button onClick={() => setQuantity(item.skuId, 0)} className="text-slate-400 hover:text-red-500 p-1 rounded-full transition-colors" aria-label="Remover"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                    <span className="font-display font-extrabold text-sm text-slate-800 flex-shrink-0">{formatMZN(item.unitPrice * item.quantity)}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        {/* Footer summary. Fees/taxes/discounts are computed by the backend at
            checkout, so here we only show the real item subtotal. */}
        {cart.items.length > 0 && (
          <div className="p-5 bg-slate-50 border-t border-slate-100 space-y-4">
            <div className="space-y-1.5 text-xs text-slate-500 font-semibold">
              <div className="flex justify-between">
                <span>Subtotal</span>
                <span className="text-slate-800 font-bold">{formatMZN(subtotal)}</span>
              </div>
              <div className="flex justify-between">
                <span>Taxa de entrega</span>
                <span className="text-slate-400">Calculada no checkout</span>
              </div>
              <div className="flex justify-between text-sm text-slate-800 font-extrabold pt-2 border-t border-slate-200/60">
                <span>Total estimado</span>
                <span className="text-brand-600">{formatMZN(subtotal)}</span>
              </div>
            </div>
            <button onClick={handleCheckout} className="w-full bg-brand-600 hover:bg-brand-500 text-white font-bold h-12 rounded-xl flex items-center justify-center gap-2 shadow-lg shadow-brand-500/20 transition-all active:scale-[0.98]">
              Continuar para checkout <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}
