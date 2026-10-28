// Vendor search (GET /api/v1/search/vendors)
export interface VendorSearchResult {
  vendorId: string
  name: string
  distanceKm: number
  distanceMeters: number | null
  available: boolean
  rating: number
  estimatedDeliveryMinutes: number | null
  deliveryFee: number | null
}

export interface VendorSearchResponse {
  vendors: VendorSearchResult[]
  totalCount: number
  page: number
  size: number
}

// Vendor public profile (GET /api/v1/catalog/vendors/{vendorId})
export interface VendorPublicInfo {
  vendorId: string
  name: string
  description: string | null
  address: string | null
  rating: number
  available: boolean
  estimatedDeliveryMinutes: number | null
  logoUrl: string | null
}

// Catalog (GET /api/v1/catalog/vendors/{vendorId}/products)
export interface Sku {
  id: string
  skuCode: string
  name: string
  price: number
  active: boolean
}

export interface Product {
  id: string
  vendorId: string
  categoryId: string
  name: string
  description?: string
  skus: Sku[]
  status?: string
  primaryImageKey?: string
  primaryImageUrl?: string | null
  requiresPrescriptionMetadata?: boolean
}

export interface Category {
  id: string
  name: string
  vertical: string
  active: boolean
  parentId?: string
}

// Cart (POST /api/v1/customers/{customerId}/cart/items)
export interface AddCartItemRequest {
  vendorId: string
  skuId: string
  quantity: number
}

export interface CartItemResponse {
  id: string
  skuId: string
  productName: string
  skuName: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface CartResponse {
  id: string
  vendorId: string
  subtotal: number
  fees: number
  taxes: number
  discounts: number
  total: number
  items: CartItemResponse[]
}

// Checkout quote (GET /api/v1/checkout/quote) — same pricing engine as checkout (INC-13)
export type DeliveryFeeBasis = 'DISTANCE_RULE' | 'POLICY_BASE' | 'PLATFORM_DEFAULT' | 'EMPTY_CART'

export interface CheckoutQuote {
  cartId: string
  addressId: string | null
  subtotal: number
  deliveryFee: number
  serviceFee: number
  taxes: number
  discountTotal: number
  total: number
  deliveryFeeBasis: DeliveryFeeBasis
  /** Straight-line distance used for the fee; null when vendor or address has no coordinates. */
  distanceKm: number | null
}

// Checkout (POST /api/v1/checkout)
export interface CheckoutRequest {
  cartId: string
  idempotencyKey: string
  /** One of the customer's saved addresses (required by the current API version, INC-12). */
  addressId: string
}

// Customer addresses (/api/v1/customers/me/addresses)
export interface CustomerAddressRequest {
  label: string
  recipientName: string
  phone: string | null
  line1: string
  line2: string | null
  city: string
  state: string | null
  postalCode: string | null
  latitude: number | null
  longitude: number | null
  deliveryInstructions: string | null
}

export interface CustomerAddress extends CustomerAddressRequest {
  id: string
  createdAt: string
  updatedAt: string
}

/** Immutable delivery address snapshot taken at checkout (only returned to the order's customer). */
export interface DeliveryAddress {
  recipientName: string
  phone: string | null
  line1: string
  line2: string | null
  city: string
  state: string | null
  postalCode: string | null
  latitude: number | null
  longitude: number | null
  instructions: string | null
}

// Orders
export interface OrderItemResponse {
  id: string
  productName: string
  skuName: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface OrderResponse {
  id: string
  reference: string
  status: string
  total: number
  deliveryCode: string | null
  customerName: string | null
  vendorName: string | null
  createdAt: string | null
  items: OrderItemResponse[] | null
  deliveryAddress: DeliveryAddress | null
}

// Tracking (GET /api/v1/orders/{orderId}/tracking)
export interface TrackingResponse {
  orderId: string
  reference: string
  orderStatus: string
  deliveryCode: string | null
}

// Auth (GET /api/v1/auth/me)
export interface MeResponse {
  id: string | null
  keycloakUserId: string
  email: string | null
  displayName: string | null
  roles: string[]
}
