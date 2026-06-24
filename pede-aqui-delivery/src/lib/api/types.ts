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

// Checkout (POST /api/v1/checkout)
export interface CheckoutRequest {
  cartId: string
  idempotencyKey: string
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
