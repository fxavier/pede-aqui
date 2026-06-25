## MODIFIED Requirements

### Requirement: Customer can browse available vendors
The system SHALL display a list of active vendors fetched from `GET /api/v1/search/vendors`. Each vendor card SHALL show name, category/vertical, availability status, rating, and estimated delivery time. **Mock vendor data SHALL NOT be used as a fallback under any circumstances.**

#### Scenario: Vendor list loads successfully
- **WHEN** a customer opens the home page
- **THEN** the app calls `GET /api/v1/search/vendors` and displays the returned vendor list

#### Scenario: No vendors available
- **WHEN** the API returns an empty vendor list
- **THEN** the app displays an empty-state message ("Nenhum restaurante disponível") with no placeholder cards

#### Scenario: API error loading vendors
- **WHEN** the vendor list API call fails
- **THEN** the app displays an error card with a retry button and no mock vendors are injected

---

### Requirement: Customer can view a vendor's product catalog
The system SHALL display a vendor's products when the customer selects a vendor, fetching from `GET /api/v1/catalog/vendors/{vendorId}/products`. Products SHALL be grouped by category. Each product card SHALL show name, description, and price in MZN. **Mock product data SHALL NOT be used as a fallback under any circumstances.**

#### Scenario: Vendor catalog loads
- **WHEN** a customer selects a vendor
- **THEN** the app navigates to `/vendor/{vendorId}` and displays the real product list

#### Scenario: Product with no image
- **WHEN** a product has no `primaryImageKey`
- **THEN** the app displays a placeholder image

#### Scenario: Empty catalog
- **WHEN** a vendor has no active products
- **THEN** the app displays "Cardápio não disponível" with no mock products shown

#### Scenario: Catalog API fails
- **WHEN** `GET /api/v1/catalog/vendors/{vendorId}/products` returns an error
- **THEN** the app displays an error state with a retry button; no mock products are shown
