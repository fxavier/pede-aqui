import { describe, it, expect, beforeEach } from 'vitest'
import reducer, {
  setCartFromResponse,
  replaceCart,
  clearCart,
  cartTotal,
  cartItemCount,
  type CartItem,
} from './cart-slice'

const STORAGE_KEY = 'pede-aqui:cart'

function item(overrides: Partial<CartItem> = {}): CartItem {
  return {
    skuId: 'sku-1',
    productId: 'prod-1',
    productName: 'Frango Grelhado',
    skuName: 'Dose Inteira',
    unitPrice: 450,
    quantity: 1,
    ...overrides,
  }
}

function emptyState() {
  return reducer(undefined, { type: '@@INIT' })
}

beforeEach(() => {
  localStorage.clear()
})

describe('cart-slice', () => {
  it('starts empty when nothing is persisted', () => {
    const state = emptyState()
    expect(state.cartId).toBeNull()
    expect(state.vendorId).toBeNull()
    expect(state.items).toEqual([])
  })

  it('setCartFromResponse adds items and persists them', () => {
    const state = reducer(
      emptyState(),
      setCartFromResponse({ cartId: 'cart-1', vendorId: 'v-1', vendorName: 'Loja Um', items: [item()] }),
    )

    expect(state.cartId).toBe('cart-1')
    expect(state.vendorId).toBe('v-1')
    expect(state.vendorName).toBe('Loja Um')
    expect(state.items).toHaveLength(1)
    expect(state.items[0].skuId).toBe('sku-1')

    const persisted = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(persisted.cartId).toBe('cart-1')
    expect(persisted.items).toHaveLength(1)
  })

  it('setCartFromResponse updates quantities of existing items', () => {
    const withItem = reducer(
      emptyState(),
      setCartFromResponse({ cartId: 'cart-1', vendorId: 'v-1', vendorName: 'Loja Um', items: [item()] }),
    )
    const updated = reducer(
      withItem,
      setCartFromResponse({ cartId: 'cart-1', vendorId: 'v-1', vendorName: 'Loja Um', items: [item({ quantity: 3 })] }),
    )

    expect(updated.items).toHaveLength(1)
    expect(updated.items[0].quantity).toBe(3)
  })

  it('replaceCart swaps the entire cart to another vendor', () => {
    const withItem = reducer(
      emptyState(),
      setCartFromResponse({ cartId: 'cart-1', vendorId: 'v-1', vendorName: 'Loja Um', items: [item()] }),
    )
    const replaced = reducer(
      withItem,
      replaceCart({
        cartId: 'cart-2',
        vendorId: 'v-2',
        vendorName: 'Loja Dois',
        items: [item({ skuId: 'sku-9', productId: 'prod-9', quantity: 2 })],
      }),
    )

    expect(replaced.cartId).toBe('cart-2')
    expect(replaced.vendorId).toBe('v-2')
    expect(replaced.items).toHaveLength(1)
    expect(replaced.items[0].skuId).toBe('sku-9')
  })

  it('clearCart empties state and removes the persisted cart', () => {
    const withItem = reducer(
      emptyState(),
      setCartFromResponse({ cartId: 'cart-1', vendorId: 'v-1', vendorName: 'Loja Um', items: [item()] }),
    )
    const cleared = reducer(withItem, clearCart())

    expect(cleared.cartId).toBeNull()
    expect(cleared.vendorId).toBeNull()
    expect(cleared.vendorName).toBeNull()
    expect(cleared.items).toEqual([])
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('rehydrates persisted cart on init', () => {
    reducer(
      emptyState(),
      setCartFromResponse({ cartId: 'cart-1', vendorId: 'v-1', vendorName: 'Loja Um', items: [item({ quantity: 2 })] }),
    )

    const rehydrated = emptyState()
    expect(rehydrated.cartId).toBe('cart-1')
    expect(rehydrated.items[0].quantity).toBe(2)
  })
})

describe('cart selectors', () => {
  it('cartTotal sums unitPrice * quantity', () => {
    const items = [item({ unitPrice: 100, quantity: 2 }), item({ skuId: 'sku-2', unitPrice: 50, quantity: 3 })]
    expect(cartTotal(items)).toBe(350)
  })

  it('cartItemCount sums quantities', () => {
    const items = [item({ quantity: 2 }), item({ skuId: 'sku-2', quantity: 3 })]
    expect(cartItemCount(items)).toBe(5)
  })
})
