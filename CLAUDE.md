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

Spring profiles: `dev` is the default (`spring.profiles.default`) and points storage at local MinIO — no AWS needed. `prod` (`SPRING_PROFILES_ACTIVE=prod`) targets real AWS S3 and fails fast if `AWS_S3_BUCKET`/`AWS_REGION` are missing. See the Storage section. `prod` also fails fast without `APP_DELIVERY_CODE_HMAC_SECRET` (delivery-PIN HMAC key; only `dev` has a default).

Secrets: `application.yml` has **no literal defaults** for `DB_PASSWORD`, `KEYCLOAK_ADMIN_CLIENT_SECRET` or `APP_DELIVERY_CODE_HMAC_SECRET` — the local values live only in `application-dev.yml`. In `prod`, `RequiredSecretsProperties` (`@Validated @NotBlank`, bound via `app.secrets.*` in `application-prod.yml`) aborts startup if any of them is blank, and every `DataSource` depends on it so the error surfaces before Flyway touches the DB. New secrets follow the same pattern (no default in base yml, dev default in `application-dev.yml`, field in `RequiredSecretsProperties`). `prod` also disables `/api-docs` and Swagger UI.

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
- `SecurityConfig` opens **for `GET` only**: `/actuator/health/**`, `/livez`, `/readyz`, `/api-docs/**`, `/swagger-ui/**`, and the public browse endpoints `/api/v1/catalog/categories`, `/api/v1/catalog/vendors/*`, `/api/v1/catalog/vendors/*/products`, `/api/v1/search/vendors`. The only anonymous writes are `POST /api/v1/register` and `POST /api/v1/customers/register`. Everything else requires a valid JWT — an anonymous POST/PUT/PATCH/DELETE is always 401 (LAC-02; guarded by `AnonymousWriteEndpointsApiTest`, which enumerates every mapped write endpoint — a new public write must be added to both `SecurityConfig` and that test's allow-list). Never add a `permitAll` matcher without an `HttpMethod`.
- Actuator: on the application port everything except health/probes (`metrics`, `prometheus`, `info`) requires `ADMIN`. When `MANAGEMENT_SERVER_PORT` is set (default `8091` in `prod`; compose sets it too) actuator moves to that internal port, where `/actuator/**` needs no token so Prometheus can scrape (`prometheus.yml` → `backend:8091`). That port must never be published by ingress/LB; on the public port probes are `/livez` and `/readyz`.
- Keycloak realm (`keycloak/delivery-realm.json`): `sslRequired: external`, brute-force protection on. `delivery-app` (backoffice) and `pede-aqui-web` (customer web) are public clients with PKCE S256 enforced for the code flow but **still allow ROPC** (`directAccessGrantsEnabled`), which both web apps use to log in. `pede-aqui-mobile` / `pede-aqui-courier-mobile` are code+PKCE only (no ROPC). The Flutter apps still call ROPC on `delivery-app` / `courier-app` (the latter is not in the realm) — migrating them and the web apps to PKCE is pending.
- Fine-grained access uses `@PreAuthorize` annotations in service or controller methods.
- Roles: `ADMIN`, `OPS`, `VENDOR_ADMIN`, `COURIER`, `CUSTOMER`, `FINANCE`, `SUPPORT`.

### Errors and correlation

- Every error body is an RFC 9457 problem document built by `ApiProblems` (`common/exception`): `type/title/status/detail/instance` plus `code` (stable lowercase snake_case — clients branch on it), optional `details`, `correlationId`, `traceId`, and the legacy `message`/`timestamp`/`fieldErrors` kept for a compatibility window. Contract and consumer analysis: `docs/api/ERROR-CONTRACT.md`.
- `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, so Spring's MVC exceptions map to proper 4xx (unknown path 404, 405, 415, malformed body/params 400) instead of the 500 catch-all. Throw `BusinessException(code, message, status)` for business errors; don't write JSON error bodies by hand — filters/interceptors that must answer directly use `ApiProblems.create(...)` (the security chain does, via `ProblemDetailSecurityHandlers`; so does `TenantRequestFilter` for `invalid_tenant_header`/`tenant_not_found`).
- `CorrelationIdFilter` runs first (`HIGHEST_PRECEDENCE`, before Spring Security): reuses an incoming `X-Correlation-Id` only if it matches `[A-Za-z0-9._:-]{1,128}`, else generates a UUID; puts it in MDC `correlationId` (logged by `logback-spring.xml`) and the response header (CORS-exposed).

### Tenant model

- Tenant-scoped records carry a `tenant_id` column. **Tenant = marketplace operator** (`docs/adr/ADR-0001-tenancy-model.md`); the Pede-Aqui operator tenant is seeded by `V045` with id `5e6d0c1a-7b2f-4f3e-9c8a-0d1e2f3a4b5c` (`app.tenancy.operator-tenant-id`, env `APP_OPERATOR_TENANT_ID`). Merchants are **vendors of the operator tenant**: `POST /api/v1/register` creates a `PENDING` vendor + `VENDOR_ADMIN` profile + `merchant_users` OWNER row in the operator tenant and never a tenant (ADR-0001 step 4); new tenants come only from the M2M provisioning endpoint (`MerchantRegistrationService.provisionTenant`) or the platform console. Legacy per-merchant dev tenants are folded in with `backend/scripts/adr-0001-consolidate-dev-tenants.sql` (dry-run first, approval required, never a Flyway migration).
- **Merchant scope** (`merchant_users`, V049): `vendor/api/MerchantScopeGuard` is the single resolver of the vendor a `VENDOR_ADMIN`/`VENDOR_STAFF` caller is confined to — `merchant_users` first (several associations → the optional `vendor_id` JWT claim picks one, else 403), then, only while `app.merchant-scope.single-vendor-fallback-enabled=true` (default), the legacy "claim, else the tenant's single vendor" heuristic. `SalesAccessGuard`, `CatalogAccessGuard`, `SalesReportService` and `OrderService` delegate to it; unresolved scope is always 403 `vendor_scope_unresolved`, a foreign vendor is 403 `vendor_access_denied`. Switch the flag off only when `backend/scripts/adr-0001-merchant-users-report.sql` §4 is 0 in that environment; ADR-0001 step 6 then deletes the fallback.
- `TenantContext.currentTenantId()` resolution (INC-11, SEC-06):
  - **platform admin** (`ADMIN`, no `tenant_id` claim; `isPlatformAdmin()`): the `X-Tenant-Id` header (impersonation). `TenantRequestFilter` rejects a malformed (400 `invalid_tenant_header`) or unknown (404 `tenant_not_found`) tenant and writes a `TENANT_IMPERSONATION` row to `audit_logs` for **every** request carrying the header.
  - **customer-only principal** (`CUSTOMER` and no staff/merchant/courier role; `isCustomerPrincipal()`): the tenant mapped to the token's OIDC client (`azp`) in `app.tenancy.customer-clients` (`pede-aqui-web`, `pede-aqui-mobile` → operator). Unmapped client, or a `tenant_id` claim that disagrees → no tenant. Never from the header.
  - **everyone else** (OPS, FINANCE, SUPPORT, VENDOR_*, COURIER, tenant-scoped ADMIN): the JWT `tenant_id` claim only. **`X-Tenant-Id` is ignored for anyone but the platform admin**; when an anonymous caller sends it, or a non-admin sends a tenant different from its own claim, `TenantRequestFilter` logs a `SECURITY ignored X-Tenant-Id …` WARN (method + path only, no PII).
  - No resolvable tenant → services answer 403 `tenant_required`. Startup fails if the operator tenant or a mapped customer tenant does not exist.
- Public storefront endpoints (categories, vendor products, vendor search) use `TenantContext.browseTenantId()`: customers browse like anonymous visitors (ACTIVE products, cross-tenant). Marked for removal (ADR-0001 step 6, `TODO(ADR-0001 step 6)` in `TenantContext`): once the dev tenants are consolidated and the fallback flag is off, browsing is confined to the tenant mapped to the client/host — see `docs/api/API-MIGRATION.md`.
- **Customer profile**: `CustomerProfileProvisioningService` creates the `AppUserProfile` (role `CUSTOMER`) idempotently (`INSERT … ON CONFLICT (tenant_id, keycloak_user_id) DO NOTHING`) at `POST /customers/register` (operator tenant) and, as a safety net, on every authenticated customer request (`TenantRequestFilter`). So register → login → cart → checkout works with no tenant header.
- Realm: seed `ops`/`finance`/`support` carry `tenant_id` = operator tenant; only `admin` is a platform admin. `tenant_id` is declared in the realm user profile with admin-only view/edit — Keycloak 26 otherwise silently drops it when set via the Admin REST API, and users must never be able to edit it.
- Service methods validate tenant context; repositories are plain Spring Data JPA interfaces.

### Database

- Migrations live in `backend/src/main/resources/db/migration/` as Flyway `V<NNN>__<name>.sql`. (`backend/backend/` is a stray build-artifact dir, not the module.)
- `ddl-auto: validate` — Hibernate never mutates schema; always create a migration for schema changes.
- PostGIS extension enabled (used for geo/zone features). `customer_addresses.location` is `geography(Point,4326)` (V046, INC-12 expand phase): the app still writes `latitude`/`longitude` and a DB trigger derives `location`; the contract step (drop lat/lng) is pending.

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
| `/catalogo` | ADMIN, VENDOR_ADMIN, OPS | Product catalog, SKUs, categories, families; product edit drawer (attributes/price/image) + OPS/ADMIN price-moderation queue |
| `/vendors` | ADMIN, VENDOR_ADMIN, OPS | Vendor management |
| `/users` | ADMIN | User profiles |
| `/orders` | ADMIN, VENDOR_ADMIN, OPS, SUPPORT | Order management |
| `/sales` | ADMIN, VENDOR_ADMIN, OPS, FINANCE, SUPPORT | Sales search/detail with cancel/refund/resend actions (RBAC-gated) |
| `/couriers` | ADMIN, OPS | Courier management |
| `/finance` | ADMIN, FINANCE | Settlements, earnings |
| `/reports` | ADMIN, VENDOR_ADMIN, OPS, FINANCE | Sales reports + CSV export; VENDOR_ADMIN scoped to own vendor |
| `/support` | ADMIN, SUPPORT | Ticket management |
| `/marketing` | ADMIN, OPS, VENDOR_ADMIN | Campaigns and promotions (tenant-wide promotions are OPS/ADMIN only) |
| `/screens` | all | HTML prototype viewer (not real features) |

### Auth flow

Login page calls Keycloak token endpoint with `grant_type=password`. Token stored in `sessionStorage` as `auth_token` and also set as a cookie. `AppProviders` rehydrates auth state on page load by calling `authService.getMe()`. Root page redirects to `/platform` for platform super-admins or `/` (dashboard) for tenant users.

Platform super-admins can "enter" a tenant (stored in `activeTenantId` Redux state) to operate on its behalf; `X-Tenant-Id` header is sent automatically. The backend honours (and audits) that header only for the platform admin; for tenant users the client sends their own JWT tenant, which the backend ignores in favour of the claim.

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
| `/checkout` | Yes | Checkout flow: pick a saved address or create one (`/customers/me/addresses`), then `POST /checkout` with `addressId` |
| `/orders` | Yes | Order history |
| `/orders/:orderId` | Yes | Order detail with status polling (30 s) |
| `/orders/:orderId/confirmation` | Yes | Post-checkout confirmation |
| `/profile` | No (shows auth state) | Customer profile |

### Auth flow

Two token paths coexist in `auth-slice.ts`:
- **OIDC PKCE** via `oidc-client-ts` (`userManager`) — for future use
- **ROPC** (primary): `keycloakService.login()` calls Keycloak's token endpoint directly with `grant_type=password`, stores the JWT as `auth_token` in `sessionStorage`. `initAuth` checks `userManager.getUser()` first, then falls back to parsing `auth_token` from `sessionStorage`.

Customer registration hits the backend at `POST /api/v1/customers/register` (open endpoint), which creates a Keycloak user via `KeycloakAdminService.createCustomer()`, assigns the `CUSTOMER` role and provisions the customer profile in the operator tenant. The web client never sends `X-Tenant-Id`: the backend derives the customer's tenant from the `pede-aqui-web` client (`azp`).

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

- **Product lifecycle**: products are created `PENDING` and only become visible after admin approval (`Product.approve()` → `ACTIVE`). Approve/reject is OPS/ADMIN only and audited (`PRODUCT_APPROVED`/`PRODUCT_REJECTED`). `CartService.addItem` refuses a SKU whose product is not `ACTIVE` (409 `product_not_available`) or an inactive SKU (409 `sku_inactive`).
- **Catalog/stock authorization (INC-05)**: product/SKU create, variation group/option writes and category CRUD require VENDOR_ADMIN/OPS/ADMIN; VENDOR_ADMIN/VENDOR_STAFF are confined to their own vendor via `CatalogAccessGuard` → `MerchantScopeGuard` (product/SKU/variation resolve to the product's vendor; `createProduct` also requires the vendor to exist in the tenant). Category CRUD stays open to VENDOR_ADMIN because `/catalogo/categorias` exposes it (categories are tenant-wide; with tenant = operator this means merchants share them — revisit). `PATCH /inventory/{id}/stock` requires VENDOR_ADMIN/VENDOR_STAFF of the owning vendor, OPS or ADMIN, is tenant-scoped (other tenant → 404) and audited (`INVENTORY_STOCK_UPDATED`). Vendor search (`/search/vendors`) derives its vendor list from products with status `ACTIVE` — an empty storefront usually means no ACTIVE products, not a broken endpoint. Vendor name/rating in search results are currently pseudo-generated stubs (see `SearchService.createVendorResponse`).
- **Pricing (INC-13)**: one engine, `cart/service/PricingService`, prices cart, checkout, `GET /cart/pricing` and vendor search (`common/pricing/*` value types). Inputs: the tenant's `fee_policies` row (delivery base fee, flat `service_fee` + `service_fee_rate` × subtotal, `tax_rate` × subtotal; `version` = `@Version`) and the vendor's active `delivery_fee_rules` bands, matched on a server-side PostGIS straight-line distance (`VendorRepository.straightLineDistancesKm`, vendor lat/lng → address snapshot / search position). Fallbacks, never zero for a non-empty cart: no coordinates on either side or no rules → `POLICY_BASE` (fee_policies.delivery_fee); tenant without a policy → `PLATFORM_DEFAULT` (5.00 / 5 % / 8 %, same as the V048 seed). Bands are inclusive; gaps/beyond the last band use the nearest lower band. Cart fees are an address-less estimate; checkout re-quotes with the address and stores the applied quote in immutable `orders.quote_*` (V048: amounts, rates, basis, distance, policy/rule ids + versions). `GET /api/v1/checkout/quote?cartId&addressId` returns exactly what checkout will charge. Money is `BigDecimal` only (ADR-0003); the `MVP10` magic coupon is gone — coupons/promotions are spec-002 (`POST /cart/{id}/coupon`, `CheckoutDiscountResolver`), applied on top of the quote.
- **Checkout**: cart → checkout (idempotency key) → mock payment → delivery record — all in one transaction (`OrderService.checkout` drives it with a `TransactionTemplate`, so it is deliberately not `@Transactional`). The order starts `PAYMENT_PENDING` with a `PENDING_CONFIRMATION` payment. Idempotency (INC-07): the cart row is locked `FOR UPDATE` and the key re-read, so duplicate submits return the first order (**201 on create, 200 on replay**); a concurrent loser that hits `uq_orders_tenant_checkout_key` rolls back and re-reads the winner in a fresh transaction. The key is bound to customer + cart (`orders.cart_id`): reuse with another cart/customer → 409 `idempotency_key_reused`. Each line is revalidated at checkout — inactive SKU → 409 `sku_inactive`, non-ACTIVE product → 409 `product_not_available`, price ≠ cart snapshot → 409 `price_changed` with `details.items[{skuId, previousUnitPrice, currentUnitPrice}]`; on `price_changed` the cart is re-priced so the customer reviews and resubmits. **Delivery address (INC-12)**: the request carries `addressId` (a saved address of the caller, managed via `GET/POST/PUT/DELETE /customers/me/addresses`; another customer's → 404); it is copied into an immutable `orders.delivery_*` snapshot (V047, `DeliveryAddressSnapshot`, insert-only + DB trigger). Requests without `addressId` are a deprecated legacy shape accepted while `app.checkout.legacy-without-address-enabled=true` (response `Deprecation: true`, no snapshot); `false` → 400 `address_required` — window in `docs/api/API-MIGRATION.md`. Address exposure: the order's customer gets `OrderResponse.deliveryAddress`; the assigned courier reads `GET /deliveries/{id}/address` only from `ACCEPTED` to `ARRIVED_AT_CUSTOMER`; vendors/ops get only `AdminOrderResponse.deliveryArea` (city).
- **Stock lifecycle (INC-07)**: `reserve` moves units `available → reserved` at checkout. `orders.stock_status` (`RESERVED → RELEASED | COMMITTED`) makes the rest exactly-once via `OrderStockService`: release on sales cancel / override-cancel / vendor rejection / failed delivery before pickup, and on a **full refund approval before pickup** (`PaymentService.approveRefund` when the payment reaches `FULLY_REFUNDED`: released unless the goods left the store — delivery in `DeliveryStatus.isGoodsLeftTheStore()` = `PICKED_UP`/`ON_ROUTE_TO_CUSTOMER`/`ARRIVED_AT_CUSTOMER`/`DELIVERED`/`FAILED_DELIVERY`, or order status `PICKED_UP`/`DELIVERING`/`DELIVERED` for rows without a delivery; same classification as the V043 data fix; an engaged-but-not-picked-up courier does not block the release — that delivery is retired, see Delivery completion); commit (drains `reserved` only — on-hand = available + reserved) on courier `PICKED_UP`, on PIN completion (`DeliveryService.complete`) because the courier app may complete straight from `ACCEPTED` without a `PICKED_UP` PATCH, and on the ops status override `DELIVERING → DELIVERED` (`SalesService.statusOverride`) — all no-ops if already committed. Inventory rows are locked `FOR UPDATE` in SKU order. Unhandled `DataIntegrityViolationException` → 409 `conflict`, `@Version` conflicts → 409 `concurrent_modification` (no constraint names exposed).
- **Mock payment confirmation** (`POST /payments/{id}/confirm`, body `{ idempotencyKey }` = the payment's key, i.e. the checkout key) is a **development-only** feature gated by `app.payments.mock-confirmation.enabled` (`false` in `application.yml`, `true` only in `application-dev.yml`). Disabled → `404` for every caller. Enabled → only the customer who owns the order (other customers get `404`) or `OPS`/`ADMIN` (other roles `403`); only a `PENDING_CONFIRMATION` payment of a `PAYMENT_PENDING` order advances (else `409 payment_not_confirmable`), and a repeat returns the same confirmed result without a second audit log. The endpoint deliberately has no `@PreAuthorize` (so "disabled" can be 404 for all roles) — checks live in `PaymentService.confirm`. Production confirmation must come only from a PSP webhook/status query (FR-030/FR-032, not built yet).
- **Delivery completion** (`POST /deliveries/{id}/complete`, `DeliveryService.complete`): only the **assigned courier** (caller's profile → courier in tenant must equal `delivery.courierId`; OPS/ADMIN cannot complete with the PIN) submits the customer's 6-digit PIN, and only from `ARRIVED_AT_CUSTOMER` or `ACCEPTED` (the courier app posts `/complete` straight after accepting the job — narrow to `ARRIVED_AT_CUSTOMER` once the app PATCHes intermediate statuses). Already `DELIVERED` → 409, other states → 409. **The order must still be deliverable**: if it is `REFUNDED`, `CANCELLED` or legacy `REJECTED_BY_VENDOR`, both `/complete` and `PATCH /status` (courier and OPS/ADMIN) answer 409 `order_not_deliverable` — checked after ownership (403/404) and the delivery-state 409s, before the lockout (423) and the PIN compare, so a refused call has no side effect (no counter, event, audit or stock movement). `REFUND_PENDING` is not in the set (never written; a pending refund may still be rejected). A wrong PIN commits only the attempt counter + `CONFIRMATION_FAILED` event (`InvalidDeliveryCodeException`, `noRollbackFor`); at `app.delivery.max-code-attempts` (default 5) it locks: 423 `delivery_code_locked`, `CONFIRMATION_LOCKED` event, audit `DELIVERY_CODE_LOCKED`. OPS/ADMIN unlock via `POST /ops/deliveries/{id}/unlock-code` (audited). PIN hash is HMAC-SHA256 (`app.delivery.code-hmac-secret`), constant-time compare; rows created before V038 are flagged `confirmation_code_legacy` and also accept the old unsalted SHA-256. PINs are not unique per tenant (V037 dropped that constraint). The plain PIN (`orders.delivery_confirmation_code_display`, shown to the customer while active) is nulled when the order becomes `DELIVERED`/`CANCELLED` (incl. failed delivery and the ops status override to `DELIVERED`) and `REFUNDED` (always — no courier can complete a refunded order any more, so `Order.markRefunded()` takes no "courier engaged" flag). PIN never logged. **Delivery retirement** (`DeliveryRetirementService.retireBeforePickup`, runs in the caller's transaction after the payment lock and order read, before inventory): a full refund (`PaymentService.approveRefund` → `FULLY_REFUNDED`), a sales cancel, an override to `CANCELLED` and a seller rejection move a delivery that is still before pickup (`DeliveryStatus.isRetirableBeforePickup()` = not `CANCELLED` and not `isGoodsLeftTheStore()`, i.e. `DISPATCH_PENDING`/`ASSIGNED`/`ACCEPTED`/`REJECTED`/`ARRIVED_AT_VENDOR`/`REASSIGNED`) to `CANCELLED`, cancel its active dispatch job (`DispatchService.ACTIVE_JOB_STATUSES` → `DispatchJobStatus.CANCELLED`, which frees the courier and the V035 slots) and write a `RETIRED` delivery event; idempotent. From pickup on the delivery is left alone (goods are out, stock committed) and the guard above stops the courier; ops resolve it manually (there is no ops delivery-cancel endpoint yet). A concurrent courier PATCH on the same delivery loses on the delivery `@Version` (409 `concurrent_modification`).
- **Vendor fulfillment state machine**: `PAYMENT_CONFIRMED → ACCEPTED_BY_VENDOR → PREPARING → READY_FOR_PICKUP`. **Seller rejection is unified (one path)**: `PATCH /vendor/orders/{id}/reject` (from `PAYMENT_CONFIRMED`/`ACCEPTED_BY_VENDOR`; vendor users scoped to their own vendor → 404 otherwise; other statuses → 400) locks the payment row first (`findByTenantIdAndOrderIdForUpdate`), then in one transaction sets the order `CANCELLED` with `orders.cancellation_reason = SELLER_REJECTED` (V044) and the vendor text in `rejection_reason`, releases stock (INC-07), clears the plain PIN, requests a refund of only the eligible remainder `payment.amount − Σ non-REJECTED refunds` (the full amount when nothing was refunded before; **no refund row when nothing remains**; key `seller-reject-<orderId>`, `PaymentService.requestRemainingRefund`), notifies the customer (`order_rejected`) and audits `ORDER_CANCELLED_SELLER_REJECTED` with business reference `refund_remaining=<amount>` — any failure rolls back everything. The financial state (`PARTIALLY_REFUNDED`/`FULLY_REFUNDED`) lives on the payment. Idempotent: a repeat or concurrent rejection, or a rejection of an order already `CANCELLED` by another path (ops cancel) or in the **legacy** `REJECTED_BY_VENDOR` status (rows written before the unification, with a `vendor-rejection-<orderId>` refund), returns the current order with no new refund, audit or notification. `OrderStatus.REJECTED_BY_VENDOR` is never written any more (kept for existing rows); `OrderResponse`/`AdminOrderResponse` expose `cancellationReason` so clients can still tell a seller rejection from other cancellations. The vendor dashboard "rejected" counter and the reports `cancelledCount` count `CANCELLED + SELLER_REJECTED` plus legacy `REJECTED_BY_VENDOR` rows. There is no order-status event table; the audit entry + customer notification are the trace.
- **Vendor order scoping**: `OrderService` reuses `sales/service/SalesAccessGuard` (a facade over `vendor/api/MerchantScopeGuard`). Merchant users (`VENDOR_ADMIN`/`VENDOR_STAFF` without `ADMIN`/`OPS`/`FINANCE`) only list and transition their own vendor's orders; another vendor's order answers 404. The vendor comes from `merchant_users` (both roles); if nothing resolves the call fails closed with 403 `vendor_scope_unresolved`. `GET /orders` keeps its array contract; `GET /orders/page?page=&size=` is the paginated variant (max 100) — see `docs/api/API-MIGRATION.md`.
- **Courier delivery state machine**: `ACCEPTED → ARRIVED_AT_VENDOR → PICKED_UP → ON_ROUTE_TO_CUSTOMER → ARRIVED_AT_CUSTOMER → DELIVERED | FAILED_DELIVERY`. `PATCH /deliveries/{id}/status` is limited to the assigned courier or OPS/ADMIN (audited `DELIVERY_STATUS_UPDATED`), answers 409 on invalid transitions, and cannot reach `DELIVERED` (PIN required via `/complete`). Refused with 409 `order_not_deliverable` once the order is `REFUNDED`/`CANCELLED` (see Delivery completion). `FAILED_DELIVERY` cancels the order and releases its stock if still `RESERVED` (in practice it is reachable only from `ARRIVED_AT_CUSTOMER`, after pickup committed the stock, so the release is a no-op; no refund yet).
- **Dispatch**: only `READY_FOR_PICKUP` orders; offered to the first eligible courier in `operatingZoneId` (APPROVED + online) **without an active delivery**. Reassignment excludes the prior courier. Offers expire after `app.dispatch.offer-ttl` (default 60s) — expiry only blocks acceptance, there is no automatic re-offer job yet. Only the addressed courier can accept/reject (others get 404); accept is a conditional UPDATE so concurrent accepts have one winner (409 for the rest). Exclusivity is enforced in the DB by V035 partial unique indexes (one active job per delivery: `ASSIGNED|ACCEPTED|REASSIGNABLE`; one active delivery per courier: `ASSIGNED..ARRIVED_AT_CUSTOMER`) — keep `DispatchService.ACTIVE_JOB_STATUSES` / `COURIER_BUSY_DELIVERY_STATUSES` in sync with them. Job lists are tenant-scoped, paged (`page`/`size`, max 100) and courier-scoped for COURIER.
- **Refunds (no PSP)**: `PaymentService.requestRefund` → `REQUESTED`; `approveRefund`/`rejectRefund` (FINANCE/ADMIN, reachable via `/payments/refunds/*` and `/finance/refunds/*`, both funnel through `PaymentService`). **Approval is an administrative record only — no money is returned to the customer; there is no PSP call.** Lookups are tenant-scoped (another tenant's refund → 404). A request locks the payment row (`SELECT … FOR UPDATE`) and must satisfy `0 < amount ≤ payment.amount − Σ non-REJECTED refunds` (else 422); idempotency key is UNIQUE per tenant (V040) — same key returns the same refund, a key bound to another payment → 409. Financial state lives on the **payment**, independent of the order: each approval sets `Payment.status` to `PARTIALLY_REFUNDED` (approved < amount) or `FULLY_REFUNDED` (approved ≥ amount; the legacy `PaymentStatus.REFUNDED` value is never written). On full coverage the order becomes `REFUNDED` — **except a `CANCELLED` order, which stays `CANCELLED`**; a partial approval leaves the order status unchanged. Full coverage also reads the order's single delivery row (after the payment lock and the order read, before inventory) and closes what it allows: always clears the plain PIN (non-`CANCELLED` orders), and unless the goods left the store retires the delivery + its active dispatch job (see Delivery completion) and releases the stock reservation (see Stock lifecycle) — all idempotent, so an order cancelled earlier is neither retired nor released twice. After pickup the delivery is untouched; the courier then gets 409 `order_not_deliverable` on the `REFUNDED` order. Finance/dashboard gross totals sum `PaymentStatus.CAPTURED` (`CONFIRMED`, `PARTIALLY_REFUNDED`, `FULLY_REFUNDED`) and subtract refunds separately. **PSP seam (not built):** refunds are created `REQUESTED` inside the business transaction; a future PSP payout must run **after commit**, be idempotent by the refund's idempotency key, and record failures on the refund in a retryable state — a PSP failure must never roll back the order cancellation. `RefundStatus` has no `FAILED` value yet; add it with the PSP integration.
- **Lock order rule (order money paths)**: any transaction that may change an order's terminal state or its refunds takes the order's **payment row `FOR UPDATE` first** (`PaymentRepository.findByTenantIdAndOrderIdForUpdate`), **then** reads the order, then the delivery/dispatch rows it retires, and only then touches inventory rows (SKU order, see Stock lifecycle). `OrderService.rejectByVendor` and `SalesService.cancel/refund/statusOverride` all follow it (`SalesService.lockPayment`), so concurrent reject/cancel/refund calls on one order serialize and the loser sees the committed state instead of racing or deadlocking on the order row; `PaymentService.requestRefund`/`approveRefund` re-lock the same row inside the same transaction, which is re-entrant. Orders without a payment yet (pre-checkout states) have nothing to lock. When adding a new path that cancels, refunds or overrides an order, start it with the same lock.
- **Audit**: sensitive admin/ops/finance actions written to `audit_logs` (actor, target, action, result, timestamp).

## Architecture Constraints

The backend is a layered modular monolith. Each top-level package under `com.delivery` is a Spring Modulith application module that keeps the `controller/service/repository/entity/dto/mapper` layering internally. Spring Modulith is used **only** for structural verification (`ApplicationModules.verify()` in CI) and transactional application events (`@ApplicationModuleListener` with the JPA event publication registry). Modules talk to each other through their public API (base package or `@NamedInterface` `api` package) or events — never through another module's repositories or entities, and without cycles. Still avoided: DDD tactical patterns, Clean/Hexagonal Architecture, CQRS, event sourcing, command buses, and Kafka/event externalization. See `docs/adr/ADR-0002-module-structure.md`.

Every public class carries a short responsibility comment. Public service methods include short purpose comments. Business rules get comments only when non-obvious.
