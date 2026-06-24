## ADDED Requirements

### Requirement: Customer can browse available vendors
The system SHALL display a paginated list of active vendors for the customer's tenant, fetched from `GET /api/v1/vendors`. Each vendor card SHALL show name, logo, category/vertical, and estimated delivery time.

#### Scenario: Vendor list loads successfully
- **WHEN** a customer opens the home page
- **THEN** the app displays a list of active vendors with name, logo, and category

#### Scenario: No vendors available
- **WHEN** the API returns an empty vendor list
- **THEN** the app displays an empty-state message ("Nenhum restaurante disponível")

#### Scenario: API error loading vendors
- **WHEN** the vendor list API call fails
- **THEN** the app displays an error state with a retry button

### Requirement: Customer can search vendors by name
The system SHALL provide a search input on the home page that filters the vendor list client-side by vendor name (case-insensitive, debounced 300ms).

#### Scenario: Search matches vendors
- **WHEN** a customer types "pizza" in the search field
- **THEN** only vendors whose names contain "pizza" (case-insensitive) are displayed

#### Scenario: Search with no matches
- **WHEN** a customer types a string that matches no vendor names
- **THEN** the app displays "Nenhum resultado encontrado"

### Requirement: Customer can filter vendors by category
The system SHALL provide a horizontal category/vertical filter bar above the vendor list. Selecting a category filters the list to vendors in that category. Multiple categories SHALL NOT be selectable simultaneously (single-select).

#### Scenario: Category filter applied
- **WHEN** a customer selects the "Restaurantes" category
- **THEN** only vendors in that category are displayed

#### Scenario: Category filter cleared
- **WHEN** a customer selects the already-active category or clicks "Todos"
- **THEN** the full vendor list is displayed

### Requirement: Customer can view a vendor's product catalog
The system SHALL display a vendor's products when the customer selects a vendor, fetching from `GET /api/v1/catalog/vendors/{vendorId}/products`. Products SHALL be grouped by category. Each product card SHALL show name, image, description, and price in MZN.

#### Scenario: Vendor catalog loads
- **WHEN** a customer selects a vendor
- **THEN** the app navigates to `/vendor/{vendorId}` and displays the product list grouped by category

#### Scenario: Product with no image
- **WHEN** a product has no image URL
- **THEN** the app displays a placeholder image

#### Scenario: Empty catalog
- **WHEN** a vendor has no active products
- **THEN** the app displays "Cardápio não disponível"
