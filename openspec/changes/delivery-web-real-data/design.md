## Context

The `pede-aqui-delivery` SPA was built with a mock-data safety net: when vendor search or product fetches fail (or return empty), three pages fall back to `MOCK_VENDORS` / `MOCK_PRODUCTS` from `src/lib/mockData.ts`. All real API infrastructure already exists — `vendorService.search()`, `catalogService.getProducts()`, `catalogService.getCategories()` — and TanStack Query already manages loading/error states. The only work is removing the fallback paths and wiring proper empty/error UI.

The verticals carousel and "Browse by category" grid are currently driven by a hardcoded `VERTICALS` constant in `mockData.ts`. The backend exposes `GET /api/v1/catalog/categories` which returns categories with a `vertical` field — this is enough to derive the vertical list dynamically.

## Goals / Non-Goals

**Goals:**
- Remove all `MOCK_VENDORS`, `MOCK_PRODUCTS`, and `mockVendorVertical` usage from pages
- Drive verticals from `catalogService.getCategories()` grouped by `vertical` field
- Show proper loading skeletons, empty states, and error states on API failure
- Delete `mockData.ts` (or strip it to nothing if nothing else imports it)

**Non-Goals:**
- Changing any backend API contracts
- Adding new API endpoints
- Geolocation / coordinates-based vendor search (existing `vendorService.search()` already accepts optional lat/lng)
- Adding image loading for vendor logos or product images (separate concern)

## Decisions

**1. Derive verticals from categories API, not a new endpoint**

The `Category` type already has a `vertical` field. Grouping `catalogService.getCategories()` results by `vertical` gives us the vertical list without any backend changes. This is the laziest correct solution.

Alternative considered: add `GET /api/v1/catalog/verticals` to the backend. Rejected — no new endpoint needed when categories already carry this data.

**2. Remove fallback silently, show error UI instead**

When the API fails, pages show an error card with a retry button (using TanStack Query's `refetch`). No toast-based error system — inline error state is simpler and already consistent with how `isError` is handled in `vendor/page.tsx`.

Alternative considered: keep mock data as a dev-only fallback behind `import.meta.env.DEV`. Rejected — dev fallback adds complexity and masks integration bugs; devs should run the backend.

**3. Verticals emoji/label mapping stays in the client**

Backend categories have a `name` and `vertical` string but no emoji or display label. A small client-side lookup map (vertical-slug → emoji + label) stays in the UI layer. This is a presentation concern, not a data concern.

Alternative considered: move emoji to backend. Rejected — out of scope and adds a migration.

**4. Catalogo page: fetch vendors by vertical via search API**

`GET /api/v1/search/vendors?category=<verticalId>` already filters by category/vertical. The catalogo page calls `vendorService.search({ category: verticalId })` and then, for each vendor, calls `catalogService.getProducts(vendor.vendorId)`. No new service method needed.

## Risks / Trade-offs

- **N+1 product fetches on catalogo page** — catalogo currently renders products for every vendor in a vertical. With real data this fires one `getProducts` call per vendor. For MVP this is acceptable; TanStack Query caches each call and the vendor count per vertical is small. → Mitigation: each per-vendor query is independent and cached; revisit if vendor count per vertical grows large.
- **Empty verticals** — if a vertical has no vendors the home page carousel will show it but the grid will be empty. This is correct behaviour; no mitigation needed.
- **Backend unavailable in dev** — removing the fallback means a blank/error screen without a running backend. → Documented in CLAUDE.md; devs run `docker compose up -d` and the Spring Boot server.

## Migration Plan

1. Update `catalogService` to expose a `getCategories()` result grouped by vertical (util function, no new service method).
2. Update `src/app/page.tsx`: replace mock vendor/vertical logic with real API calls; add error state.
3. Update `src/app/vendor/page.tsx`: remove `MOCK_PRODUCTS` fallback; add empty/error state.
4. Update `src/app/catalogo/page.tsx`: replace all mock imports with API calls.
5. Delete `src/lib/mockData.ts` (verify no other imports remain).
6. Run `npm run typecheck && npm run build` to confirm no dead imports.

No rollback strategy needed — this is a front-end only change with no database or API contract changes. Reverting is a git revert.
