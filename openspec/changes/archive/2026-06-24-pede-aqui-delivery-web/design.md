## Context

The monorepo already has a Spring Boot backend, a Next.js backoffice, and two Flutter mobile apps. Customers in Mozambique need a browser-based ordering experience without requiring a mobile app. The delivery web app will consume the same `/api/v1` REST endpoints already used by the Flutter customer app. Keycloak is already configured for PKCE; we add a new `pede-aqui-web` public client in the existing `delivery` realm.

Current state: no web customer-facing application exists. `pede-aqui-backoffice/` is operational for operators/vendors and serves as the architectural reference for the API client pattern (`client.ts` → `services.ts`).

## Goals / Non-Goals

**Goals:**
- New Vite + React 18 + TypeScript app at `pede-aqui-delivery/`
- Tailwind CSS + shadcn/ui for styling (consistent, low-effort component baseline)
- Redux Toolkit for auth and cart state (mirrors backoffice pattern)
- Keycloak PKCE login/logout (no password grant — public browser client)
- Full ordering flow: browse vendors → browse catalog → add to cart → checkout → order confirmation
- Order history and status tracking

**Non-Goals:**
- Real-time WebSocket order tracking (polling is sufficient for MVP)
- PWA / service worker / offline support
- SSR or Next.js (Vite SPA is simpler and avoids server cost)
- Payment UI (mock payment only, same as Flutter app)
- Courier or vendor-facing features (covered by other apps)
- i18n beyond Portuguese labels

## Decisions

### 1. Vite SPA over Next.js
Rationale: No server-side rendering needed; the backend is the API. Vite is faster to build and deploy as a static site (Vercel/S3). The backoffice uses Next.js but customer app has no SEO requirement at MVP stage.

Alternatives considered: Next.js App Router — rejected because it adds SSR complexity and hosting cost without benefit for an authenticated app.

### 2. shadcn/ui over a full component library (MUI, Ant Design)
Rationale: shadcn copies source into the project; no runtime dependency, full Tailwind integration, small bundle. Fits the "already-installed dependency" rung of the ladder — Tailwind is already chosen.

Alternatives: MUI — rejected (large bundle, separate styling system conflicts with Tailwind).

### 3. Redux Toolkit for auth + cart state
Rationale: Backoffice already uses RTK; same pattern. Auth slice holds token/user, cart slice holds items and persists to localStorage. TanStack Query for server data (catalog, orders).

Alternative: Zustand — simpler but mixing two state libraries across apps in the same monorepo adds cognitive overhead. RTK is already a known pattern here.

### 4. Keycloak PKCE (Authorization Code + PKCE) over password grant
Rationale: Public browser client cannot store a client secret. Password grant is deprecated in OAuth 2.1. PKCE is the correct flow for SPAs.
Implementation: `oidc-client-ts` or direct fetch to Keycloak token endpoint with PKCE — use `oidc-client-ts` to avoid reimplementing PKCE challenge/verifier/redirect handling.

### 5. Cart persisted to localStorage
Rationale: Cart survives page refresh without a server round-trip. On login, merge localStorage cart with any server-side cart if the backend supports it; otherwise localStorage-only is sufficient for MVP.

### 6. Directory layout mirrors backoffice conventions
```
pede-aqui-delivery/
  src/
    app/           # Route pages (React Router)
    components/    # Shared UI components
    features/      # Feature slices (auth, catalog, cart, checkout, orders)
    lib/api/       # client.ts + services.ts + types.ts (same pattern as backoffice)
    store/         # Redux store + slices
  public/
```

## Risks / Trade-offs

- [PKCE redirect] Browser redirect to Keycloak and back must survive the SPA router. Mitigation: dedicate `/auth/callback` route to handle the code exchange.
- [CORS] Backend must allow the delivery web app origin. Mitigation: add `http://localhost:5173` and prod origin to `SecurityConfig` CORS allowed origins.
- [Cart merge] No backend cart sync in MVP means multi-device cart is lost on login from a new device. Mitigation: note as known limitation; backend cart API exists to add later.
- [Token refresh] `oidc-client-ts` silent renew requires `prompt=none` iframe or refresh token grant. Mitigation: use refresh token grant (Keycloak default); silent renew as fallback.
- [Keycloak client] A new `pede-aqui-web` public client must be created in the Keycloak realm before login works. Mitigation: document in README; add to local Keycloak realm export.
