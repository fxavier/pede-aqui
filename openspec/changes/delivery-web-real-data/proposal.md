## Why

The `pede-aqui-delivery` web app currently falls back to hardcoded mock data in three pages (home, vendor, catalogo) when the backend is unreachable or returns empty results, meaning users see stale placeholder content in production instead of real vendor and product data. Removing the fallback ensures the app reflects live backend state and surface API errors clearly.

## What Changes

- `src/app/page.tsx`: Remove `MOCK_VENDORS` / `mockVendorVertical` fallback; show error state when vendor search fails. Verticals carousel and "Browse by category" links use backend categories instead of hardcoded `VERTICALS` list.
- `src/app/vendor/page.tsx`: Remove `MOCK_PRODUCTS` fallback; show empty/error state when product fetch fails.
- `src/app/catalogo/page.tsx`: Replace full mock-driven vendor + product lists with real API calls; remove `MOCK_VENDORS`, `MOCK_PRODUCTS`, `mockVendorVertical` imports.
- `src/lib/mockData.ts`: Remove `MOCK_VENDORS`, `MOCK_PRODUCTS`, `mockVendorVertical`. Retain `VERTICALS` only if still referenced elsewhere, otherwise delete the file.
- `src/lib/api/services.ts`: Add `verticalService.list()` / extend `catalogService` if the backend exposes a verticals or categories-by-vertical endpoint; otherwise drive vertical tabs from `catalogService.getCategories()`.
- `src/lib/api/types.ts`: Add any missing types needed (e.g. vertical, category-with-vertical grouping).

## Capabilities

### New Capabilities

- `vendor-browse-live`: Home page vendor grid and vertical/category tabs driven entirely by live backend data (vendor search + category listing APIs).
- `catalog-browse-live`: Catalogo and vendor product pages driven entirely by live catalog API with proper loading/error states.

### Modified Capabilities

- `vendor-catalog-browse`: Requirements change — mock fallback removed; app must show loading/error UI instead of mock data when backend is unavailable.

## Impact

- **Files changed**: `src/app/page.tsx`, `src/app/vendor/page.tsx`, `src/app/catalogo/page.tsx`, `src/lib/mockData.ts`, `src/lib/api/services.ts`, `src/lib/api/types.ts`
- **No new dependencies** — TanStack Query already handles server state; existing `vendorService.search()` and `catalogService.getProducts()` / `getCategories()` already exist
- **Backend APIs required**: `GET /api/v1/search/vendors`, `GET /api/v1/catalog/vendors/{vendorId}/products`, `GET /api/v1/catalog/categories` — all already implemented
- **Breaking for dev workflow**: Mock fallback removal means the app requires a running backend to show meaningful content (documented in CLAUDE.md already)
