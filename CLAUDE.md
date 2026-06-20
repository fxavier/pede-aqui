# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-tenant delivery marketplace MVP ("Pede Aqui") targeting Mozambique. Four distinct applications share a single monorepo:

| Directory | Tech | Role |
|---|---|---|
| `backend/` | Java 21 + Spring Boot | REST API, Keycloak JWT auth, Flyway migrations |
| `web/` | Next.js + TypeScript | Backoffice (admin/ops/finance/vendor/support) |
| `pede_aqui_delivery_app/` | Flutter + Dart | Customer mobile app |
| `pede_aqui_courier_app/` | Flutter + Dart | Courier (estafeta) mobile app |

Local services run via Docker Compose: PostgreSQL (PostGIS), Redis, Keycloak, MinIO, Prometheus.

## Local Development

### Start infrastructure

```bash
docker compose up -d postgres redis keycloak minio
```

### Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run
# OpenAPI UI: http://localhost:8080/swagger-ui.html
```

Run a single test class:

```bash
cd backend
mvn -pl backend test -Dtest=SupportTicketServiceTest
```

Full verify (compile + tests):

```bash
cd backend && mvn clean verify
```

### Web (Next.js backoffice)

```bash
cd web
npm ci
npm run dev           # http://localhost:3000
npm run validate      # typecheck + lint + screen coverage
npm run build
```

### Customer mobile app

```bash
cd pede_aqui_delivery_app
flutter pub get
flutter run

# With Keycloak PKCE (Android emulator — use 10.0.2.2 for host)
flutter run -d android \
  --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1 \
  --dart-define=KEYCLOAK_ISSUER=http://10.0.2.2:8081/realms/delivery \
  --dart-define=KEYCLOAK_CLIENT_ID=pede-aqui-mobile \
  --dart-define=KEYCLOAK_REDIRECT_URI=com.pedeaqui.delivery:/oauthredirect
```

### Courier mobile app

```bash
cd pede_aqui_courier_app
flutter pub get
flutter run

# With Keycloak PKCE (Android emulator)
flutter run -d android \
  --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1 \
  --dart-define=KEYCLOAK_ISSUER=http://10.0.2.2:8081/realms/delivery \
  --dart-define=KEYCLOAK_CLIENT_ID=pede-aqui-courier-mobile \
  --dart-define=KEYCLOAK_REDIRECT_URI=com.pedeaqui.courier:/oauthredirect
```

Validate Flutter apps:

```bash
flutter analyze && flutter test
python3 tool/validate_screens.py       # delivery app screen coverage
python3 tool/validate_project.py       # courier app coverage
```

## Backend Architecture

### Package layout (`com.delivery`)

Each domain (e.g. `order`, `vendor`, `dispatch`, `finance`, `support`) owns its own sub-package tree:

```
controller/    REST endpoint, request validation, response DTOs
service/       Business logic, transactions, tenant/role checks, state transitions
repository/    Spring Data JPA (no custom tenant filtering here)
entity/        JPA table mappings
dto/           Request and response payloads
mapper/        Entity ↔ DTO conversion
```

Cross-cutting packages: `common/config`, `common/exception`, `common/security`.

### Security

- Keycloak JWT tokens. `JwtRoleConverter` maps `realm_access.roles` into `ROLE_<name>` Spring authorities.
- `SecurityConfig` opens `/actuator/health/**`, `/api-docs/**`, `/swagger-ui/**`, and `/api/v1/register`. Everything else requires a valid JWT.
- Fine-grained access uses `@PreAuthorize` annotations in service or controller methods.
- Roles in use: `ADMIN`, `OPS`, `VENDOR_ADMIN`, `COURIER`, `CUSTOMER`, `FINANCE`, `SUPPORT`.

### Tenant model

- Tenant-scoped records carry a `tenant_id` column.
- Service methods validate tenant context; repositories are plain Spring Data JPA interfaces.
- The `X-Tenant-Id` header (UUID) must be passed on most `/api/v1/*` requests (auto-documented via `OpenApiConfig`).

### Database

- Migrations live in `backend/backend/src/main/resources/db/migration/` as Flyway `V<NNN>__<name>.sql`.
- `ddl-auto: validate` — Hibernate never mutates schema; always create a migration for schema changes.
- PostGIS extension enabled (used for geo/zone features).

### Storage

MinIO (local) / AWS S3 (prod) for document uploads. Presigned URLs issued by the backend; clients upload directly.

## Web Backoffice Architecture

Next.js App Router. Routes mirror operational domains: `/admin`, `/operations`, `/finance`, `/vendor`, `/support`.

Source layout:

```
src/app/        App Router pages (admin, operations, finance, vendor, support)
src/features/   Feature modules with business logic per domain
src/components/ Shared UI primitives
src/services/   API service layer (currently mock, API-ready)
```

Mock data is active by default. To connect real backend: set `NEXT_PUBLIC_ENABLE_MOCKS=false` and `NEXT_PUBLIC_API_BASE_URL`.

## Flutter App Architecture

Both mobile apps follow the same pattern:

**Delivery app** (`pede_aqui_delivery_app`):
- `lib/core/` — config, DI (GetIt), network (Dio), constants
- `lib/features/` — auth, cart, catalog, checkout, orders, settings (each feature-isolated)
- `lib/shared/` — reusable widgets
- State management: BLoC/Cubit (`flutter_bloc`)
- DI registration in `lib/core/di/service_locator.dart`; mock repositories by default, swap to API repositories for backend integration

**Courier app** (`pede_aqui_courier_app`):
- `lib/core/` — constants, DI (GetIt), network, providers, theme, utils
- `lib/data/` — datasources (mock + remote), models, repositories
- `lib/presentation/` — cubits, screens, widgets
- Currency: Metical (MZN / `MT`), locale: Portuguese (Portugal)
- Switch from `MockCourierDataSource` to `RemoteCourierDataSource` in `lib/core/di/injection.dart` for API integration

## Key API Flows

- **Checkout**: cart → checkout (idempotency key) → mock payment → delivery record — all in one transaction.
- **Delivery completion**: courier submits 6-digit OTP from customer; invalid attempts counted but OTP never logged.
- **Vendor fulfillment state machine**: `PAYMENT_CONFIRMED → ACCEPTED_BY_VENDOR → PREPARING → READY_FOR_PICKUP`.
- **Courier delivery state machine**: `ACCEPTED → ARRIVED_AT_VENDOR → PICKED_UP → ON_ROUTE_TO_CUSTOMER → ARRIVED_AT_CUSTOMER → DELIVERED | FAILED_DELIVERY`.
- **Dispatch**: first eligible courier in `operatingZoneId` (APPROVED + online). Reassignment excludes prior courier.
- **Audit**: sensitive admin/ops/finance actions written to `audit_logs` (actor, target, action, result, timestamp).

## Architecture Constraints

The backend deliberately avoids DDD, Clean/Hexagonal Architecture, CQRS, event sourcing, command buses, and Kafka. Keep it in the simple layered model described in `docs/architecture.md`.

Every public class carries a short responsibility comment. Public service methods include short purpose comments. Business rules get comments only when non-obvious.
