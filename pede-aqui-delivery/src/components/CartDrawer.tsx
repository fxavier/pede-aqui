import { useSelector, useDispatch } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import { Minus, Plus, Trash2 } from 'lucide-react'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { clearCart, cartTotal, cartItemCount } from '@/store/cart-slice'
import type { RootState, AppDispatch } from '@/store'
import { formatMZN } from '@/lib/utils'
import { cartService } from '@/lib/api/services'
import { setCartFromResponse } from '@/store/cart-slice'

interface Props {
  open: boolean
  onClose: () => void
}

export function CartDrawer({ open, onClose }: Props) {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const auth = useSelector((s: RootState) => s.auth)
  const cart = useSelector((s: RootState) => s.cart)
  const total = cartTotal(cart.items)
  const count = cartItemCount(cart.items)

  async function handleQuantityChange(skuId: string, productId: string, _productName: string, _skuName: string, _unitPrice: number, delta: number) {
    if (!auth.sub || !cart.vendorId) return
    const current = cart.items.find((i) => i.skuId === skuId)
    const newQty = (current?.quantity ?? 0) + delta
    if (newQty <= 0) {
      // Remove by setting qty to 0 — backend may not support this; optimistic local remove
      const newItems = cart.items.filter((i) => i.skuId !== skuId)
      if (newItems.length === 0) {
        dispatch(clearCart())
        return
      }
      dispatch(setCartFromResponse({
        cartId: cart.cartId!,
        vendorId: cart.vendorId,
        vendorName: cart.vendorName ?? '',
        items: newItems,
      }))
      return
    }
    try {
      const response = await cartService.addItem(auth.sub, {
        vendorId: cart.vendorId,
        skuId,
        quantity: delta > 0 ? delta : 0,
      })
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
        })),
      }))
    } catch {
      /* optimistic update already applied */
    }
  }

  function handleCheckout() {
    onClose()
    if (auth.status !== 'authenticated') {
      navigate('/login?redirect=/checkout')
    } else {
      navigate('/checkout')
    }
  }

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Carrinho {count > 0 && `(${count})`}</SheetTitle>
        </SheetHeader>

        <div className="mt-6 flex flex-col gap-4 overflow-y-auto" style={{ maxHeight: 'calc(100vh - 200px)' }}>
          {cart.items.length === 0 ? (
            <div className="flex flex-col items-center gap-4 py-12 text-center text-muted-foreground">
              <span className="text-4xl">🛒</span>
              <p>O teu carrinho está vazio</p>
              <Button variant="outline" onClick={onClose}>Ver restaurantes</Button>
            </div>
          ) : (
            cart.items.map((item) => (
              <div key={item.skuId} className="flex items-start gap-3">
                <div className="flex flex-1 flex-col">
                  <span className="text-sm font-medium">{item.productName}</span>
                  {item.skuName !== item.productName && (
                    <span className="text-xs text-muted-foreground">{item.skuName}</span>
                  )}
                  <span className="mt-1 text-sm font-semibold text-primary">{formatMZN(item.unitPrice * item.quantity)}</span>
                </div>
                <div className="flex items-center gap-1">
                  <Button
                    variant="outline"
                    size="icon"
                    className="h-7 w-7"
                    onClick={() => handleQuantityChange(item.skuId, item.productId, item.productName, item.skuName, item.unitPrice, -1)}
                  >
                    {item.quantity === 1 ? <Trash2 className="h-3 w-3" /> : <Minus className="h-3 w-3" />}
                  </Button>
                  <span className="w-6 text-center text-sm">{item.quantity}</span>
                  <Button
                    variant="outline"
                    size="icon"
                    className="h-7 w-7"
                    onClick={() => handleQuantityChange(item.skuId, item.productId, item.productName, item.skuName, item.unitPrice, 1)}
                  >
                    <Plus className="h-3 w-3" />
                  </Button>
                </div>
              </div>
            ))
          )}
        </div>

        {cart.items.length > 0 && (
          <div className="mt-auto border-t pt-4">
            <div className="flex justify-between text-sm font-medium mb-3">
              <span>Total</span>
              <span>{formatMZN(total)}</span>
            </div>
            <Button className="w-full" onClick={handleCheckout}>
              Finalizar Pedido
            </Button>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}
