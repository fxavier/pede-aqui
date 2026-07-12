# Pede Aqui — Delivery Web App

React + Vite SPA for customers to browse vendors, place orders, and track deliveries.
The UI was refactored to adopt the **pede-aqui-delivery-ui** design language while keeping
the existing architecture (React Router, Redux Toolkit, TanStack Query, Keycloak/OIDC, axios).

## Setup

```bash
cd pede-aqui-delivery
npm install
npm run dev        # http://localhost:5173
```

## Environment (`.env.local`)

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Spring Boot backend |
| `VITE_KEYCLOAK_URL` | `http://localhost:8081` | Keycloak server |
| `VITE_KEYCLOAK_REALM` | `delivery` | Keycloak realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `pede-aqui-web` | Keycloak public client |

## Tech stack

- React 18 + Vite 5 + TypeScript (strict)
- Tailwind CSS v3 + shadcn/ui (Radix)
- Redux Toolkit (auth + cart) · TanStack Query (server state)
- oidc-client-ts (Keycloak PKCE) + ROPC fallback
- React Router v6

## Design system (ported)

- Fonts: **Plus Jakarta Sans** (display + body), Fraunces/JetBrains Mono as fallbacks.
- Palette: **brand = rose** (`#f43f5e`/`#e11d48`) + **accent-orange** (`#FF5A1F`) on a slate neutral scale.
- Tokens live in `tailwind.config.js` (`brand.*`, `accent-orange`) and `src/index.css`
  (shadcn HSL vars remapped, so Button/Dialog/Sheet/Badge re-skin automatically).
- Animations are CSS-only (`card-enter`, `animate-fade-up/scale-in/spin-slow`); the
  prototype's `motion` dependency was intentionally **not** added.

## Screen map (route → ported screen)

| Route | Ported screen | Data source |
|---|---|---|
| `AppShell` | Header + mobile bottom nav | Redux auth/cart, router |
| `/` | HomeView | real categories + vendor search |
| `/catalogo/:verticalId` | RestaurantsView | real vendor search per vertical |
| `/vendor/:vendorId` | RestaurantDetailView + item modal | real products + server cart |
| `CartDrawer` | CartDrawer | Redux cart + real subtotal |
| `/checkout` | CheckoutView | real `checkout({cartId, idempotencyKey})` |
| `/orders` | OrderHistoryView | localStorage order history |
| `/orders/:id` | OrderTrackingView | real `track()` polling + stepper |
| `/orders/:id/confirmation` | confirmation | real order |
| `/profile` (new) | ProfileView | Redux auth (name/email) |

## Data-model caveats (backend has no source — clearly flagged in code)

Cosmetic / not wired to the API, marked with `cosmetic:` or explanatory comments:
- vendor/product **images** → deterministic emoji + gradient covers (`src/lib/covers.ts`);
- home **promos** and partner banner; loyalty tier / points / savings;
- checkout **address + payment method** (only `cartId` + `idempotencyKey` are sent);
- tracking **courier** card and **live map** (real-time GPS not available → static placeholder);
- item **notes** (client-side only; the cart API accepts no notes field);
- cart **decrement/remove** and **coupons** (no backend endpoint → optimistic-local / omitted);
- **reorder** from history (stored order items lack `skuId`/`vendorId`, so it is not offered).

## Limitations (MVP, unchanged)

- No customer order-history endpoint — orders are cached in `localStorage` after checkout.
- Cart is server-side; adding items requires authentication.
- No WebSocket tracking; polls every 30s while an order is active.
