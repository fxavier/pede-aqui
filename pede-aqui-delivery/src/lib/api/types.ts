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
  /** LAC-27: store open right now (hours + closures); closed stores come last in the results. */
  open?: boolean
  /** LAC-27: next opening instant (ISO-8601) when closed; null when unknown. */
  nextOpeningAt?: string | null
}

/** Open state shown to customers: the backend's request-time `open`, falling back to `available` for older APIs. */
export function isVendorOpen(vendor: Pick<VendorSearchResult, 'open' | 'available'>): boolean {
  return vendor.open ?? vendor.available
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
  /** Why a CANCELLED order was cancelled; currently only 'SELLER_REJECTED', null otherwise. */
  cancellationReason?: string | null
  total: number
  deliveryCode: string | null
  customerName: string | null
  vendorName: string | null
  createdAt: string | null
  items: OrderItemResponse[] | null
  deliveryAddress: DeliveryAddress | null
}

/** Keyset page `{ items, nextCursor }`; `nextCursor` is null on the last page (backend common/pagination). */
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

/** One row of the customer's own order history (GET /api/v1/me/orders). */
export interface CustomerOrderSummary {
  id: string
  reference: string
  status: string
  /** Why a CANCELLED order was cancelled; currently only 'SELLER_REJECTED', null otherwise. */
  cancellationReason: string | null
  vendorId: string
  vendorName: string
  itemCount: number
  subtotal: number
  fees: number
  taxes: number
  discountTotal: number
  total: number
  createdAt: string
}

/** The customer's own order (GET /api/v1/me/orders/{id}); `deliveryCode` is null once the order is finished. */
export interface CustomerOrderDetail {
  id: string
  reference: string
  status: string
  cancellationReason: string | null
  vendorId: string
  vendorName: string
  createdAt: string
  items: OrderItemResponse[]
  deliveryAddress: DeliveryAddress | null
  deliveryCode: string | null
  subtotal: number
  fees: number
  /** Null for orders placed before the pricing quote existed. */
  deliveryFee: number | null
  serviceFee: number | null
  taxes: number
  discountTotal: number
  total: number
  deliveryFeeBasis: string | null
  distanceKm: number | null
  /** What the customer can do now (LAC-26): cancel at once, ask OPS, nothing. */
  cancellationPhase: CancellationPhase
  /** Fee withheld if a request is approved (REQUEST phase only). */
  cancellationFeeEstimate: number | null
  /** The order's most recent cancellation request, if any. */
  cancellationRequest: CancellationRequest | null
}

export type CancellationPhase = 'FREE' | 'REQUEST' | 'ALREADY_CANCELLED' | 'NOT_CANCELLABLE'

/** A customer cancellation request awaiting (or decided by) operations (LAC-26). */
export interface CancellationRequest {
  id: string
  orderId: string
  orderReference: string | null
  orderStatus: string | null
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED'
  /** Why a REJECTED request ended: OPS_REJECTED, ORDER_ADVANCED (order moved on), ORDER_CLOSED. */
  closureReason: 'OPS_REJECTED' | 'ORDER_ADVANCED' | 'ORDER_CLOSED' | null
  reason: string | null
  feeRate: number
  feeAmount: number
  refundAmount: number | null
  requestedAt: string
  decidedAt: string | null
}

/** Result of POST /me/orders/{id}/cancel: 200 CANCELLED, 202 REQUESTED. */
export interface CustomerCancellationResult {
  orderId: string
  orderStatus: string
  cancellationReason: string | null
  outcome: 'CANCELLED' | 'REQUESTED'
  refundAmount: number | null
  request: CancellationRequest | null
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
