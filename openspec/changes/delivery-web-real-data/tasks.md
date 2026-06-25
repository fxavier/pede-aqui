## 1. Extend API services for verticals

- [x] 1.1 Add a `groupByVertical(categories: Category[])` utility in `src/lib/api/services.ts` (or a small `src/lib/verticals.ts`) that returns unique vertical slugs from the category list
- [x] 1.2 Add a client-side `VERTICAL_META` map (slug → `{ label, emoji }`) covering the known verticals (`restaurantes`, `supermercados`, `farmacias`, `bebidas`, `outros`, etc.)

## 2. Update home page (`src/app/page.tsx`)

- [x] 2.1 Replace `VERTICALS` constant import with a `useQuery` call to `catalogService.getCategories()` and derive the vertical tab list from it using `groupByVertical`
- [x] 2.2 Remove `MOCK_VENDORS` and `mockVendorVertical` imports and the fallback logic in the `useMemo` that returns mock vendors when `isError` is true
- [x] 2.3 Add an error state card (with retry button using `refetch`) to the vendor grid section for when `isError` is true
- [x] 2.4 Ensure the "Browse by category" grid (`/catalogo/{id}` links) is rendered from the derived vertical list, not the hardcoded `BROWSEABLE_VERTICALS`

## 3. Update vendor page (`src/app/vendor/page.tsx`)

- [x] 3.1 Remove the `MOCK_PRODUCTS` import and the `const allProducts = isError || products.length === 0 ? MOCK_PRODUCTS : products` fallback line
- [x] 3.2 Add an error state (with retry) when the catalog API call fails
- [x] 3.3 Add an explicit empty state ("Cardápio não disponível") when products list is empty

## 4. Update catalogo page (`src/app/catalogo/page.tsx`)

- [x] 4.1 Remove all imports of `VERTICALS`, `MOCK_VENDORS`, `MOCK_PRODUCTS`, `mockVendorVertical` from `mockData`
- [x] 4.2 Replace hardcoded vendor list with `vendorService.search({ category: verticalId })` via `useQuery`
- [x] 4.3 For each vendor, replace hardcoded product list with `catalogService.getProducts(vendor.vendorId)` via `useQuery` (one query per vendor, TanStack Query handles caching)
- [x] 4.4 Add loading skeletons, empty state ("Nenhum resultado para esta categoria"), and error state with retry for the vendor/product fetch

## 5. Cleanup

- [x] 5.1 Search for any remaining `mockData` imports across `src/` and remove them
- [x] 5.2 Delete `src/lib/mockData.ts`
- [x] 5.3 Run `npm run typecheck` and fix any type errors
- [x] 5.4 Run `npm run build` and confirm zero warnings about missing exports or unused imports
