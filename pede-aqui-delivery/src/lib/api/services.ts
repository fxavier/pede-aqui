import { apiClient } from './client'
import type {
  AddCartItemRequest,
  CartResponse,
  Category,
  CheckoutRequest,
  MeResponse,
  OrderResponse,
  Product,
  TrackingResponse,
  VendorPublicInfo,
  VendorSearchResponse,
} from './types'

export const authService = {
  getMe: () => apiClient.get<MeResponse>('/auth/me').then((r) => r.data),
}

function parseJwtPayload(token: string): Record<string, unknown> {
  const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(atob(b64))
}

const KC_URL = import.meta.env.VITE_KEYCLOAK_URL as string
const KC_REALM = import.meta.env.VITE_KEYCLOAK_REALM as string
const KC_CLIENT = import.meta.env.VITE_KEYCLOAK_CLIENT_ID as string

export const keycloakService = {
  async login(email: string, password: string): Promise<{ access_token: string; profile: Record<string, unknown> }> {
    const body = new URLSearchParams({
      grant_type: 'password',
      client_id: KC_CLIENT,
      username: email,
      password,
      scope: 'openid profile email',
    })
    const r = await fetch(`${KC_URL}/realms/${KC_REALM}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => ({})) as Record<string, string>
      throw new Error(err.error_description ?? 'Credenciais inválidas')
    }
    const data = await r.json() as { access_token: string }
    return { access_token: data.access_token, profile: parseJwtPayload(data.access_token) }
  },
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

export const checkoutService = {
  checkout: (req: CheckoutRequest) =>
    apiClient.post<OrderResponse>('/checkout', req).then((r) => r.data),
}

export const orderService = {
  track: (orderId: string) =>
    apiClient.get<TrackingResponse>(`/orders/${orderId}/tracking`).then((r) => r.data),
}
