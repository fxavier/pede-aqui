## ADDED Requirements

### Requirement: Home page vendor grid is driven by live API data
The system SHALL fetch vendor data exclusively from `GET /api/v1/search/vendors` (optionally filtered by `category`). No mock or hardcoded vendor data SHALL be used.

#### Scenario: Vendors load from API on page open
- **WHEN** a customer opens the home page
- **THEN** the app calls `GET /api/v1/search/vendors` and renders the returned vendor list

#### Scenario: API fails to load vendors
- **WHEN** the vendor search API call fails or times out
- **THEN** the app displays an error card with a retry button and no mock vendors are shown

#### Scenario: API returns empty vendor list
- **WHEN** `GET /api/v1/search/vendors` returns an empty `vendors` array
- **THEN** the app displays "Nenhum restaurante disponível" and no placeholder cards are shown

---

### Requirement: Vertical tabs are derived from backend category data
The system SHALL build the vertical filter carousel from `GET /api/v1/catalog/categories`, grouping unique `vertical` values. A client-side mapping of vertical-slug → emoji and label is used for display.

#### Scenario: Categories API populates vertical tabs
- **WHEN** the categories API returns categories across multiple verticals
- **THEN** the home page renders one tab per unique vertical (plus an "All" tab)

#### Scenario: Selecting a vertical tab filters vendor list
- **WHEN** a customer clicks a vertical tab
- **THEN** the app calls `GET /api/v1/search/vendors?category=<verticalId>` and updates the vendor grid

#### Scenario: Categories API fails
- **WHEN** `GET /api/v1/catalog/categories` fails
- **THEN** the vertical tabs section shows only the "Todos" (all) tab; vendor grid still loads
