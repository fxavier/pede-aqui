# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-tenant delivery marketplace MVP ("Pede Aqui") targeting Mozambique. Four distinct applications share a single monorepo:

| Directory | Tech | Role |
|---|---|---|
| `backend/` | Java 21 + Spring Boot | REST API, Keycloak JWT auth, Flyway migrations |
| `pede-aqui-backoffice/` | Next.js 15 + TypeScript | Backoffice (admin/ops/finance/vendor/support) |
| `pede-aqui-delivery/` | React 18 + Vite + TypeScript | Customer-facing delivery web SPA |
| `pede_aqui_delivery_app/` | Flutter + Dart | Customer mobile app |
| `pede_aqui_courier_app/` | Flutter + Dart | Courier (estafeta) mobile app |

(`web/` is an older prototype; active backoffice work is in `pede-aqui-backoffice/`.)

`infra/` and `admin/` hold two Terraform root modules for S3 upload storage (`infra/` = static CORS apply, `admin/` = privileged bootstrap) — see the Storage section.

Local services run via Docker Compose: PostgreSQL (PostGIS), Redis, Keycloak, MinIO, Prometheus.

## Local Development

### Start infrastructure

```bash
docker compose up -d postgres redis keycloak minio minio-init
```

(`minio-init` creates the `pede-aqui-uploads` bucket used by the backend's `dev` profile; it's idempotent.)

### Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run
# OpenAPI UI: http://localhost:8080/swagger-ui.html
```

Run a single test class:

```bash
cd backend
mvn test -Dtest=SupportTicketServiceTest
```

Full verify (compile + tests):

```bash
cd backend && mvn clean verify
```

Spring profiles: `dev` is the default (`spring.profiles.default`) and points storage at local MinIO — no AWS needed. `prod` (`SPRING_PROFILES_ACTIVE=prod`) targets real AWS S3 and fails fast if `AWS_S3_BUCKET`/`AWS_REGION` are missing. See the Storage section.

### Customer web app (React + Vite)

```bash
cd pede-aqui-delivery
npm install           # pnpm install also works (both lockfiles exist)
npm run dev           # http://localhost:5173
npm run typecheck
npm run build
```

If using pnpm: `pnpm-workspace.yaml` must keep `allowBuilds: { esbuild: true }` — pnpm 10+ blocks esbuild's postinstall otherwise and `pnpm dev` fails with `ERR_PNPM_IGNORED_BUILDS`. (The old `pnpm.onlyBuiltDependencies` package.json field is ignored.)

`.env.local` (copy from the checked-in file):
```
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_KEYCLOAK_URL=http://localhost:8081
VITE_KEYCLOAK_REALM=delivery
VITE_KEYCLOAK_CLIENT_ID=pede-aqui-web
```

The app requires a running backend. There is no mock fallback — all pages hit the live API. Public browse endpoints (categories, vendor search, product catalog) work without authentication or a tenant header.

### Backoffice (Next.js)

```bash
cd pede-aqui-backoffice
npm ci
npm run dev           # http://localhost:3000
npm run validate      # typecheck + lint + screen coverage check
npm run build
```

`.env.local` must exist (copy from the checked-in file) with at minimum:
```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_KEYCLOAK_URL=http://localhost:8081
NEXT_PUBLIC_KEYCLOAK_REALM=delivery
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=delivery-app
```

There is no mock mode in the service layer — the API services always hit the backend. If the backend is not running, service calls will fail. (`NEXT_PUBLIC_ENABLE_MOCKS` only affects the `/screens` HTML prototype viewer.)

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

On an Android emulator use `10.0.2.2` to reach host services (as above); on an iOS simulator use `localhost` for the same `--dart-define` URLs. Keycloak redirect URIs must be registered per client exactly as shown.

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

Domain packages: `auth`, `cart`, `catalog`, `customer`, `dashboard`, `delivery`, `dispatch`, `finance`, `geo`, `inventory`, `marketing`, `notification`, `order`, `payment`, `rating`, `support`, `tenant`, `upload`, `vendor`.

### Security

- Keycloak JWT tokens. `JwtRoleConverter` maps `realm_access.roles` into `ROLE_<name>` Spring authorities.
- `SecurityConfig` opens: `/actuator/health/**`, `/api-docs/**`, `/swagger-ui/**`, `/api/v1/register`, `/api/v1/customers/register`, and the three public browse endpoints: `/api/v1/catalog/categories`, `/api/v1/catalog/vendors/*/products`, `/api/v1/search/vendors`. Everything else requires a valid JWT.
- Fine-grained access uses `@PreAuthorize` annotations in service or controller methods.
- Roles: `ADMIN`, `OPS`, `VENDOR_ADMIN`, `COURIER`, `CUSTOMER`, `FINANCE`, `SUPPORT`.

### Tenant model

- Tenant-scoped records carry a `tenant_id` column.
- `TenantContext` resolves tenant: JWT `tenant_id` claim first, then `X-Tenant-Id` request header as fallback (used by platform admins managing tenants).
- Platform super-admins have `ADMIN` role and **no** `tenant_id` in their JWT. `TenantContext.isPlatformAdmin()` identifies them.
- Service methods validate tenant context; repositories are plain Spring Data JPA interfaces.

### Database

- Migrations live in `backend/src/main/resources/db/migration/` as Flyway `V<NNN>__<name>.sql`. (`backend/backend/` is a stray build-artifact dir, not the module.)
- `ddl-auto: validate` — Hibernate never mutates schema; always create a migration for schema changes.
- PostGIS extension enabled (used for geo/zone features).

### Storage

Profile-driven: **dev = MinIO** (default), **prod = AWS S3**. Pattern: call `/uploads/images/presigned-url` or `/uploads/documents/presigned-url` to get `{ uploadUrl, storageKey }`, then PUT the file directly to `uploadUrl`. Pass `storageKey` back to the API to link the file to an entity.

- Presigner is `S3Config` reading `app.storage.*`. A non-empty `s3-endpoint` switches to MinIO/path-style; empty targets real AWS. Explicit access keys win over the AWS default chain.
- **`dev` profile** (`application-dev.yml`, active by default): MinIO at `http://localhost:9000`, bucket `pede-aqui-uploads`, `minioadmin` creds — all hardcoded defaults, overridable via `MINIO_*` vars. The bucket is auto-created by the `minio-init` compose service. MinIO answers CORS `*` by default, so browser uploads need no extra config. The `AWS_*` vars in `.env` are ignored for storage in dev.
- **`prod` profile** (`application-prod.yml`): real S3; `AWS_S3_BUCKET`/`AWS_REGION` have no defaults so startup fails fast if unset. Leave access keys unset in prod when an instance role exists — the default chain picks it up.
- Presigning is pure local crypto (no network call), which is why the compose backend signs `localhost:9000` URLs it can't itself reach — the browser on the host does the PUT.
- `application.yml` auto-imports the repo-root `.env` via `spring.config.import` — but a real OS env var still overrides the `.env` value. A stale backend process on port 8080 is the usual reason config changes "don't take"; kill it (`lsof -i :8080`) before restarting.
- **Browser-direct uploads to real S3 need bucket CORS.** A presigned URL authorizes the PUT but the browser preflight still fails unless the bucket has a CORS policy allowing the app origin. This is the whole reason the Terraform exists. (MinIO doesn't have this problem.)
- **AWS state (2026-07): the dev bucket `pede-aqui-dev-documents-*` was deleted.** `infra/terraform.tfvars` now has `manage_bucket = true` so one `terraform apply` recreates bucket + public-access-block + ownership + CORS — but it requires `s3:CreateBucket`, and **neither local AWS profile has it**: both `default` (`tms-springboot-dev`) and `admin` (`delivery-springboot-dev` — misleading name, it's an app user) are object-rw only. Recreating the bucket needs real admin credentials added to `~/.aws` first.
- **Terraform is split into two root modules — this split is deliberate, don't merge them:**
  - `infra/` is **fully static — no data-source read of the bucket.** Bucket name/ARN/region are hardcoded in `infra/locals.tf` (S3 ARNs carry no account/region, so the ARN derives from the name). This lets `plan` run under a low-privilege identity that can't read the bucket.
  - `admin/` is the privileged bootstrap module (bucket-metadata read + IAM user creation). Run it with an admin profile (`terraform apply -var aws_profile=<admin>`) only when provisioning a new environment.
- **Identities:** the backend app users hold only `s3:PutObject`/`GetObject`/`DeleteObject` — no IAM permissions, no `s3:PutBucketCORS`, no `s3:CreateBucket`. Bucket-level actions are one-time privileged operations: an admin runs `terraform apply` in `infra/`, or sets CORS directly in the S3 console.

## Backoffice Architecture

Next.js App Router. All authenticated pages use `AppShell` from `src/components/layout/app-shell.tsx`.

### Routes

| Route | Role(s) | Purpose |
|---|---|---|
| `/login`, `/register` | — | Auth entry points |
| `/platform` | ADMIN (no tenant) | Super-admin: tenant management and impersonation |
| `/` | all | Dashboard; redirects to `/platform` for platform admins |
| `/empresa` | VENDOR_ADMIN, ADMIN | Own company profile, logo, documents |
| `/admin` | ADMIN | Orders overview, category/vertical management |
| `/catalogo` | ADMIN, VENDOR_ADMIN, OPS | Product catalog, SKUs, categories, families |
| `/vendors` | ADMIN, VENDOR_ADMIN, OPS | Vendor management |
| `/users` | ADMIN | User profiles |
| `/orders` | ADMIN, VENDOR_ADMIN, OPS, SUPPORT | Order management |
| `/couriers` | ADMIN, OPS | Courier management |
| `/finance` | ADMIN, FINANCE | Settlements, earnings |
| `/support` | ADMIN, SUPPORT | Ticket management |
| `/marketing` | ADMIN, OPS | Campaigns |
| `/screens` | all | HTML prototype viewer (not real features) |

### Auth flow

Login page calls Keycloak token endpoint with `grant_type=password`. Token stored in `sessionStorage` as `auth_token` and also set as a cookie. `AppProviders` rehydrates auth state on page load by calling `authService.getMe()`. Root page redirects to `/platform` for platform super-admins or `/` (dashboard) for tenant users.

Platform super-admins can "enter" a tenant (stored in `activeTenantId` Redux state) to operate on its behalf; `X-Tenant-Id` header is sent automatically.

### State management

Redux Toolkit: `auth-slice` (user identity, tenant impersonation), `ui-slice` (sidebar, search). TanStack Query for server state where used. Redux store is in `src/store/`.

### API layer

All API calls go through `src/lib/api/services.ts` via `src/lib/api/client.ts`. The client attaches `Authorization: Bearer <token>` and `X-Tenant-Id` from `sessionStorage` automatically. Service exports: `platformService`, `tenantService`, `authService`, `dashboardService`, `orderService`, `catalogService`, `categoryService`, `vendorService`, `courierService`, `financeService`, `supportService`, `userService`, `uploadService`, `registrationService`, and others. Types are in `src/lib/api/types.ts`.

### Screens prototype area

`/screens` renders static HTML prototypes stored in `public/imported-screens/*.html` via `ImportedScreenView`. These are design references, not live features. The actual feature pages for the same domains live under their own routes above.

## Customer Web App Architecture (`pede-aqui-delivery`)

React 18 + Vite SPA. Tailwind CSS v3 + shadcn/ui components.

### Routes

| Route | Auth required | Purpose |
|---|---|---|
| `/` | No | Home — vendor grid, vertical browsing, category carousel |
| `/vendor/:vendorId` | No (add to cart requires auth) | Vendor product catalog |
| `/catalogo/:verticalId` | No | Products grouped by vendor for a vertical |
| `/login`, `/register` | No | Custom Keycloak ROPC auth (no redirect) |
| `/checkout` | Yes | Checkout flow |
| `/orders` | Yes | Order history |
| `/orders/:orderId` | Yes | Order detail with status polling (30 s) |
| `/orders/:orderId/confirmation` | Yes | Post-checkout confirmation |
| `/profile` | No (shows auth state) | Customer profile |

### Auth flow

Two token paths coexist in `auth-slice.ts`:
- **OIDC PKCE** via `oidc-client-ts` (`userManager`) — for future use
- **ROPC** (primary): `keycloakService.login()` calls Keycloak's token endpoint directly with `grant_type=password`, stores the JWT as `auth_token` in `sessionStorage`. `initAuth` checks `userManager.getUser()` first, then falls back to parsing `auth_token` from `sessionStorage`.

Customer registration hits the backend at `POST /api/v1/customers/register` (open endpoint), which creates a Keycloak user via `KeycloakAdminService.createCustomer()` and assigns the `CUSTOMER` role.

### State management

Redux Toolkit: `auth-slice` (token, sub, displayName, email, status), `cart-slice` (items, vendorId, cartId). TanStack Query for all server data. Store in `src/store/`.

### API layer

All API calls go through `src/lib/api/services.ts` via `src/lib/api/client.ts` (Axios). The client attaches `Authorization: Bearer <token>` from `sessionStorage` when available. Services: `authService`, `keycloakService`, `customerRegistrationService`, `vendorService`, `catalogService`, `cartService`, `checkoutService`, `orderService`.

Vertical metadata (slug → emoji/label) lives in `src/lib/verticals.ts`. The home page derives the vertical list from `GET /api/v1/catalog/categories` grouped by the `vertical` field.

### Key UI components

- `ProductCard` — three-state "Comprar" button (`idle → adding → added`) with CSS spring animation
- `VendorCard` — vendor tile with rating, delivery time, open/closed badge
- `CartDrawer` — slide-in cart panel
- `AppShell` — top nav with cart icon badge; wraps all authenticated-capable routes

## Flutter App Architecture

**Delivery app** (`pede_aqui_delivery_app`):
- `lib/core/` — config, DI (GetIt), network (Dio), constants
- `lib/features/` — auth, cart, catalog, checkout, orders, settings (each feature-isolated)
- `lib/shared/` — reusable widgets
- State management: BLoC/Cubit (`flutter_bloc`)
- DI registration in `lib/core/di/service_locator.dart`; swap to API repositories for backend integration
- **Design system mirrors the web `pede-aqui-delivery`, which is the visual source of truth.** Stacks are incompatible, so only tokens/visual rules are replicated, never CSS. Tokens live in `lib/core/constants/`: `app_colors.dart` (ember-orange primary, forest-green chrome, warm cream surfaces — hex equivalents of the web HSL vars), `app_spacing.dart` (`AppSpacing` + `AppRadii`), `app_shadows.dart` (`AppShadows.warm*`, brown-tinted to match the web `shadow-warm` utilities). Theme assembled in `lib/app/theme.dart`. Fonts bundled in `assets/fonts/`: **Fraunces** (serif — display/section headers) + **Plus Jakarta Sans** (body). No hardcoded colors/measures outside the token files. The web is light-only; the app's dark theme is a derived variant kept only for the settings toggle. Note: the web has since been restyled to a rose brand (`#e11d48`, see `pede-aqui-delivery/design.md`); the Flutter tokens still reflect the earlier ember-orange palette and haven't been re-synced.

**Courier app** (`pede_aqui_courier_app`):
- `lib/core/` — constants, DI (GetIt), network, providers, theme, utils
- `lib/data/` — datasources (mock + remote), models, repositories
- `lib/presentation/` — cubits, screens, widgets
- Currency: Metical (MZN / `MT`), locale: Portuguese (Portugal)
- Switch from `MockCourierDataSource` to `RemoteCourierDataSource` in `lib/core/di/injection.dart` for API integration

## Key API Flows

- **Product lifecycle**: products are created `PENDING` and only become visible after admin approval (`Product.approve()` → `ACTIVE`). Vendor search (`/search/vendors`) derives its vendor list from products with status `ACTIVE` — an empty storefront usually means no ACTIVE products, not a broken endpoint. Vendor name/rating in search results are currently pseudo-generated stubs (see `SearchService.createVendorResponse`).
- **Checkout**: cart → checkout (idempotency key) → mock payment → delivery record — all in one transaction.
- **Delivery completion**: courier submits 6-digit OTP from customer; invalid attempts counted but OTP never logged.
- **Vendor fulfillment state machine**: `PAYMENT_CONFIRMED → ACCEPTED_BY_VENDOR → PREPARING → READY_FOR_PICKUP`.
- **Courier delivery state machine**: `ACCEPTED → ARRIVED_AT_VENDOR → PICKED_UP → ON_ROUTE_TO_CUSTOMER → ARRIVED_AT_CUSTOMER → DELIVERED | FAILED_DELIVERY`.
- **Dispatch**: first eligible courier in `operatingZoneId` (APPROVED + online). Reassignment excludes prior courier.
- **Audit**: sensitive admin/ops/finance actions written to `audit_logs` (actor, target, action, result, timestamp).

## Architecture Constraints

The backend deliberately avoids DDD, Clean/Hexagonal Architecture, CQRS, event sourcing, command buses, and Kafka. Keep it in the simple layered model described in `docs/architecture.md`.

Every public class carries a short responsibility comment. Public service methods include short purpose comments. Business rules get comments only when non-obvious.
