import { apiClient } from './client'
import type {
  AddCartItemRequest,
  CartResponse,
  Category,
  CheckoutQuote,
  CheckoutRequest,
  CustomerAddress,
  CustomerAddressRequest,
  CustomerOrderDetail,
  CustomerCancellationResult,
  CustomerOrderSummary,
  CursorPage,
  DeletionRequest,
  MeResponse,
  OrderRating,
  OrderRatingStatus,
  OrderResponse,
  Product,
  ProfileSettings,
  ProfileUpdateRequest,
  SubmitRatingRequest,
  TrackingResponse,
  VendorPublicInfo,
  VendorSearchResponse,
} from './types'

export const authService = {
  getMe: () => apiClient.get<MeResponse>('/auth/me').then((r) => r.data),
}

export const customerRegistrationService = {
  register: (req: { firstName: string; lastName: string; email: string; password: string }) =>
    apiClient.post('/customers/register', req).then((r) => r.data),
}

export const vendorService = {
  search: (params?: { lat?: number; lng?: number; category?: string }) =>
    apiClient.get<VendorSearchResponse>('/search/vendors', { params }).then((r) => r.data),

  get: (vendorId: string) =>
    apiClient.get<VendorPublicInfo>(`/catalog/vendors/${vendorId}`).then((r) => r.data),
}

export const catalogService = {
  getProducts: (vendorId: string) =>
    apiClient.get<Product[]>(`/catalog/vendors/${vendorId}/products`).then((r) => r.data),

  getCategories: () => apiClient.get<Category[]>('/catalog/categories').then((r) => r.data),
}

export const cartService = {
  addItem: (customerId: string, req: AddCartItemRequest) =>
    apiClient.post<CartResponse>(`/customers/${customerId}/cart/items`, req).then((r) => r.data),
}

export const addressService = {
  list: () => apiClient.get<CustomerAddress[]>('/customers/me/addresses').then((r) => r.data),

  create: (req: CustomerAddressRequest) =>
    apiClient.post<CustomerAddress>('/customers/me/addresses', req).then((r) => r.data),

  update: (id: string, req: CustomerAddressRequest) =>
    apiClient.put<CustomerAddress>(`/customers/me/addresses/${id}`, req).then((r) => r.data),

  remove: (id: string) => apiClient.delete(`/customers/me/addresses/${id}`).then(() => undefined),
}

export const checkoutService = {
  checkout: (req: CheckoutRequest) =>
    apiClient.post<OrderResponse>('/checkout', req).then((r) => r.data),

  /** Fees and total checkout will charge for this cart and address (base-fee estimate without an address). */
  quote: (cartId: string, addressId?: string) =>
    apiClient
      .get<CheckoutQuote>('/checkout/quote', { params: addressId ? { cartId, addressId } : { cartId } })
      .then((r) => r.data),
}

export const orderService = {
  track: (orderId: string) =>
    apiClient.get<TrackingResponse>(`/orders/${orderId}/tracking`).then((r) => r.data),

  /** One page of the signed-in customer's orders, newest first; pass the previous page's `nextCursor`. */
  listMine: (params: { limit?: number; cursor?: string | null } = {}) =>
    apiClient
      .get<CursorPage<CustomerOrderSummary>>('/me/orders', {
        params: { limit: params.limit, ...(params.cursor ? { cursor: params.cursor } : {}) },
      })
      .then((r) => r.data),

  /** The signed-in customer's own order (404 when it is not theirs). */
  getMine: (orderId: string) =>
    apiClient.get<CustomerOrderDetail>(`/me/orders/${orderId}`).then((r) => r.data),

  /** Cancels (free phase, 200) or asks to cancel (after acceptance, 202) the signed-in customer's order. */
  cancelMine: (orderId: string, reason?: string) =>
    apiClient
      .post<CustomerCancellationResult>(`/me/orders/${orderId}/cancel`, reason ? { reason } : {})
      .then((r) => r.data),
}

export const profileService = {
  /** The signed-in customer's editable profile and preferences. */
  get: () => apiClient.get<ProfileSettings>('/me/profile').then((r) => r.data),

  /** Partial update: send only changed fields (e-mail and phone are not editable here). */
  update: (req: ProfileUpdateRequest) => apiClient.patch<ProfileSettings>('/me', req).then((r) => r.data),
}

export const privacyService = {
  /** Latest account-deletion request (404 deletion_request_not_found when there never was one). */
  getDeletionRequest: () => apiClient.get<DeletionRequest>('/me/deletion-request').then((r) => r.data),

  /** Starts the 30-day reflection period (202 new, 200 already pending; 409 active_orders_present). */
  requestDeletion: () => apiClient.post<DeletionRequest>('/me/deletion-request').then((r) => r.data),

  /** Cancels the pending request (404 deletion_request_not_found when nothing is pending). */
  cancelDeletion: () => apiClient.delete<DeletionRequest>('/me/deletion-request').then((r) => r.data),

  /** The customer's personal data as a JSON document (429 rate_limited when asked too often). */
  exportData: () => apiClient.get<unknown>('/me/data-export').then((r) => r.data),
}

/** LAC-28: the signed-in customer's rating of one of their orders. */
export const ratingService = {
  /** Whether the order can be rated now (and until when) plus the existing rating, if any. */
  getMine: (orderId: string) =>
    apiClient.get<OrderRatingStatus>(`/me/orders/${orderId}/rating`).then((r) => r.data),

  /** Rates a delivered order once (409 when not delivered, past the 7-day window or already rated). */
  submit: (orderId: string, req: SubmitRatingRequest) =>
    apiClient.post<OrderRating>(`/me/orders/${orderId}/rating`, req).then((r) => r.data),

  /** Replaces stars and comment within 24 h of submitting (409 rating_edit_window_closed after). */
  edit: (orderId: string, req: SubmitRatingRequest) =>
    apiClient.put<OrderRating>(`/me/orders/${orderId}/rating`, req).then((r) => r.data),
}
