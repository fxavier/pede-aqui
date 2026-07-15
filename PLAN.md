# Pede Aqui — Remediation Plan (Phase 2 proposal)

Derived from `AUDIT.md` (2026-07-12, commit `e027a96`). Batches are small, independently shippable, one commit each, ordered by leverage and dependency. Nothing below is implemented yet — **awaiting approval**.

**Verification gates used throughout:**
- Backend: `cd backend && mvn clean verify`
- Backoffice: `cd pede-aqui-backoffice && npm run validate`
- Delivery web: `cd pede-aqui-delivery && npm run typecheck && npm run build`
- Flutter: `flutter analyze && flutter test` (per app)

**Out of scope by hard constraint:** `.env`, credential files, `infra/`, `admin/` — see "User actions required" at the end.

---

## Tranche A — Backend security & correctness (highest leverage)

### Batch 1 — Courier ownership + OTP lockout + completion guards
Fixes: SEC-01, SEC-02, SEC-05, COR-03, COR-08.
- **Files:** `delivery/service/DeliveryService.java`, `delivery/entity/Delivery.java`, `dispatch/service/DispatchService.java`, `dispatch/entity/DispatchJob.java` (+ tests).
- **Approach:**
  - Resolve the caller's courier id (reuse the existing `CourierService` "me" lookup) and require `delivery.getCourierId()` / `job.getCourierId()` match in `complete`, `updateStatus`, `accept`, `reject`; allow OPS/ADMIN passthrough on `updateStatus` (they're already permitted by the controller).
  - `complete()`: require current status `ARRIVED_AT_CUSTOMER`; reject terminal states.
  - OTP lockout: reject completion when `confirmationAttempts >= 5` with a distinct error code (`delivery_locked`); no schema change (column exists).
  - `DispatchJob.accept()/reject()`: only from `ASSIGNED`.
- **Risks:** couriers currently exercising cross-courier calls (there shouldn't be any legitimate ones) start getting 403; OPS flows must keep working — covered by tests.
- **Tests:** unit tests per rule in `delivery/` and `dispatch/` test packages, following `DeliveryConfirmationTest` style: wrong courier → 403, wrong state → 400, 6th attempt → locked, re-complete DELIVERED → 400, accept from non-ASSIGNED → 400.

### Batch 2 — Vendor ownership on orders
Fixes: SEC-03, SEC-04.
- **Files:** `order/service/OrderService.java` (+ tests).
- **Approach:** resolve the caller's vendor id from their `AppUserProfile` and require `order.getVendorId()` match in `acceptByVendor` / `rejectByVendor` / `markPreparing` / `markReadyForPickup`; in `listForCurrentContext`, filter to the caller's vendor when the caller holds a vendor role (tenant-wide view stays for ADMIN/OPS/SUPPORT).
- **Pre-step (investigate, stop if false):** confirm `AppUserProfile` (or vendor staff linkage) actually carries a vendor id. If no user→vendor linkage exists in the schema, STOP — that becomes a flagged schema migration (see sign-off section) before this batch can land.
- **Risks:** vendor users whose profiles lack the linkage lose access until backfilled — needs a data check first.
- **Tests:** vendor A on vendor B's order → 403 for each transition; vendor list contains only own orders; admin list unchanged.

### Batch 3 — Order state machine: refund wiring + rejection sequence
Fixes: COR-04, COR-07.
- **Files:** `payment/service/PaymentService.java`, `order/service/OrderService.java`, `order/entity/Order.java` (+ tests).
- **Approach:** `approveRefund` loads the order by `refund.getOrderId()` and calls `markRefunded()` in the same transaction. In `rejectByVendor`, keep `REJECTED_BY_VENDOR` → `REFUND_PENDING` as an explicit, guarded sequence (add the transition to the order state machine rather than silently overwriting) so history is honest; audit both events.
- **Risks:** any consumer treating `REFUND_PENDING` as terminal now sees `REFUNDED` — that's the intended fix; frontends already render both statuses generically.
- **Tests:** reject→approve-refund ends in `REFUNDED`; reject-refund leaves `REFUND_PENDING`; `markRefunded` only from `REFUND_PENDING`.

### Batch 4 — Inventory release/settle lifecycle
Fixes: COR-01.
- **Files:** `inventory/entity/InventoryItem.java`, `inventory/service/InventoryService.java`, `order/service/OrderService.java`, `delivery/service/DeliveryService.java` (+ tests).
- **Approach:** add `release(qty)` (available += qty, reserved −= qty) and `settle(qty)` (reserved −= qty) with non-negative guards; call `release` for each order item on vendor rejection and on `FAILED_DELIVERY`-triggered cancellation; call `settle` on delivery completion. All inside the existing transactions; optimistic `@Version` already covers concurrency.
- **Risks:** historical rows have inflated `quantityReserved` — the fix is forward-only; a one-off data correction is listed under sign-off (needs a decision, not code).
- **Tests:** reserve→reject releases; reserve→failed-delivery releases; reserve→deliver settles; reserved never goes negative.

### Batch 5 — Checkout idempotency under concurrency + 409 mapping
Fixes: COR-06.
- **Files:** `order/service/OrderService.java`, `common/exception/GlobalExceptionHandler.java` (+ tests).
- **Approach:** catch `DataIntegrityViolationException` around order creation in `checkout`, re-fetch by idempotency key and return the existing order. Add `GlobalExceptionHandler` mappings: `OptimisticLockingFailureException` → 409 `conflict`, residual `DataIntegrityViolationException` → 409 `constraint_violation` (standard `ErrorResponse` shape).
- **Risks:** low; changes only error paths.
- **Tests:** simulated duplicate-key on create returns the first order (no 500); handler unit tests for both mappings.

### Batch 6 — Product approval enforced in cart/checkout
Fixes: COR-05.
- **Files:** `cart/service/CartService.java`, `order/service/OrderService.java` (defensive re-check) (+ tests).
- **Approach:** in `addItem`, resolve the SKU's product and reject unless status `ACTIVE`; same check per item at checkout (carts created before a product was suspended).
- **Risks:** carts already containing now-inactive products start failing checkout with a clear error — correct behavior.
- **Tests:** PENDING/REJECTED product → `product_not_available` on add and on checkout.

### Batch 7 — Dispatch assignment guards
Fixes: COR-02.
- **Files:** `dispatch/service/DispatchService.java`, `dispatch/repository/DispatchJobRepository.java`, `order/entity/Order.java`, `delivery/entity/Delivery.java` (+ tests).
- **Approach:** guard `assign()` on order/delivery being in the dispatchable state; exclude couriers who have an active (`ASSIGNED`/`ACCEPTED`) `DispatchJob` from eligibility via a repository query (no new "busy" flag, no schema change); make `Order.markAssignedToCourier()` / `Delivery.assignCourier()` validate current state.
- **Risks:** zones with one courier now serialize deliveries — that is the intended MVP behavior (one active job per courier); flag if product wants a capacity >1.
- **Tests:** second assign for same order → 409; courier with active job not re-picked; assignment from wrong order state → 400.

### Batch 8 — Tenant header hardening
Fixes: SEC-06.
- **Files:** `common/security/TenantContext.java` (+ callers if needed, + tests).
- **Approach:** honor `X-Tenant-Id` only for platform admins (`isPlatformAdmin()`). For other principals without a JWT tenant claim (customers), derive tenant from their `AppUserProfile` membership — reject header values they don't belong to. Public (permitAll) browse endpoints are unaffected (no principal → header still allowed for anonymous storefront context, which carries no privileges).
- **Risks:** highest-behavioral-risk batch — customer flows depend on how tenant is currently selected at login/registration. Pre-step: trace how the web/mobile clients send `X-Tenant-Id` for customers today; if customers legitimately span tenants without profiles, STOP and report.
- **Tests:** customer with profile in tenant A sending header B → 403; platform admin header passthrough unchanged; anonymous browse unchanged.

### Batch 9 — Small backend hardening set
Fixes: SEC-09, SEC-12, SEC-13, SEC-19, PROD-06, SEC-21 (role-name split).
- **Files:** `common/security/RateLimitConfig.java`, `SecurityConfig.java`, `upload/controller/UploadController.java`, `upload/service/UploadService.java`, `auth/service/AppUserProfileService.java`, `common/security/MarketplaceRole.java`, `dispatch/service/CourierService.java` (+ tests).
- **Approach:** add `/register` paths to the rate-limited set keyed by client IP; add `@PreAuthorize` to the image presign endpoint (same role list as documents + CUSTOMER if profile photos are intended — default: same as documents); add `contentLengthRange`-equivalent max-size condition to presigns; allow-list roles SUPPORT may assign (non-privileged only); restrict `/actuator/**` (except health) to ADMIN/OPS; emit the 429 body through `ErrorResponse`; standardize `OPERATIONS` → `OPS`.
- **Risks:** low, localized. Actuator restriction interacts with Batch 14 (Prometheus) — done together deliberately? No: scrape fix is in Batch 14; this batch only restricts human access, Batch 14 adds the scrape path.
- **Tests:** security-slice tests per rule (follow existing `@WebMvcTest` style).

---

## Tranche B — Config & operations

### Batch 10 — Remove committed secret defaults, prod fail-fast ⚠️ sign-off
Fixes: SEC-07, PROD-04.
- **Files:** `backend/src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`.
- **Approach:** move the DB-password and Keycloak-admin-client-secret literal defaults out of base `application.yml` into `application-dev.yml` (dev keeps working with zero setup); base config leaves them unset so **prod fails fast** when `DB_PASSWORD` / `KEYCLOAK_ADMIN_CLIENT_SECRET` / `KEYCLOAK_ISSUER_URI` / `CORS_ALLOWED_ORIGINS` are missing (same pattern as the existing S3 validation).
- **⚠️ Flagged:** behavioral change for any deployment relying on the insecure defaults — that reliance is the vulnerability. Requires the credential rotation listed under "User actions".
- **Tests:** `mvn clean verify` (dev profile unaffected); manual: boot with prod profile and a missing var → startup failure.

### Batch 11 — Outbound resilience + metrics scrape
Fixes: PROD-03, PROD-01, PROD-05.
- **Files:** `common/config/RestTemplateConfig.java`, `common/security/SecurityConfig.java`, `docker-compose.yml`, `prometheus.yml`.
- **Approach:** RestTemplate with connect/read timeouts (3s/10s via `SimpleClientHttpRequestFactory`); permit `/actuator/prometheus` for the scraper — recommended: keep it authenticated and give Prometheus a static bearer via `authorization` block in `prometheus.yml`, or (simpler, compose-internal) permit it unauthenticated and rely on the port not being published — **decision point, default = permit-internal**; add compose healthchecks for keycloak/backend and switch backend's `depends_on.keycloak` to `service_healthy`.
- **Risks:** timeout values may need tuning; healthcheck start-period for Keycloak realm import.
- **Tests:** `mvn clean verify`; manual: `docker compose up` → Prometheus targets page shows backend UP.

### Batch 12 — CI covers the four real apps
Fixes: PROD-02.
- **Files:** `.github/workflows/ci.yml`.
- **Approach:** replace the `web` job (dead prototype) and `mobile` job (nonexistent dir) with: `pede-aqui-backoffice` (`npm ci && npm run validate && npm run build`), `pede-aqui-delivery` (`npm ci && npm run typecheck && npm run build`), one Flutter job per app (`flutter analyze && flutter test`). Add `cache:` to setup-java/setup-node and Flutter caching; branch-filter pushes to `main` + all PRs.
- **Risks:** first run will surface existing warnings; `--max-warnings=0` lint may fail on the backoffice — if so, report rather than loosen the gate.
- **Tests:** the workflow itself on a branch push.

### Batch 13 — Pagination on unbounded lists
Fixes: COR-09, SEC-11.
- **Files:** the five list paths (`OrderService`, `DispatchService`, `CourierService`, `VendorService`, `TenantService`, `AppUserProfileService`, audit-log list) + their controllers/repositories/mappers.
- **Approach:** `Pageable` with a max page size (e.g. 100) and `findByTenantId(..., pageable)`; keep response shape backward-compatible where frontends consume arrays (Spring `Page.getContent()` — return the page object only where frontends are updated in the same batch; otherwise cap + sort and keep the array shape). Default: **keep array shape, apply cap**, full pagination as a follow-up to avoid a breaking API change.
- **⚠️ Flagged if full pagination chosen:** breaking response-shape change for backoffice consumers.
- **Tests:** page-size cap respected; tenant filtering pushed to the query (no `findAll`).

---

## Tranche C — Frontends

### Batch 14 — Backoffice: real route guards + multi-role
Fixes: COR-10, SEC-14, SEC-15.
- **Files:** `src/components/layout/app-shell.tsx`, `src/app/providers.tsx`, `src/app/login/page.tsx`, `src/app/platform/page.tsx`, affected page components (`finance`, `admin`, `users`).
- **Approach:** store `roles: string[]` and check membership; hoist the forbidden check so guarded pages don't mount their fetch effects (guard component wrapping `children` **before** page render — e.g. AppShell renders a `<Forbidden/>` route replacement and pages move fetches behind the shared guard, or a small `useRoleGuard(roles)` hook that pages call to gate their effects); add a platform-admin check to `/platform` with redirect.
- **Risks:** touching every guarded page's fetch wiring — mechanical but broad; keep diffs per-page small.
- **Tests:** `npm run validate`; manual role-matrix smoke (vendor_admin cannot trigger finance fetches — verify via network tab).

### Batch 15 — Backoffice + delivery web: 401 handling, tenant-switch cache, error surfaces
Fixes: SEC-17, COR-11, COR-12, COR-13, COR-14, SEC-21 (logout).
- **Files:** backoffice `src/lib/http-client.ts`, `src/store/…auth`, `app-shell.tsx`; delivery `src/lib/api/client.ts`, `src/app/vendor/page.tsx`, `src/components/…CartDrawer.tsx`, `src/app/checkout/page.tsx`, `src/lib/api/services.ts`.
- **Approach:** central 401 → clear auth + redirect `/login` (axios response interceptor in delivery; http-client wrapper in backoffice); `queryClient.clear()` on `enterTenant`/`exitTenant`; try/catch + user-visible error on cart mutations (existing toast/state patterns); move checkout redirect into `useEffect`; delete the dead 404 branch in `userService.list`; call Keycloak end-session on logout where a session exists.
- **Risks:** low; UX-visible improvements only.
- **Tests:** `npm run validate` / `typecheck + build`; manual: expire token → redirected to login; failed add-to-cart shows an error.

### Batch 16 — Flutter live-mode enablement ⚠️ sign-off (new dependency + product decision)
Fixes: GAP-02, SEC-18, GAP-06, GAP-07 (partially GAP-09).
- **Files:** both apps' DI (`service_locator.dart`, `injection.dart`), `app_config.dart`/equivalent, `auth_repository.dart`, `remote_auth_data_source.dart`, delivery-app registration screen, cart repository wiring; `pubspec.yaml` (both).
- **Approach:** Keycloak issuer URL via `--dart-define` (consistent with `API_BASE_URL`); flip `USE_MOCK_DATA` default to `false` **(product decision — flag)** or at minimum fail loudly in release builds when mock mode is on; persist tokens in `flutter_secure_storage` and call the existing `verifySession()` on startup; wire delivery-app `register()` to `POST /customers/register`; plumb the real customer id from the auth session into `ApiCartRepository`; fix `getWeeklyEarnings` shape parsing (drop the silent `catch (_) => []`).
- **⚠️ Flagged:** new dependency `flutter_secure_storage` (both apps); mock-default flip changes every unconfigured dev run.
- **Tests:** `flutter analyze && flutter test` + new unit tests for auth persistence and registration mapping; manual emulator login against local stack.

---

## Tranche D — Test foundation

### Batch 17 — Integration test baseline (Testcontainers)
Fixes: TEST-01, TEST-03 (partially).
- **Files:** `backend/src/test/java/...` only (revive `PostgresTestContainerConfig`); `backend/pom.xml` only if the Testcontainers deps are missing (verify first — the config class suggests present).
- **Approach:** one `@SpringBootTest` slice against Postgres-in-container running Flyway; integration tests for: tenant row isolation (SEC-06 regression), checkout→reject→refund→inventory-release (Batches 3–5 regression), OTP lockout (Batch 1), dispatch single-active-job (Batch 7). These are the regression harness for Tranche A.
- **Risks:** CI time increases; Docker required in CI (GitHub runners have it).
- **Tests:** it *is* the tests; gate = `mvn clean verify` green.

### Batch 18 — Web test harness (minimal)
Fixes: TEST-02 (baseline, not coverage).
- **Files:** `pede-aqui-delivery` (add `vitest` + a first test for cart-slice and checkout idempotency-key behavior), backoffice (role-guard unit tests). ⚠️ **new dev-dependencies (vitest / testing-library)** — flagged.
- **Approach:** smallest harness that locks the Batch 14/15 behavior; no snapshot sprawl.
- **Tests:** `npm test` wired into the CI jobs from Batch 12.

---

## Explicit sign-off required (do not proceed without it)

| Item | Type | Related |
|---|---|---|
| User→vendor linkage migration, **only if** Batch 2's investigation finds none | Schema migration (new Flyway `V028+`) | SEC-03/04 |
| One-off data correction for historically inflated `quantityReserved` | Data migration | COR-01 |
| `flutter_secure_storage` (both apps), `vitest`/testing-library (web) | New dependencies | Batches 16, 18 |
| Flip `USE_MOCK_DATA` default to live | Behavioral/product | GAP-02 |
| Full pagination (response-shape change) vs. cap-only in Batch 13 | Breaking API change | SEC-11 |
| Prod fail-fast on missing env vars (deploys relying on defaults will stop booting) | Breaking deploy change | SEC-07/PROD-04 |
| Keycloak realm hardening: `sslRequired`, disable ROPC, brute-force protection, token lifespans (`keycloak/delivery-realm.json` + running realms) | Infra-adjacent; changes auth flows all clients use (ROPC is currently the primary login everywhere) | SEC-08, SEC-16 |

## Deferred (needs product direction, not in this plan)

Real payments/M-Pesa (GAP-01, L), courier status wiring end-to-end (GAP-03), ratings (GAP-04), notification delivery (GAP-05), wallet/proof-photo/earnings endpoints (GAP-08/09), the 36 mock screens keep-vs-cut (GAP-10), cosmetic web features (GAP-11), token storage architecture/BFF + CSP (SEC-16), tracing (PROD-07), web Dockerfiles (PROD-10), repo hygiene deletions (PROD-11 — involves deleting dirs/nested `.git`; do manually or approve explicitly).

## User actions required (cannot be done by code changes here)

1. **Rotate the AWS IAM key pair** referenced by `.env:11-12` and the key in `delivery-springboot-dev_accessKeys.csv`; move both files out of the repo working tree (open item since the 2026-06-06 audit).
2. Rotate the Keycloak admin client secret and DB password anywhere the committed defaults were ever used (goes with Batch 10).
3. Decide the sign-off items above.

## Suggested execution order

1 → 3 → 4 → 5 → 6 → 7 → 9 (Tranche A core, each small) → 2 (after its investigation) → 10 → 11 → 12 → 17 (locks everything in) → 8 (highest behavioral risk, now regression-covered) → 14 → 15 → 13 → 16 → 18.

---

**STOP — awaiting approval.** Reply with the batches (or sign-off items) to green-light; implementation is one commit per batch with tests, per the constraints in the original brief.
