## Why

The existing backend and backoffice are operational, but customers currently have no web interface to browse restaurants, place orders, and track deliveries. A React/Vite web app fills this gap and gives browser-based customers a first-class ordering experience without requiring a mobile app install.

## What Changes

- New standalone web application under `pede-aqui-delivery/` in the monorepo
- React 18 + Vite + TypeScript + Tailwind CSS + shadcn/ui component library
- Redux Toolkit for global auth/cart state
- Connects to the existing Spring Boot REST API (`/api/v1`)
- Keycloak PKCE flow for authentication (mirrors the Flutter customer app flow)
- No changes to the backend API or backoffice

## Capabilities

### New Capabilities

- `customer-auth`: Browser-based Keycloak PKCE login, registration redirect, token persistence in sessionStorage, auto-refresh
- `vendor-catalog-browse`: List vendors, browse their product catalog, search and filter by category/vertical
- `cart-management`: Add/remove items, view cart summary, per-tenant cart persisted in Redux + localStorage
- `checkout-flow`: Address entry, order placement via the existing checkout API (idempotency key, mock payment), order confirmation screen
- `order-tracking`: View active and past orders, real-time status polling from the order API

### Modified Capabilities

<!-- No existing specs are being changed -->

## Impact

- New directory `pede-aqui-delivery/` added to the monorepo root
- Depends on existing backend endpoints: `/api/v1/auth`, `/api/v1/catalog`, `/api/v1/cart`, `/api/v1/orders`, `/api/v1/checkout`, `/api/v1/vendors`
- New npm workspace entry; no changes to `pede-aqui-backoffice/` or the backend
- Keycloak realm must have `pede-aqui-web` client configured for PKCE (same realm as `delivery-app`)
