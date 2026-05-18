# Quickstart: Delivery Marketplace MVP

## Prerequisites

- Java 21
- Maven 3.9+
- Docker and Docker Compose
- Node.js 20+ for the web portal
- Flutter stable with Dart 3.x for mobile apps

## Local Services

The local environment uses Docker Compose for PostgreSQL/PostGIS, Redis, Keycloak,
MinIO, and the backend application. Redis is included for rate limiting or targeted
caching only; PostgreSQL remains the source of truth.

## Start Infrastructure

```bash
docker compose up -d postgres redis keycloak minio
```

## Configure Keycloak

Create or import a local realm for the marketplace and configure roles:

- `CUSTOMER`
- `VENDOR_ADMIN`
- `VENDOR_STAFF`
- `COURIER`
- `ADMIN`
- `OPS`
- `FINANCE`
- `SUPPORT`

The backend consumes JWTs from Keycloak and does not store passwords.

## Backend Development

```bash
cd backend
mvn spring-boot:run
```

Expected backend behavior:

- API base path: `/api/v1`
- OpenAPI UI available through the configured Swagger endpoint
- Flyway applies migrations on startup
- Health and metrics endpoints are exposed for local verification
- Structured logs include correlation IDs for important flows

## Web Backoffice Development

```bash
cd web
npm install
npm run dev
```

Role-based sections:

- Admin
- Vendor
- Operations
- Finance
- Support

Each page should include loading, empty, error, and forbidden states.

## Flutter Mobile Development

```bash
cd mobile
flutter pub get
flutter run
```

Customer MVP screens:

- Auth
- Address management
- Vendor discovery
- Catalog browsing
- Cart
- Checkout
- Order status/tracking
- Delivery code display
- Order history

Courier MVP screens:

- Auth
- Online/offline status
- Assignment list
- Accept/reject job
- Pickup flow
- Delivery confirmation code entry
- Earnings summary

## Test Strategy

Run backend tests:

```bash
cd backend
mvn test
```

Required test coverage includes:

- Service unit tests for cart, checkout, order transitions, payment idempotency,
  refund idempotency, delivery code validation, pharmacy validation, and fuel blocking
- Repository integration tests with Testcontainers for PostgreSQL/PostGIS
- API tests for checkout, payment confirmation, order lifecycle, delivery completion,
  tenant isolation, and role authorization
- Web and Flutter tests for critical screens and state transitions where practical

## MVP Acceptance Flow

1. Admin creates tenant, roles, zones, categories, fees, commissions, taxes, and policies.
2. Vendor registers, submits document metadata, configures hours, availability, catalog,
   SKUs, prices, and stock.
3. Customer registers, adds address, searches nearby vendors, filters results, browses
   catalog, builds a single-vendor cart, checks out, and receives order status.
4. Mock/local payment confirmation completes idempotently.
5. Vendor accepts order, prepares it, and marks it ready for pickup.
6. Courier receives assignment, accepts it, marks arrival, pickup, arrival at customer,
   and completes delivery with the 6-digit customer code.
7. Notifications, audit logs, support tickets, finance records, and dashboards reflect
   the completed flow.

## Documentation To Maintain

- `README.md`: setup, run, test, environment variables
- `docs/architecture.md`: simple layered architecture and package responsibilities
- `docs/api.md`: API summary and OpenAPI location
- `docs/local-development.md`: Docker Compose and local Keycloak setup
