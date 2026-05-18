# Tasks: Delivery Marketplace MVP

**Input**: Design documents from `/specs/001-delivery-marketplace-mvp/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`

**Tests**: Required for critical business logic, tenant isolation, authorization, order state transitions, payment idempotency, delivery confirmation, support visibility, finance summaries, dashboards, and MVP happy path.

**Architecture Guardrails**: Use only simple layered packages: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config`, `exception`, `security`. Do not create DDD, Clean Architecture, Hexagonal Architecture, ports/adapters, use cases, command/query buses, CQRS, event sourcing, Kafka tasks, or speculative interfaces.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it touches different files and does not depend on incomplete tasks.
- **[Story]**: Maps a task to a user story phase, such as `[US1]`.
- **AC**: Acceptance criteria are included in every task description.

## Path Conventions

- **Backend**: `backend/src/main/java/com/delivery/[feature]/`, `backend/src/main/resources/db/migration/`, `backend/src/test/java/com/delivery/`
- **Web**: `web/src/app/`, `web/src/features/`, `web/src/services/`
- **Mobile**: `mobile/lib/core/`, `mobile/lib/features/`, `mobile/test/`
- **Docs**: `README.md`, `docs/`, `specs/001-delivery-marketplace-mvp/`

## Requested Phase Coverage

The user-requested phases 0-20 are covered as follows: setup covers Phase 0; foundational covers Phases 1-3; US1 covers customer discovery, catalog, cart, pricing, checkout, payment, notifications, tracking, and customer mobile screens; US2 covers vendor onboarding, catalog, inventory, and vendor fulfillment; US3 covers courier, dispatch, delivery confirmation, and courier mobile screens; US4 covers admin and operations; US5 covers finance and reconciliation; US6 covers support; polish covers production hardening and final validation.

## Phase 1: Setup (Monorepo and Local Environment)

**Purpose**: Create the repository skeleton, local infrastructure, baseline tooling, and documentation needed by all vertical slices.

- [X] T001 Create monorepo directories `backend/`, `web/`, `mobile/`, `docs/`, and `.github/workflows/`; AC: all directories exist and match `specs/001-delivery-marketplace-mvp/plan.md` structure.
- [X] T002 Create Spring Boot Maven project in `backend/pom.xml` and `backend/src/main/java/com/delivery/DeliveryApplication.java`; AC: `cd backend && mvn -q -DskipTests package` starts compilation with Java 21 settings.
- [X] T003 [P] Create Next.js TypeScript project files in `web/package.json`, `web/tsconfig.json`, `web/src/app/layout.tsx`, and `web/src/app/page.tsx`; AC: `cd web && npm run build` is defined and the default page renders.
- [X] T004 [P] Create Flutter app structure in `mobile/pubspec.yaml`, `mobile/lib/main.dart`, `mobile/lib/core/`, and `mobile/lib/features/`; AC: `cd mobile && flutter analyze` can run after dependencies are fetched.
- [X] T005 Create Docker Compose services in `docker-compose.yml` for PostgreSQL/PostGIS, Redis, Keycloak, MinIO, and backend; AC: `docker compose config` validates the file.
- [X] T006 [P] Add root README setup instructions in `README.md`; AC: README documents prerequisites, local services, backend, web, mobile, and validation commands.
- [X] T007 [P] Add basic CI pipeline in `.github/workflows/ci.yml`; AC: pipeline includes backend `mvn clean verify`, web `npm run lint` and `npm run build`, and mobile `flutter analyze` and `flutter test` jobs.
- [X] T008 [P] Add formatting and linting configuration in `backend/pom.xml`, `web/package.json`, `web/eslint.config.mjs`, and `mobile/analysis_options.yaml`; AC: each project has an executable lint or formatting check.
- [X] T009 [P] Add local development guide in `docs/local-development.md`; AC: document explains Docker Compose, Keycloak, PostgreSQL, Redis, MinIO, and common troubleshooting.

## Phase 2: Foundational (Backend Foundation, Security, Tenant Base)

**Purpose**: Blocking backend infrastructure that must be complete before user stories are implemented.

**Checkpoint**: No user story work starts until this phase is complete.

- [X] T010 Create backend package folders under `backend/src/main/java/com/delivery/` for `common`, `auth`, `tenant`, `vendor`, `catalog`, `inventory`, `geo`, `cart`, `order`, `payment`, `dispatch`, `delivery`, `notification`, `support`, `finance`, and `dashboard`; AC: only simple layered subfolders are created where needed.
- [X] T011 Configure application settings in `backend/src/main/resources/application.yml`; AC: profiles exist for local and test with PostgreSQL, Keycloak issuer, Flyway, logging, and OpenAPI settings.
- [X] T012 [P] Implement common error response DTO in `backend/src/main/java/com/delivery/common/dto/ErrorResponse.java`; AC: public class has a responsibility comment and exposes code, message, field errors, and correlation ID.
- [X] T013 [P] Implement centralized exception handling in `backend/src/main/java/com/delivery/common/exception/GlobalExceptionHandler.java`; AC: validation, authorization, not found, conflict, and generic errors return consistent responses without stack traces.
- [X] T014 [P] Add validation support examples in `backend/src/main/java/com/delivery/common/exception/ValidationError.java`; AC: invalid request DTO fields are returned in `ErrorResponse` field errors.
- [X] T015 [P] Configure OpenAPI in `backend/src/main/java/com/delivery/common/config/OpenApiConfig.java`; AC: Swagger metadata includes API title, version, JWT security scheme, and `/api/v1` base path.
- [X] T016 [P] Implement correlation ID filter in `backend/src/main/java/com/delivery/common/config/CorrelationIdFilter.java`; AC: incoming or generated correlation ID is added to response headers and logging context.
- [X] T017 [P] Configure structured logging in `backend/src/main/resources/logback-spring.xml`; AC: logs include timestamp, level, logger, message, and correlation ID.
- [X] T018 Add Flyway baseline migration in `backend/src/main/resources/db/migration/V001__baseline.sql`; AC: migration enables UUID support, creates no placeholder-only tables, and runs on app startup.
- [X] T019 Configure PostgreSQL and Testcontainers in `backend/src/test/java/com/delivery/common/PostgresTestContainerConfig.java`; AC: repository integration tests can reuse a PostgreSQL/PostGIS container.
- [X] T020 [P] Add health and metrics dependencies and config in `backend/pom.xml` and `backend/src/main/resources/application.yml`; AC: Actuator health endpoint is available locally.
- [X] T021 [P] Configure local Keycloak realm documentation in `docs/local-development.md`; AC: roles `CUSTOMER`, `VENDOR_ADMIN`, `VENDOR_STAFF`, `COURIER`, `ADMIN`, `OPS`, `FINANCE`, and `SUPPORT` are documented.
- [X] T022 Implement Spring Security resource server config in `backend/src/main/java/com/delivery/common/security/SecurityConfig.java`; AC: unauthenticated requests are rejected except documented public health/OpenAPI endpoints.
- [X] T023 Implement JWT role mapping in `backend/src/main/java/com/delivery/common/security/JwtRoleConverter.java`; AC: Keycloak roles map to Spring authorities with `ROLE_` prefix.
- [X] T024 Implement tenant context helper in `backend/src/main/java/com/delivery/common/security/TenantContext.java`; AC: authenticated tenant and user identifiers are readable by services without global mutable state.
- [X] T025 Create `AppUserProfile` entity in `backend/src/main/java/com/delivery/auth/entity/AppUserProfile.java`; AC: UUID ID, tenant ID, Keycloak user ID, email, display name, phone, roles, status, timestamps, and public class comment exist.
- [X] T026 [P] Create user profile DTOs in `backend/src/main/java/com/delivery/auth/dto/MeResponse.java` and `UserProfileRequest.java`; AC: DTOs use validation annotations and do not expose entities.
- [X] T027 Create user profile repository in `backend/src/main/java/com/delivery/auth/repository/AppUserProfileRepository.java`; AC: repository extends Spring Data JPA and includes queries by tenant and Keycloak user ID.
- [X] T028 Create user profile service in `backend/src/main/java/com/delivery/auth/service/AppUserProfileService.java`; AC: public methods have comments and enforce tenant/user access for `/me`.
- [X] T029 Create `/api/v1/me` endpoint in `backend/src/main/java/com/delivery/auth/controller/MeController.java`; AC: authenticated user receives profile and roles from DTO response.
- [X] T030 [P] Add unauthorized/forbidden API tests in `backend/src/test/java/com/delivery/auth/AuthSecurityApiTest.java`; AC: missing token returns 401 and wrong role returns 403.
- [X] T031 Create `Tenant` entity in `backend/src/main/java/com/delivery/tenant/entity/Tenant.java`; AC: UUID ID, name, slug, status, default currency, timestamps, and class comment exist.
- [X] T032 [P] Create tenant DTOs and mapper in `backend/src/main/java/com/delivery/tenant/dto/` and `backend/src/main/java/com/delivery/tenant/mapper/TenantMapper.java`; AC: create/update/list responses are entity-free.
- [X] T033 Create tenant repository in `backend/src/main/java/com/delivery/tenant/repository/TenantRepository.java`; AC: repository supports lookup by slug and active status.
- [X] T034 Create tenant service in `backend/src/main/java/com/delivery/tenant/service/TenantService.java`; AC: service methods have comments and validate tenant status before use.
- [X] T035 Create tenant controller in `backend/src/main/java/com/delivery/tenant/controller/TenantController.java`; AC: admin-only endpoints support create, list, get, and update tenant status.
- [X] T036 Add tenant and user migration in `backend/src/main/resources/db/migration/V002__tenants_and_users.sql`; AC: `tenants` and `app_user_profiles` include UUID PKs, indexes, timestamps, constraints, and tenant ID where scoped.
- [X] T037 [P] Add tenant access tests in `backend/src/test/java/com/delivery/tenant/TenantAccessTest.java`; AC: cross-tenant access attempts are denied at service/API level.
- [X] T038 [P] Document tenant model in `docs/architecture.md`; AC: tenant ownership, service checks, and scoped data rules are documented.

## Phase 3: User Story 1 - Customer Completes an Order (Priority: P1)

**Goal**: Customer can discover vendors, browse products, manage a single-vendor cart, checkout, pay with mock confirmation, track status, view delivery code, and complete delivery through the courier.

**Independent Test**: Create seeded tenant/vendor/catalog/stock/customer, search vendor, add products to cart, checkout with idempotency key, confirm payment, move fulfillment statuses, and complete delivery using the correct 6-digit code.

### Tests for User Story 1

- [X] T039 [P] [US1] Add cart business rule unit tests in `backend/src/test/java/com/delivery/cart/CartServiceTest.java`; AC: one-vendor rule, invalid quantity, and tenant mismatch tests fail before implementation.
- [X] T040 [P] [US1] Add pricing unit tests in `backend/src/test/java/com/delivery/cart/PricingServiceTest.java`; AC: delivery fee, service fee, tax, discount total, and rounding cases are covered.
- [ ] T041 [P] [US1] Add checkout API tests in `backend/src/test/java/com/delivery/order/CheckoutApiTest.java`; AC: successful checkout, duplicate idempotency key, invalid cart, out-of-stock, fuel-blocking, and pharmacy validation cases are covered.
- [X] T042 [P] [US1] Add payment idempotency tests in `backend/src/test/java/com/delivery/payment/PaymentServiceTest.java`; AC: duplicate confirmation returns one final payment result.
- [X] T043 [P] [US1] Add delivery code tests in `backend/src/test/java/com/delivery/delivery/DeliveryConfirmationTest.java`; AC: correct code delivers and incorrect code is rejected without logging the code.

### Implementation for User Story 1

- [X] T044 [P] [US1] Create category/product/SKU entities in `backend/src/main/java/com/delivery/catalog/entity/Category.java`, `Product.java`, and `Sku.java`; AC: UUID IDs, tenant IDs, timestamps, product restrictions, and public class comments exist.
- [X] T045 [P] [US1] Create inventory entity in `backend/src/main/java/com/delivery/inventory/entity/InventoryItem.java`; AC: quantity fields, version column, tenant/vendor/SKU links, and negative stock protection fields exist.
- [X] T046 [P] [US1] Create customer address entity in `backend/src/main/java/com/delivery/cart/entity/CustomerAddress.java`; AC: tenant/customer fields, address fields, optional location, delivery instructions, and timestamps exist.
- [X] T047 [P] [US1] Create cart entities in `backend/src/main/java/com/delivery/cart/entity/Cart.java` and `CartItem.java`; AC: cart stores one vendor, fulfillment type, totals, version, and items with price snapshots.
- [X] T048 [P] [US1] Create order entities and status enum in `backend/src/main/java/com/delivery/order/entity/Order.java`, `OrderItem.java`, and `OrderStatus.java`; AC: lifecycle states match spec and order has unique reference field.
- [X] T049 [P] [US1] Create payment/refund entities and enums in `backend/src/main/java/com/delivery/payment/entity/Payment.java`, `Refund.java`, `PaymentStatus.java`, and `RefundStatus.java`; AC: idempotency key, status, amount, timestamps, and version are present.
- [X] T050 [P] [US1] Create delivery entities and enums in `backend/src/main/java/com/delivery/delivery/entity/Delivery.java`, `DeliveryEvent.java`, and `DeliveryStatus.java`; AC: confirmation code hash, attempts, proof metadata, cash collected, status, and version are present.
- [X] T051 [US1] Add catalog, inventory, cart, order, payment, and delivery migration in `backend/src/main/resources/db/migration/V003__customer_order_flow.sql`; AC: tables have UUID PKs, tenant IDs, indexes, constraints, and version columns for concurrent records.
- [ ] T052 [P] [US1] Create catalog DTOs and mappers in `backend/src/main/java/com/delivery/catalog/dto/` and `backend/src/main/java/com/delivery/catalog/mapper/CatalogMapper.java`; AC: DTOs include validation annotations and no API exposes entities.
- [X] T053 [P] [US1] Create inventory DTOs and mapper in `backend/src/main/java/com/delivery/inventory/dto/` and `backend/src/main/java/com/delivery/inventory/mapper/InventoryMapper.java`; AC: update requests validate non-negative quantities.
- [ ] T054 [P] [US1] Create cart DTOs and mapper in `backend/src/main/java/com/delivery/cart/dto/` and `backend/src/main/java/com/delivery/cart/mapper/CartMapper.java`; AC: add item, update quantity, pricing, and cart response DTOs are defined.
- [ ] T055 [P] [US1] Create order DTOs and mapper in `backend/src/main/java/com/delivery/order/dto/` and `backend/src/main/java/com/delivery/order/mapper/OrderMapper.java`; AC: checkout request, order response, tracking response, and status update DTOs are defined.
- [X] T056 [P] [US1] Create payment DTOs and mapper in `backend/src/main/java/com/delivery/payment/dto/` and `backend/src/main/java/com/delivery/payment/mapper/PaymentMapper.java`; AC: confirm payment, refund request, and payment response DTOs are defined.
- [X] T057 [P] [US1] Create delivery DTOs and mapper in `backend/src/main/java/com/delivery/delivery/dto/` and `backend/src/main/java/com/delivery/delivery/mapper/DeliveryMapper.java`; AC: delivery status, completion code, and tracking DTOs are defined.
- [X] T058 [P] [US1] Create catalog and inventory repositories in `backend/src/main/java/com/delivery/catalog/repository/` and `backend/src/main/java/com/delivery/inventory/repository/`; AC: query methods include tenant/vendor/category/SKU filters and no wrapper classes.
- [ ] T059 [P] [US1] Create cart/order/payment/delivery repositories in `backend/src/main/java/com/delivery/cart/repository/`, `order/repository/`, `payment/repository/`, and `delivery/repository/`; AC: query methods support tenant-scoped lookups, idempotency keys, and status filters.
- [X] T060 [US1] Implement `CatalogService` in `backend/src/main/java/com/delivery/catalog/service/CatalogService.java`; AC: public methods have comments, block prohibited fuel, and validate pharmacy product flags.
- [X] T061 [US1] Implement `InventoryService` in `backend/src/main/java/com/delivery/inventory/service/InventoryService.java`; AC: stock cannot go negative and reservation uses optimistic locking.
- [X] T062 [US1] Implement `CartService` in `backend/src/main/java/com/delivery/cart/service/CartService.java`; AC: one vendor per cart, tenant checks, item availability, and clear validation errors work.
- [X] T063 [US1] Implement `PricingService` in `backend/src/main/java/com/delivery/cart/service/PricingService.java`; AC: subtotal, delivery fee, service fee, tax, simple functional discount, and total are calculated deterministically.
- [ ] T064 [US1] Implement geospatial vendor search service in `backend/src/main/java/com/delivery/geo/service/SearchService.java`; AC: nearby vendor search filters by tenant, category, availability, rating, and delivery zone where data exists.
- [X] T065 [US1] Implement `OrderService` checkout flow in `backend/src/main/java/com/delivery/order/service/OrderService.java`; AC: idempotency key, stock validation, order reference, order items, delivery code generation/hash, and initial statuses work in one transaction.
- [X] T066 [US1] Implement `PaymentService` mock confirmation in `backend/src/main/java/com/delivery/payment/service/PaymentService.java`; AC: local confirmation is idempotent and updates payment/order state once.
- [ ] T067 [US1] Implement customer tracking service in `backend/src/main/java/com/delivery/order/service/OrderTrackingService.java`; AC: customer sees current order/delivery status and delivery code only for own order.
- [X] T068 [US1] Implement delivery completion service methods in `backend/src/main/java/com/delivery/delivery/service/DeliveryService.java`; AC: correct 6-digit code marks delivery/order delivered and incorrect code records failed attempt.
- [X] T069 [P] [US1] Create catalog, inventory, search, cart, checkout, order, payment, and delivery controllers in `backend/src/main/java/com/delivery/*/controller/`; AC: endpoints match `/api/v1` contract, validate DTOs, call services, and contain no business logic.
- [X] T070 [P] [US1] Add PostGIS migration and indexes in `backend/src/main/resources/db/migration/V004__geo_search.sql`; AC: vendor location and zone indexes support nearby search without external services.
- [X] T071 [P] [US1] Add API docs for customer flow in `docs/api.md`; AC: search, catalog, cart, checkout, payment confirmation, order tracking, and delivery code endpoints are documented.
- [X] T072 [US1] Implement Flutter customer auth and API core in `mobile/lib/core/api/`, `mobile/lib/core/auth/`, and `mobile/lib/features/auth/`; AC: login state is represented by BLoC and API client attaches bearer token.
- [X] T073 [P] [US1] Implement Flutter address management in `mobile/lib/features/profile/`; AC: customer can list, add, edit, and select delivery address with loading/error states.
- [X] T074 [P] [US1] Implement Flutter vendor discovery in `mobile/lib/features/vendor_discovery/`; AC: customer can search nearby vendors and filter by category/availability/rating with empty/error states.
- [X] T075 [P] [US1] Implement Flutter catalog browsing in `mobile/lib/features/catalog/`; AC: catalog displays products, SKU prices, availability, pharmacy flags, and blocks prohibited fuel.
- [X] T076 [US1] Implement Flutter cart and checkout screens in `mobile/lib/features/cart/` and `mobile/lib/features/checkout/`; AC: one-vendor cart, totals, delivery/pickup, instructions, and checkout submission work.
- [X] T077 [US1] Implement Flutter order tracking, delivery code display, and history in `mobile/lib/features/orders/`; AC: customer sees status timeline, 6-digit code, and past orders.
- [X] T078 [P] [US1] Add Flutter BLoC tests for customer flows in `mobile/test/features/customer_order_flow_test.dart`; AC: vendor discovery, cart rule, checkout success, and order status state transitions are tested.

**Checkpoint**: Customer can complete the MVP happy path through backend APIs and customer mobile screens using seeded vendor/catalog/courier data.

## Phase 4: User Story 2 - Vendor Manages Catalog and Fulfillment (Priority: P2)

**Goal**: Vendor can register business profile, submit verification metadata, manage catalog/stock, accept/reject orders, prepare orders, mark ready for pickup, and view basic metrics.

**Independent Test**: Create vendor, verify it, add catalog and stock, receive an order, accept it, prepare it, mark it ready, and verify customer notification/status changes.

### Tests for User Story 2

- [X] T079 [P] [US2] Add vendor onboarding tests in `backend/src/test/java/com/delivery/vendor/VendorServiceTest.java`; AC: registration, document metadata, verification approval, and rejection cases are covered.
- [X] T080 [P] [US2] Add vendor fulfillment transition tests in `backend/src/test/java/com/delivery/order/VendorFulfillmentTest.java`; AC: accept, reject with reason, preparing, and ready-for-pickup transitions are covered.
- [X] T081 [P] [US2] Add catalog/inventory API tests in `backend/src/test/java/com/delivery/catalog/CatalogInventoryApiTest.java`; AC: vendor can create category/product/SKU, update stock, and negative stock is rejected.

### Implementation for User Story 2

- [X] T082 [P] [US2] Create vendor entities in `backend/src/main/java/com/delivery/vendor/entity/Vendor.java`, `VendorDocument.java`, `VendorOpeningHour.java`, and verification enums; AC: tenant ID, UUID IDs, timestamps, status fields, version where needed, and class comments exist.
- [X] T083 [US2] Add vendor migration in `backend/src/main/resources/db/migration/V006__vendors.sql`; AC: vendor, document, and opening-hour tables include tenant indexes and verification constraints.
- [X] T084 [P] [US2] Create vendor DTOs and mapper in `backend/src/main/java/com/delivery/vendor/dto/` and `backend/src/main/java/com/delivery/vendor/mapper/VendorMapper.java`; AC: register, update, verification, and response DTOs validate input.
- [X] T085 [P] [US2] Create vendor repository in `backend/src/main/java/com/delivery/vendor/repository/VendorRepository.java`; AC: repository supports tenant-scoped lookup by status, category, availability, and location fields.
- [X] T086 [US2] Implement `VendorService` in `backend/src/main/java/com/delivery/vendor/service/VendorService.java`; AC: public methods have comments and enforce tenant/role rules for vendor admins and platform admins.
- [X] T087 [US2] Implement vendor verification methods in `backend/src/main/java/com/delivery/vendor/service/VendorVerificationService.java`; AC: admin can approve/reject with reason and action is auditable later.
- [X] T088 [US2] Implement vendor fulfillment methods in `backend/src/main/java/com/delivery/order/service/OrderService.java`; AC: vendor accept, reject with reason, preparing, and ready-for-pickup update order state only through valid transitions.
- [X] T089 [P] [US2] Create vendor controller in `backend/src/main/java/com/delivery/vendor/controller/VendorController.java`; AC: endpoints support registration, profile, documents metadata, opening hours, availability, and verification actions.
- [X] T090 [P] [US2] Create vendor order endpoints in `backend/src/main/java/com/delivery/order/controller/VendorOrderController.java`; AC: accept, reject, preparing, ready-for-pickup endpoints are vendor-authorized and validate DTOs.
- [X] T091 [P] [US2] Document vendor onboarding, catalog, inventory, and fulfillment in `docs/api.md`; AC: docs include statuses, required reasons, and examples.
- [X] T092 [US2] Implement web vendor pages in `web/src/features/vendor/` and `web/src/app/vendor/`; AC: vendor profile, catalog management, inventory, order board, metrics, and forbidden states are present.
- [X] T093 [P] [US2] Add basic web tests for vendor pages in `web/src/features/vendor/vendor-pages.test.tsx`; AC: loading, empty, error, forbidden, and accepted order status rendering are tested.

**Checkpoint**: Vendor can onboard, manage catalog/stock, process orders, and view basic metrics independently.

## Phase 5: User Story 3 - Courier Completes Delivery (Priority: P3)

**Goal**: Courier can register, set availability, receive/accept/reject assignments, follow pickup/drop-off flow, complete delivery with code, record proof metadata and cash, and view earnings summary.

**Independent Test**: Assign a ready order to a verified online courier, accept job, complete pickup/drop-off statuses, reject invalid code, accept valid code, and record COD when enabled.

### Tests for User Story 3

- [X] T094 [P] [US3] Add courier availability tests in `backend/src/test/java/com/delivery/dispatch/CourierServiceTest.java`; AC: only verified online couriers in zone are eligible.
- [X] T095 [P] [US3] Add dispatch assignment tests in `backend/src/test/java/com/delivery/dispatch/DispatchServiceTest.java`; AC: simple assignment, rejection, and reassignment are covered.
- [X] T096 [P] [US3] Add delivery lifecycle API tests in `backend/src/test/java/com/delivery/delivery/DeliveryApiTest.java`; AC: arrival, pickup, on-route, arrived, failed, and delivered transitions are tested.

### Implementation for User Story 3

- [X] T097 [P] [US3] Create courier entity in `backend/src/main/java/com/delivery/dispatch/entity/Courier.java`; AC: tenant ID, user profile link, verification status, availability, operating zone, rating, version, and class comment exist.
- [X] T098 [P] [US3] Create dispatch job entity and enum in `backend/src/main/java/com/delivery/dispatch/entity/DispatchJob.java` and `DispatchJobStatus.java`; AC: assignment, rejection reason, timestamps, status, version, and tenant ID exist.
- [X] T099 [US3] Add courier and dispatch migration in `backend/src/main/resources/db/migration/V007__couriers_dispatch.sql`; AC: tables include tenant indexes, courier/status indexes, and constraints.
- [X] T100 [P] [US3] Create courier and dispatch DTOs/mappers in `backend/src/main/java/com/delivery/dispatch/dto/` and `backend/src/main/java/com/delivery/dispatch/mapper/`; AC: availability, assignment, accept, reject, and summary DTOs validate input.
- [X] T101 [P] [US3] Create courier and dispatch repositories in `backend/src/main/java/com/delivery/dispatch/repository/`; AC: repositories support tenant, zone, availability, status, and courier lookup.
- [X] T102 [US3] Implement `CourierService` in `backend/src/main/java/com/delivery/dispatch/service/CourierService.java`; AC: public methods have comments and enforce courier identity/tenant rules.
- [X] T103 [US3] Implement `DispatchService` in `backend/src/main/java/com/delivery/dispatch/service/DispatchService.java`; AC: simple available-courier assignment, accept, reject, and reassignment work without orchestration framework.
- [X] T104 [US3] Complete `DeliveryService` lifecycle in `backend/src/main/java/com/delivery/delivery/service/DeliveryService.java`; AC: arrived-at-vendor, pickup, on-route, arrived-at-customer, failed delivery, proof metadata, and COD fields work.
- [X] T105 [P] [US3] Create courier and dispatch controllers in `backend/src/main/java/com/delivery/dispatch/controller/CourierController.java` and `DispatchController.java`; AC: endpoints validate DTOs and enforce courier/admin/ops roles.
- [X] T106 [P] [US3] Create delivery controller in `backend/src/main/java/com/delivery/delivery/controller/DeliveryController.java`; AC: endpoints for status updates and completion with code match OpenAPI contract.
- [X] T107 [P] [US3] Document dispatch and delivery flow in `docs/api.md`; AC: statuses, rejection, reassignment, code confirmation, proof metadata, and COD are described.
- [X] T108 [US3] Implement Flutter courier core screens in `mobile/courier_app/lib/main.dart`; AC: online/offline, assignments, accept/reject, pickup, delivery code entry, and error states work.
- [X] T109 [P] [US3] Implement Flutter courier earnings summary in `mobile/courier_app/lib/main.dart`; AC: completed deliveries, failed deliveries, and earnings summary render from API DTOs.
- [ ] T110 [P] [US3] Add Flutter courier BLoC tests in `mobile/test/features/courier_flow_test.dart`; AC: assignment accept/reject and delivery confirmation state transitions are tested.

**Checkpoint**: Courier delivery flow can complete orders from ready-for-pickup through delivered or failed delivery.

## Phase 6: User Story 4 - Admin and Operations Manage Marketplace Operations (Priority: P4)

**Goal**: Admin and operations users can manage tenants, users/roles, categories, verification, zones, fees, commissions, taxes, cancellation policies, monitor orders, reassign delivery jobs, see operational dashboard, and audit sensitive actions.

**Independent Test**: Admin configures tenant policies and verifies vendor/courier; operations monitors a failed delivery, reassigns job, and sees the audit and dashboard updates.

### Tests for User Story 4

- [X] T111 [P] [US4] Add admin authorization tests in `backend/src/test/java/com/delivery/admin/AdminAuthorizationTest.java`; AC: admin endpoints reject customer/vendor/courier roles.
- [X] T112 [P] [US4] Add operations reassignment tests in `backend/src/test/java/com/delivery/dispatch/OperationsReassignmentTest.java`; AC: OPS can reassign failed/rejected jobs and non-OPS cannot.
- [X] T113 [P] [US4] Add audit log tests in `backend/src/test/java/com/delivery/audit/AuditLogServiceTest.java`; AC: sensitive admin/ops actions write actor, target, action, result, and timestamp.

### Implementation for User Story 4

- [X] T114 [P] [US4] Create zone and policy entities in `backend/src/main/java/com/delivery/geo/entity/Zone.java` and `backend/src/main/java/com/delivery/tenant/entity/FeePolicy.java`; AC: tenant ID, fee/tax/commission/cancellation fields, geometry/status, timestamps, and comments exist.
- [X] T115 [P] [US4] Create audit log entity in `backend/src/main/java/com/delivery/common/entity/AuditLog.java`; AC: actor, tenant, action, target type/id, business reference, result, and created timestamp exist.
- [X] T116 [US4] Add admin operations migration in `backend/src/main/resources/db/migration/V010__admin_ops_audit.sql`; AC: zones, policies, and audit tables include constraints and indexes.
- [X] T117 [P] [US4] Create admin/operations DTOs in `backend/src/main/java/com/delivery/tenant/dto/`, `geo/dto/`, and `dashboard/dto/`; AC: zone, policy, reassignment, and dashboard DTOs validate input.
- [X] T118 [US4] Implement `AuditLogService` in `backend/src/main/java/com/delivery/common/service/AuditLogService.java`; AC: public methods have comments and never log sensitive values.
- [X] T119 [US4] Implement zone and policy services in `backend/src/main/java/com/delivery/geo/service/ZoneService.java` and `backend/src/main/java/com/delivery/tenant/service/PolicyService.java`; AC: admin can configure zones, fees, commissions, taxes, cancellation policies.
- [X] T120 [US4] Implement operations reassignment in `backend/src/main/java/com/delivery/dispatch/service/DispatchService.java`; AC: OPS can reassign eligible jobs and action is audited.
- [X] T121 [P] [US4] Create admin controller endpoints in `backend/src/main/java/com/delivery/tenant/controller/AdminController.java`; AC: endpoints cover tenant, role, category, verification, zone, fee, policy, and audit views.
- [X] T122 [P] [US4] Create operations controller endpoints in `backend/src/main/java/com/delivery/dispatch/controller/OperationsDispatchController.java`; AC: OPS can monitor orders, view events, and reassign delivery jobs.
- [X] T123 [US4] Implement admin dashboard in `backend/src/main/java/com/delivery/dashboard/service/DashboardService.java` and `DashboardController.java`; AC: dashboard returns orders by status, active vendors, active couriers, cancellations, and failed deliveries.
- [X] T124 [P] [US4] Document admin and operations workflows in `docs/api.md` and `docs/architecture.md`; AC: role boundaries, audit events, and reassignment rules are documented.
- [X] T125 [US4] Implement web admin and operations pages in `web/src/features/admin/`, `web/src/features/operations/`, `web/src/app/admin/`, and `web/src/app/operations/`; AC: dashboards, vendor/courier verification, zones, policies, order board, dispatch management, and forbidden states work.
- [X] T126 [P] [US4] Add web admin/operations tests in `web/src/features/admin/admin-pages.test.tsx` and `web/src/features/operations/operations-pages.test.tsx`; AC: dashboard metrics, reassignment form, and forbidden state render correctly.

**Checkpoint**: Admin and operations users can configure and intervene in marketplace operations with audit visibility.

## Phase 7: User Story 5 - Finance Monitors Money Movement (Priority: P5)

**Goal**: Finance users can view payment transactions, commissions, refunds, cash-on-delivery reconciliation, vendor payout status, and export basic financial reports.

**Independent Test**: Seed paid, refunded, commission-bearing, and COD orders; finance views summaries, approves/rejects refunds where allowed, and exports basic records.

### Tests for User Story 5

- [X] T127 [P] [US5] Add finance authorization tests in `backend/src/test/java/com/delivery/finance/FinanceAuthorizationTest.java`; AC: finance endpoints reject non-finance/admin roles.
- [X] T128 [P] [US5] Add commission and COD summary tests in `backend/src/test/java/com/delivery/finance/FinanceServiceTest.java`; AC: totals include transactions, commissions, refunds, and unreconciled cash.
- [X] T129 [P] [US5] Add refund approval API tests in `backend/src/test/java/com/delivery/payment/RefundApiTest.java`; AC: finance/admin approval, rejection, duplicate refund idempotency, and audit behavior are covered.

### Implementation for User Story 5

- [X] T130 [P] [US5] Create finance entities in `backend/src/main/java/com/delivery/finance/entity/Commission.java` and `CashReconciliation.java`; AC: tenant, order, vendor/courier, amount, status, timestamps, version, and class comments exist.
- [X] T131 [US5] Add finance migration in `backend/src/main/resources/db/migration/V008__finance.sql`; AC: commissions and cash reconciliation tables include tenant/status/date indexes.
- [X] T132 [P] [US5] Create finance DTOs and mapper in `backend/src/main/java/com/delivery/finance/dto/` and `backend/src/main/java/com/delivery/finance/mapper/FinanceMapper.java`; AC: transaction, commission, refund, COD, payout, and export DTOs are defined.
- [X] T133 [P] [US5] Create finance repositories in `backend/src/main/java/com/delivery/finance/repository/`; AC: repositories support tenant, status, vendor, courier, and date-range queries.
- [X] T134 [US5] Implement `FinanceService` in `backend/src/main/java/com/delivery/finance/service/FinanceService.java`; AC: public methods have comments and return transaction, commission, refund, COD, payout, and export summaries.
- [X] T135 [US5] Implement refund approval/rejection in `backend/src/main/java/com/delivery/payment/service/PaymentService.java`; AC: total/partial refunds are idempotent, auditable, and visible to finance.
- [X] T136 [P] [US5] Create finance controller in `backend/src/main/java/com/delivery/finance/controller/FinanceController.java`; AC: endpoints cover transactions, commissions, refunds, COD reconciliation, payout status, and basic export.
- [X] T137 [US5] Add finance dashboard methods in `backend/src/main/java/com/delivery/dashboard/service/DashboardService.java`; AC: dashboard returns transactions, commissions, refunds, and COD reconciliation.
- [X] T138 [P] [US5] Document finance views in `docs/api.md`; AC: transactions, commission, refunds, COD reconciliation, payout status, and export assumptions are documented.
- [X] T139 [US5] Implement web finance pages in `web/src/features/finance/` and `web/src/app/finance/`; AC: transactions, refunds, COD reconciliation, payout status, export action, loading/error/empty/forbidden states work.
- [X] T140 [P] [US5] Add web finance tests in `web/src/features/finance/finance-pages.test.tsx`; AC: finance dashboard, refund state, and COD table render correctly.

**Checkpoint**: Finance users can monitor money movement and reconciliation without full accounting/ERP scope.

## Phase 8: User Story 6 - Support Resolves Tickets (Priority: P6)

**Goal**: Support users can view tickets, link tickets to orders, classify incidents, update status, add internal notes, and resolve tickets while protecting internal notes from customers.

**Independent Test**: Customer opens ticket linked to order, support classifies and updates it, internal note stays hidden from customer, and audit history is visible to authorized back-office users.

### Tests for User Story 6

- [X] T141 [P] [US6] Add support ticket service tests in `backend/src/test/java/com/delivery/support/SupportTicketServiceTest.java`; AC: create, classify, status update, internal note, and resolve flows are covered.
- [X] T142 [P] [US6] Add support visibility API tests in `backend/src/test/java/com/delivery/support/SupportTicketApiTest.java`; AC: customer cannot see internal notes and support/admin can.

### Implementation for User Story 6

- [X] T143 [P] [US6] Create support ticket entity and enums in `backend/src/main/java/com/delivery/support/entity/SupportTicket.java`, `SupportTicketStatus.java`, and `IncidentClassification.java`; AC: tenant, creator, order link, internal notes, status, version, and comments exist.
- [X] T144 [US6] Add support migration in `backend/src/main/resources/db/migration/V011__support_tickets.sql`; AC: support ticket table includes tenant, order, status, classification, assignee, and created date indexes.
- [X] T145 [P] [US6] Create support DTOs and mapper in `backend/src/main/java/com/delivery/support/dto/` and `backend/src/main/java/com/delivery/support/mapper/SupportTicketMapper.java`; AC: public customer response excludes internal notes.
- [X] T146 [P] [US6] Create support repository in `backend/src/main/java/com/delivery/support/repository/SupportTicketRepository.java`; AC: repository supports tenant, creator, order, status, and assigned support user queries.
- [X] T147 [US6] Implement `SupportTicketService` in `backend/src/main/java/com/delivery/support/service/SupportTicketService.java`; AC: public methods have comments, enforce visibility, and audit status/internal-note changes.
- [X] T148 [P] [US6] Create support ticket controller in `backend/src/main/java/com/delivery/support/controller/SupportTicketController.java`; AC: endpoints support customer create/list and support/admin update/classify/resolve.
- [X] T149 [P] [US6] Document support flow in `docs/api.md`; AC: ticket lifecycle, linked order, internal notes, and role visibility are documented.
- [X] T150 [US6] Implement web support pages in `web/src/features/support/` and `web/src/app/support/`; AC: ticket list/detail, classification, status update, internal notes, resolve action, and forbidden states work.
- [X] T151 [P] [US6] Add web support tests in `web/src/features/support/support-pages.test.tsx`; AC: internal notes are hidden in customer-style view and visible in support-style view.

**Checkpoint**: Support users can resolve tickets with correct role-limited visibility.

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Production hardening, documentation, validation commands, and final MVP acceptance checks across all stories.

- [X] T152 Add notification entity and repository in `backend/src/main/java/com/delivery/notification/entity/Notification.java` and `repository/NotificationRepository.java`; AC: recipient, role, type, title, message, business reference, read timestamp, tenant, and created timestamp exist.
- [X] T153 Add notification migration in `backend/src/main/resources/db/migration/V009__notifications.sql`; AC: notification table includes tenant, recipient, role, read, and created indexes.
- [X] T154 Implement notification service/controller in `backend/src/main/java/com/delivery/notification/service/NotificationService.java` and `controller/NotificationController.java`; AC: order events create persisted notifications and local logs without external providers.
- [X] T155 [P] Add notification tests in `backend/src/test/java/com/delivery/notification/NotificationServiceTest.java`; AC: customer, vendor, courier, admin, and operations notification records are created for important events.
- [X] T156 Add rate limiting for sensitive endpoints in `backend/src/main/java/com/delivery/common/security/RateLimitConfig.java`; AC: login, checkout, payment confirmation, refund approval, delivery code attempts, role changes, and support-note access are protected.
- [X] T157 [P] Add metrics and Prometheus config in `backend/src/main/resources/application.yml` and `docker-compose.yml`; AC: Actuator/Micrometer metrics are exposed and Prometheus can scrape local backend.
- [X] T158 [P] Add backup and restore documentation in `docs/local-development.md`; AC: PostgreSQL dump/restore and MinIO object backup commands are documented.
- [X] T159 [P] Add environment variable documentation in `README.md`; AC: backend, web, mobile, Keycloak, database, Redis, MinIO, and OpenAPI variables are documented.
- [X] T160 [P] Review final API docs in `docs/api.md` against `specs/001-delivery-marketplace-mvp/contracts/openapi.yaml`; AC: documented endpoints match contract names and `/api/v1` paths.
- [X] T161 Add end-to-end MVP flow test documentation in `docs/local-development.md`; AC: customer search -> catalog -> cart -> checkout -> payment -> vendor accept -> ready -> dispatch -> pickup -> delivery confirmation -> finance/support visibility is documented.
- [ ] T162 Ensure public class and service method comments in `backend/src/main/java/com/delivery/`; AC: public classes and public service methods have useful responsibility/purpose comments and no obvious noise comments.
- [X] T163 Run backend validation in `backend/`; AC: `mvn clean verify` passes.
- [X] T164 Run local infrastructure validation at repository root; AC: `docker compose up -d` starts PostgreSQL, Redis, Keycloak, MinIO, and backend without unhealthy services.
- [X] T165 Run web validation in `web/`; AC: `npm run lint` and `npm run build` pass.
- [X] T166 Run Flutter validation in `mobile/`; AC: `flutter analyze` and `flutter test` pass.
- [X] T167 Confirm OpenAPI locally from backend; AC: Swagger/OpenAPI endpoint is reachable and includes customer, vendor, courier, admin, finance, support, notification, and dashboard paths.
- [X] T168 Execute documented MVP happy path manually or via smoke script in `docs/local-development.md`; AC: flow completes from customer search through finance/support visibility using only MVP features.

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup completion and blocks all user stories.
- **US1 Customer Completes an Order (Phase 3)**: Depends on Foundational and provides the MVP core path.
- **US2 Vendor Manages Catalog and Fulfillment (Phase 4)**: Depends on Foundational and integrates with US1 order/catalog data.
- **US3 Courier Completes Delivery (Phase 5)**: Depends on Foundational and integrates with US1 order/delivery data.
- **US4 Admin and Operations (Phase 6)**: Depends on Foundational and benefits from US2/US3 data for dashboards and reassignment.
- **US5 Finance (Phase 7)**: Depends on payment/order data from US1 and COD data from US3.
- **US6 Support (Phase 8)**: Depends on Foundational and can link to US1 orders.
- **Polish (Phase 9)**: Depends on selected stories being complete.

### User Story Dependencies

- **US1**: MVP-first core customer order flow. Can start after Foundation with seeded vendor/courier data.
- **US2**: Can start after Foundation; completes real vendor onboarding and fulfillment behind US1.
- **US3**: Can start after Foundation; completes courier dispatch and delivery behind US1.
- **US4**: Can start after Foundation, but dashboard/reassignment validation needs US2/US3 data.
- **US5**: Needs payment/refund/COD records from US1/US3 for full validation.
- **US6**: Can start after Foundation; linked-order validation needs US1 order records.

### Within Each User Story

- Tests for critical behavior come before or alongside implementation.
- Entities/DTOs/mappers before repositories and services.
- Services before controllers.
- Controllers before web/mobile integration.
- Documentation and validation complete the story checkpoint.

## Parallel Opportunities

- Setup tasks marked `[P]` can run in parallel after T001.
- Foundation tasks for common DTOs, OpenAPI, logging, Keycloak docs, and tests can run in parallel where file paths differ.
- In each story, DTO/mapper/entity/test tasks marked `[P]` can run in parallel before service integration.
- Web and Flutter tasks can run in parallel with backend implementation after the relevant API DTOs/contracts are stable.
- Documentation tasks marked `[P]` can run in parallel after the feature behavior is defined.

## Parallel Example: User Story 1

```bash
Task: "T039 [P] [US1] Add cart business rule unit tests in backend/src/test/java/com/delivery/cart/CartServiceTest.java"
Task: "T040 [P] [US1] Add pricing unit tests in backend/src/test/java/com/delivery/cart/PricingServiceTest.java"
Task: "T044 [P] [US1] Create category/product/SKU entities in backend/src/main/java/com/delivery/catalog/entity/"
Task: "T045 [P] [US1] Create inventory entity in backend/src/main/java/com/delivery/inventory/entity/InventoryItem.java"
```

## Parallel Example: User Story 2

```bash
Task: "T079 [P] [US2] Add vendor onboarding tests in backend/src/test/java/com/delivery/vendor/VendorServiceTest.java"
Task: "T084 [P] [US2] Create vendor DTOs and mapper in backend/src/main/java/com/delivery/vendor/"
Task: "T085 [P] [US2] Create vendor repository in backend/src/main/java/com/delivery/vendor/repository/VendorRepository.java"
Task: "T091 [P] [US2] Document vendor onboarding, catalog, inventory, and fulfillment in docs/api.md"
```

## Parallel Example: User Story 3

```bash
Task: "T094 [P] [US3] Add courier availability tests in backend/src/test/java/com/delivery/dispatch/CourierServiceTest.java"
Task: "T097 [P] [US3] Create courier entity in backend/src/main/java/com/delivery/dispatch/entity/Courier.java"
Task: "T100 [P] [US3] Create courier and dispatch DTOs/mappers in backend/src/main/java/com/delivery/dispatch/"
Task: "T107 [P] [US3] Document dispatch and delivery flow in docs/api.md"
```

## Implementation Strategy

### MVP First

1. Complete Setup and Foundational phases.
2. Complete US1 with seeded vendor/courier data to prove customer search -> checkout -> payment -> delivery confirmation.
3. Validate US1 independently using backend API tests and Flutter customer screens.
4. Add US2 and US3 to replace seeded operational steps with real vendor and courier workflows.

### Incremental Delivery

1. Setup + Foundation -> secure local platform skeleton.
2. US1 -> customer MVP happy path.
3. US2 -> real vendor onboarding and fulfillment.
4. US3 -> real courier dispatch and delivery.
5. US4 -> admin/operations intervention and dashboard.
6. US5 -> finance monitoring and reconciliation.
7. US6 -> support ticket management.
8. Polish -> production hardening and final validation.

## Validation Commands

```bash
docker compose up -d
cd backend && mvn clean verify
cd web && npm run lint && npm run build
cd mobile && flutter analyze && flutter test
```

## Notes

- Do not add out-of-scope V1 features: multi-vendor cart, multi-drop routing, advanced AI/ML ETA, sponsored listings, loyalty tiers, drones, lockers, full ERP/accounting, automated pharmacy verification, advanced fraud scoring, analytics warehouse, complex microservices, or Kafka.
- Do not create placeholder-only code. Each task must leave working, testable behavior or concrete documentation/configuration.
- Keep services direct and readable. Add interfaces only when multiple real implementations exist or a test explicitly requires one.
