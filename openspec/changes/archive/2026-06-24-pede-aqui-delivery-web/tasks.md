## 1. Project Scaffold

- [x] 1.1 Create `pede-aqui-delivery/` with `npm create vite@latest` (React + TypeScript template)
- [x] 1.2 Install and configure Tailwind CSS v3 + PostCSS
- [x] 1.3 Install and configure shadcn/ui (init, add button/card/input/dialog/badge/sheet components)
- [x] 1.4 Install dependencies: `@reduxjs/toolkit react-redux react-router-dom oidc-client-ts @tanstack/react-query axios`
- [x] 1.5 Set up `src/` directory layout: `app/`, `components/`, `features/`, `lib/api/`, `store/`
- [x] 1.6 Create `.env.local` template with `VITE_API_BASE_URL`, `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`
- [x] 1.7 Add `pede-aqui-delivery` to monorepo root `package.json` workspaces (if using npm workspaces) — N/A: no root package.json

## 2. API Client

- [x] 2.1 Create `src/lib/api/client.ts` — axios instance attaching `Authorization` header from sessionStorage token
- [x] 2.2 Create `src/lib/api/types.ts` — TypeScript types for Vendor, Product, Order, Cart, CheckoutRequest mirroring backend DTOs
- [x] 2.3 Create `src/lib/api/services.ts` — `vendorService`, `catalogService`, `orderService`, `checkoutService` using `client.ts`

## 3. Auth (customer-auth spec)

- [x] 3.1 Configure `oidc-client-ts` `UserManager` with PKCE settings in `src/features/auth/oidcConfig.ts`
- [x] 3.2 Create `src/store/auth-slice.ts` — Redux slice for `{ user, token, status }` with login/logout/refresh actions
- [x] 3.3 Create `src/app/login/page.tsx` — login button that triggers PKCE redirect
- [x] 3.4 Create `src/app/auth/callback/page.tsx` — handles Keycloak redirect, exchanges code for tokens, stores in sessionStorage, dispatches to Redux, redirects to intended route
- [x] 3.5 Implement silent token refresh on app init (rehydrate from sessionStorage, call refresh if access token expired)
- [x] 3.6 Create `ProtectedRoute` component that redirects unauthenticated users to `/login?redirect=<path>`
- [x] 3.7 Implement logout: clear sessionStorage, dispatch to Redux, redirect to Keycloak logout URL

## 4. App Shell & Routing

- [x] 4.1 Create `src/app/router.tsx` with React Router routes: `/`, `/vendor/:vendorId`, `/cart`, `/checkout`, `/orders`, `/orders/:orderId`, `/orders/:orderId/confirmation`, `/login`, `/auth/callback`
- [x] 4.2 Create `src/components/layout/AppShell.tsx` — top nav with logo, search bar, cart icon (badge), auth button
- [x] 4.3 Wire Redux store (`src/store/index.ts`) and TanStack Query provider in `src/app/main.tsx`

## 5. Vendor & Catalog Browse (vendor-catalog-browse spec)

- [x] 5.1 Create `src/app/page.tsx` (home) — fetches vendor list via `vendorService`, renders `VendorCard` grid
- [x] 5.2 Create `src/components/VendorCard.tsx` — shows name, logo, category, estimated delivery time
- [x] 5.3 Add search input with 300ms debounce filtering vendor list by name client-side
- [x] 5.4 Add horizontal category filter bar (single-select, "Todos" resets filter)
- [x] 5.5 Create `src/app/vendor/page.tsx` — fetches products, groups by category, renders `ProductCard` list
- [x] 5.6 Create `src/components/ProductCard.tsx` — shows image (with placeholder), name, description, price in MZN, "Adicionar" button

## 6. Cart Management (cart-management spec)

- [x] 6.1 Create `src/store/cart-slice.ts` — Redux slice with `{ vendorId, items: [{productId, name, price, quantity}] }`, with localStorage persistence
- [x] 6.2 Implement add-to-cart logic: same vendor → sync; different vendor → show replace dialog
- [x] 6.3 Create `src/components/CartDrawer.tsx` — shadcn Sheet with item list, quantity controls, subtotal, "Finalizar Pedido" button
- [x] 6.4 Implement "Finalizar Pedido" guard: redirect to `/login?redirect=/checkout` if unauthenticated
- [x] 6.5 Rehydrate cart from localStorage on app load (manual init in store initialState)

## 7. Checkout Flow (checkout-flow spec)

- [x] 7.1 Create `src/app/checkout/page.tsx` — two-step form: (1) address, (2) order summary + confirm; redirect to home if cart empty
- [x] 7.2 Implement address form with validation (street, number, neighbourhood, city required; notes optional)
- [x] 7.3 Generate UUID idempotency key per checkout session (stored in component ref, not regenerated on retry)
- [x] 7.4 Call `POST /api/v1/checkout` on confirm; on success: clear cart, navigate to `/orders/{orderId}/confirmation`
- [x] 7.5 Show inline error with retry on checkout API failure (do not regenerate idempotency key)
- [x] 7.6 Create `src/app/orders/confirmation/page.tsx` — renders order details with success message and "Ver meus pedidos" link

## 8. Order Tracking (order-tracking spec)

- [x] 8.1 Create `src/app/orders/page.tsx` — reads localStorage order history, renders order list newest-first (no backend /orders/my endpoint exists)
- [x] 8.2 Create `src/components/OrderCard.tsx` — shows order ID, vendor, status label (Portuguese), total, date
- [x] 8.3 Create `src/lib/orderStatusLabels.ts` — maps all backend status values to Portuguese strings per spec
- [x] 8.4 Create `src/app/orders/detail/page.tsx` — fetches order tracking, renders items + status timeline
- [x] 8.5 Implement 30-second polling via TanStack Query `refetchInterval` for active orders; stop when status is DELIVERED or FAILED_DELIVERY
- [x] 8.6 Handle 404 from order API: display "Pedido não encontrado" with link to `/orders`

## 9. Keycloak Configuration

- [x] 9.1 Add `pede-aqui-web` public client to Keycloak `delivery` realm (valid redirect URIs: `http://localhost:5173/*`, prod URL; web origins for CORS)
- [x] 9.2 Updated `keycloak/delivery-realm.json` with new client (no separate realm-export path exists)

## 10. Backend CORS

- [x] 10.1 Add `http://localhost:5173` (dev) to `app.cors.allowed-origins` in `application.yml`

## 11. README & DevX

- [x] 11.1 Add `pede-aqui-delivery/README.md` with setup steps, env vars, and `npm run dev` instructions
- [x] 11.2 Add `pede-aqui-delivery` to root `README.md` apps list
