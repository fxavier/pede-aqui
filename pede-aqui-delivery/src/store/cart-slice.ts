import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface CartItem {
  skuId: string
  productId: string
  productName: string
  skuName: string
  unitPrice: number
  quantity: number
}

interface CartState {
  cartId: string | null
  vendorId: string | null
  vendorName: string | null
  items: CartItem[]
}

function loadFromStorage(): CartState {
  try {
    const raw = localStorage.getItem('pede-aqui:cart')
    if (raw) return JSON.parse(raw) as CartState
  } catch { /* ignore */ }
  return { cartId: null, vendorId: null, vendorName: null, items: [] }
}

function saveToStorage(state: CartState) {
  localStorage.setItem('pede-aqui:cart', JSON.stringify(state))
}

const cartSlice = createSlice({
  name: 'cart',
  initialState: loadFromStorage,
  reducers: {
    setCartFromResponse(state, action: PayloadAction<{
      cartId: string
      vendorId: string
      vendorName: string
      items: CartItem[]
    }>) {
      state.cartId = action.payload.cartId
      state.vendorId = action.payload.vendorId
      state.vendorName = action.payload.vendorName
      state.items = action.payload.items
      saveToStorage(state)
    },
    clearCart(state) {
      state.cartId = null
      state.vendorId = null
      state.vendorName = null
      state.items = []
      localStorage.removeItem('pede-aqui:cart')
    },
    replaceCart(state, action: PayloadAction<{
      cartId: string
      vendorId: string
      vendorName: string
      items: CartItem[]
    }>) {
      state.cartId = action.payload.cartId
      state.vendorId = action.payload.vendorId
      state.vendorName = action.payload.vendorName
      state.items = action.payload.items
      saveToStorage(state)
    },
  },
})

export const { setCartFromResponse, clearCart, replaceCart } = cartSlice.actions
export default cartSlice.reducer

export function cartTotal(items: CartItem[]): number {
  return items.reduce((sum, i) => sum + i.unitPrice * i.quantity, 0)
}

export function cartItemCount(items: CartItem[]): number {
  return items.reduce((sum, i) => sum + i.quantity, 0)
}
