## ADDED Requirements

### Requirement: Vendor product page uses only live catalog API data
The system SHALL fetch products exclusively from `GET /api/v1/catalog/vendors/{vendorId}/products`. No mock product data SHALL be used as a fallback.

#### Scenario: Products load from API
- **WHEN** a customer navigates to `/vendor/{vendorId}`
- **THEN** the app calls the catalog API and renders real products

#### Scenario: Product API fails
- **WHEN** `GET /api/v1/catalog/vendors/{vendorId}/products` returns an error
- **THEN** the app displays an error message with a retry button; no mock products are shown

#### Scenario: Vendor has no products
- **WHEN** the catalog API returns an empty product list
- **THEN** the app displays "Cardápio não disponível" with no placeholder cards

---

### Requirement: Catalogo page (vertical browse) uses only live API data
The system SHALL fetch vendor lists and their products from the backend for the `/catalogo/{verticalId}` route. No mock data SHALL be used.

#### Scenario: Vendors and products load for a vertical
- **WHEN** a customer navigates to `/catalogo/{verticalId}`
- **THEN** the app calls `GET /api/v1/search/vendors?category={verticalId}` and then `GET /api/v1/catalog/vendors/{vendorId}/products` for each vendor

#### Scenario: No vendors found for vertical
- **WHEN** the vendor search returns no results for a vertical
- **THEN** the app displays an empty state ("Nenhum resultado para esta categoria") with no mock cards

#### Scenario: API error on catalogo page
- **WHEN** any API call on the catalogo page fails
- **THEN** the app displays an error state with a retry option; no mock data is shown
