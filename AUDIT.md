# Pede Aqui — Production-Readiness Audit

**Date:** 2026-07-12 · **Commit:** `e027a96` · **Method:** 6 parallel read-only audit passes (backend security, backend correctness, web frontends, Flutter apps, prod-readiness/tests, feature gaps); every finding listed here was re-verified against source by the lead reviewer before inclusion. Findings from stale docs (`docs/audit-report.md` 2026-06-06, `docs/api-gaps.md`) were re-checked — several of their claims are now false and are corrected below.

**Not audited:** `infra/` and `admin/` Terraform content (constraint: read-only existence noted), `web/` (dead prototype), `screens/` (design assets), `backend/backend/` (stale husk), contents of `.env` / credential files (values deliberately not read), dependency CVE scan (`npm audit`/OWASP not run — flagged as follow-up), load/performance testing.

Severity: **[C]** CRITICAL · **[H]** HIGH · **[M]** MEDIUM · **[L]** LOW. Effort: **S** &lt; ½ day · **M** ½–2 days · **L** &gt; 2 days.

---

## 1. Architecture map

| Component | Tech | Role | Data-layer status |
|---|---|---|---|
| `backend/` | Java 21, Spring Boot, Flyway (V001–V027), PostGIS, Redis | REST API `/api/v1`, Keycloak JWT auth | Live |
| `pede-aqui-backoffice/` | Next.js 15, Redux Toolkit, TanStack Query | Admin/ops/finance/vendor/support portal | Live API |
| `pede-aqui-delivery/` | React 18 + Vite, Redux Toolkit | Customer web SPA | Live API |
| `pede_aqui_delivery_app/` | Flutter, BLoC | Customer mobile | **Mock by default** |
| `pede_aqui_courier_app/` | Flutter, Cubit | Courier mobile | **Mock by default** |

**Backend layering** (per `docs/architecture.md`, deliberately simple — no DDD/CQRS/Kafka): `controller → service → repository → entity`, with `dto`/`mapper` and cross-cutting `common/{config,exception,security}`. 19 domain packages (`order`, `cart`, `catalog`, `dispatch`, `delivery`, `payment`, `finance`, `tenant`, `auth`, …).

**Data flow (happy path):** cart → checkout (idempotency key; one transaction creates Order + mock Payment + Delivery + reserves stock) → vendor state machine (`PAYMENT_CONFIRMED → ACCEPTED_BY_VENDOR → PREPARING → READY_FOR_PICKUP`) → dispatch (first eligible courier in zone) → courier state machine (`ACCEPTED → … → DELIVERED`) → OTP-confirmed completion.

**External integrations:** Keycloak (JWT + admin REST API for user provisioning), S3/MinIO (presigned uploads, profile-switched), Redis (single 60s vendor-search cache), Prometheus (configured but **cannot scrape** — see PROD-01). Payment is a local mock (`provider = "LOCAL_MOCK"`).

**Tenancy:** `tenant_id` column + `TenantContext` (JWT claim → `X-Tenant-Id` header fallback). Platform admins = `ADMIN` role with no tenant claim.

---

## 2. Correctness & bugs

| ID | Sev | Finding | Evidence | Effort |
|---|---|---|---|---|
| COR-01 | **C** | Reserved stock is never released or consumed — permanent inventory leak | `InventoryService.java:30` has only `reserve()`; no release/settle exists anywhere. Checkout reserves (`OrderService.java:113-115`); rejection (`OrderService.java:174-208`), failed delivery (`DeliveryService.java:72-74`), and successful delivery never release/settle. `quantityReserved` grows forever; cancelled orders eat stock permanently | M |
| COR-02 | **H** | Dispatch can double-assign and re-assign with no guard | `DispatchService.java:61-73` — `assign()` never marks the courier busy (eligibility = self-toggled `available` flag, `CourierService.java:80-86`), and `Order`/`Delivery` transition unconditionally. Same courier gets every order in the zone; `assign()` re-invoked on an assigned order creates duplicate `DispatchJob` rows | M |
| COR-03 | **H** | Delivery completion bypasses the state machine and is re-enterable | `DeliveryService.java:44-57` — `complete()` checks only the OTP hash, never the current status. A delivery in any state (even already `DELIVERED`) jumps to `DELIVERED` | S |
| COR-04 | **H** | Refund approval never advances the order — stuck in `REFUND_PENDING` forever | `PaymentService.java:81-91` mutates only `Refund`; `Order.markRefunded()` has zero callers (dead code) | S |
| COR-05 | **H** | Unapproved (PENDING/REJECTED) products are orderable | `CartService.java:44-62` — `addItem` validates SKU + stock but never product status; only the anonymous catalog listing filters to `ACTIVE` (`CatalogService.java:114-121`). Defeats the admin-approval gate | S |
| COR-06 | **M** | Concurrent duplicate checkout returns HTTP 500 instead of the idempotent result | `OrderService.java:97-102` is check-then-act; the DB unique key (`V003…sql:122`) makes the loser throw `DataIntegrityViolationException`, unhandled in `GlobalExceptionHandler` → 500. Same gap for all `@Version` optimistic-lock conflicts (no 409 mapping) | S |
| COR-07 | **M** | Vendor rejection double-transitions the order, erasing `REJECTED_BY_VENDOR` | `OrderService.java:184` sets `REJECTED_BY_VENDOR`, then `:204` immediately overwrites with `REFUND_PENDING` in the same transaction. Combined with COR-04 the order dies in `REFUND_PENDING` | S |
| COR-08 | **M** | `DispatchJob.accept()/reject()` lack state guards | `DispatchJob.java:59-61` transition unconditionally; `accept` (`DispatchService.java:86-92`) has no status check (contrast `reassign`, which checks `REASSIGNABLE`). An in-progress delivery can be reset to `ACCEPTED` | S |
| COR-09 | **M** | Tenant-scoped lists load whole tables and filter in memory | `DispatchService.java:77-82` (`findAll()` + per-row `tenantId()` call), same pattern in `CourierService.java:139`, `VendorService.java:68`, `TenantService.java:40`, `OrderService.java:80` | M |
| COR-10 | **M** | Backoffice honors only the first role — multi-role users mis-authorized | `providers.tsx:47`, `login/page.tsx:55` (`roles[0]`); nav filter + route guard compare a single role (`app-shell.tsx:69-80`). `['OPS','ADMIN']` user is denied `/admin` | S |
| COR-11 | **M** | Tenant impersonation switch never clears the React Query cache | `auth-slice.ts` `enterTenant`/`exitTenant` update storage/Redux only; QueryClient has `gcTime: Infinity` — tenant A's cached data can render while "inside" tenant B | S |
| COR-12 | **M** | Delivery-web cart mutations swallow failures / unhandled rejections | `vendor/page.tsx:263,80-105,123-130` (uncaught async `onClick`), `CartDrawer.tsx:41` (empty `catch {}`) — UI desyncs from server cart with no feedback | S |
| COR-13 | **L** | Dead 404-handling in `userService.list` masks failures as empty list | `services.ts:285-297` checks an axios-style `error.response.status` that this app's `http-client.ts` never produces | S |
| COR-14 | **L** | `navigate()` during render in checkout | `checkout/page.tsx:37-40` — redirect in render body instead of an effect | S |
| COR-15 | **L** | `inventory_items` lacks FKs to sku/vendor | `V003…sql:54,194` — lookup is covered by the unique constraint, but orphaned rows are representable | S |

**Checked and clean:** money is `BigDecimal` `HALF_UP` scale-2 throughout; `@Version` on all mutable entities; checkout/payment/delivery each single-`@Transactional`; no TODO/FIXME in backend main; order-detail polling uses TanStack `refetchInterval` (no leak); no TS/ESLint build-error suppression in either web app; OTP generated with `SecureRandom`.

---

## 3. Security

| ID | Sev | Finding | Evidence | Effort |
|---|---|---|---|---|
| SEC-01 | **C** | Any courier can complete/advance/fail ANY delivery in the tenant (no assigned-courier check) | `DeliveryService.java:44,61` load by tenant+id only; `Delivery.courierId` never compared to caller; controller gates role only (`DeliveryController.java:26,32`). Includes marking `FAILED_DELIVERY` (cancels the order) and recording cash amounts | S |
| SEC-02 | **C** | Any courier can accept/reject any dispatch job (hijack or sabotage) | `DispatchService.java:86,96` never compare `job.getCourierId()` to caller; `DispatchController.java:37,41` gate role only | S |
| SEC-03 | **C** | Vendor users can operate on other vendors' orders — including forcing refunds | `OrderService.java:162-224` resolve orders tenant-wide (`findOrder`), no vendor-ownership check; `VendorOrderController.java:26-44` gate role only. `rejectByVendor` triggers a refund (`:201`) — merchant A can refund merchant B's orders | M |
| SEC-04 | **C** | Vendor users see all tenant orders + customer names (PII) | `OrderService.java:78-92` `listForCurrentContext` returns `findByTenantId` for any vendor role, resolving customer display names | M |
| SEC-05 | **C** | Delivery OTP is brute-forceable: attempts counted but never enforced | `DeliveryService.java:47-51` — `confirmationAttempts` incremented, never checked (read only by the mapper). 10⁶ space, only defense a per-instance 20/min limiter (SEC-10). Combined with SEC-01 + COR-03: any courier can brute-force any delivery to `DELIVERED` from any state | S |
| SEC-06 | **H** | Tenant derived from spoofable `X-Tenant-Id` header for every non-admin without a tenant claim | `TenantContext.java:18-24,48-57` — header fallback with no membership validation; customers are created with no tenant claim (`KeycloakAdminService.java:63-67`), so ALL customer requests select tenant via client header | M |
| SEC-07 | **H** | Committed insecure fallback secrets in `application.yml`; prod does not force-override | `application.yml:22` (DB password literal default), `:84` (Keycloak admin client-secret literal default; same value in `keycloak/delivery-realm.json:79`). `application-prod.yml` fails fast only on S3 vars. Rotate + remove defaults (values not reproduced here) | S |
| SEC-08 | **H** | Keycloak realm export: `sslRequired: "none"` + public client with ROPC enabled | `delivery-realm.json:4`; `delivery-app` client `publicClient: true` + `directAccessGrantsEnabled: true` (`:47-48`). No brute-force protection or token lifespans pinned. ROPC is also the primary login flow in both web apps and both Flutter apps | M |
| SEC-09 | **H** | Open registration endpoints are unthrottled; merchant registration mints tenants unverified | `SecurityConfig.java:34-35` permitAll; `RateLimitConfig.isSensitive` (`:54-66`) matches `/auth`,`/login` but not `/register`; `MerchantRegistrationService.java:43-101` creates tenant + VENDOR_ADMIN with no verification | S |
| SEC-10 | **M** | Rate limiter is per-instance, unbounded (memory leak), naive path matching | `RateLimitConfig.java:27,35` — `ConcurrentHashMap` never evicted, keys embed UUIDs, `.contains()` matching; useless across replicas. Redis already wired but unused for this | M |
| SEC-11 | **M** | Unbounded list endpoints (DoS + bulk exposure) | `OrderService.java:79-81`, `DispatchService.java:78`, `AppUserProfileService.java:131-138`, `CourierService.java:137-141`, audit log list; no `Pageable`/max-size anywhere (overlaps COR-09) | M |
| SEC-12 | **M** | Presigned image uploads: no size limit, no role gate | `UploadService.java:52-63` presigns without content-length condition; `UploadController.java:24-28` image endpoint has no `@PreAuthorize` (document endpoint is gated at `:30`) | S |
| SEC-13 | **M** | SUPPORT can create user profiles with arbitrary roles (mass-assignment) | `AdminController.java:72-77` allows ADMIN,SUPPORT; `AppUserProfileService.java:97-119` copies `request.roles()` with no allow-list. JWT-derived runtime authz limits immediate escalation | S |
| SEC-14 | **M** | Backoffice route guard hides UI but privileged fetches still fire | `app-shell.tsx:74-80,184-194` swaps children only; pages own their fetch `useEffect` and mount regardless (`finance/page.tsx:52-59` fires 5 finance calls for any authenticated user). The in-code comment claims otherwise | M |
| SEC-15 | **M** | `/platform` super-admin console has no client-side role guard at all | `platform/page.tsx:49-81` self-shelled (not in `AppShell` nav → guard never applies); `middleware.ts:17-24` checks token presence only | S |
| SEC-16 | **M** | JWT in JS-readable storage (sessionStorage + non-HttpOnly cookie, no `Secure`), no CSP/HSTS/X-Frame-Options | `login/page.tsx:43-44`, delivery `client.ts:11`, `auth-slice.ts:62`; `next.config.mjs` sets no headers. Any XSS = full token theft | L |
| SEC-17 | **M** | No 401 handling / token refresh in either web app — expired session fails silently | delivery `client.ts:10-14` (no response interceptor), backoffice `http-client.ts:23-35` (nothing maps 401→logout); ROPC stores access token only; `automaticSilentRenew: false` | M |
| SEC-18 | **M** | Flutter: tokens in memory only, no secure storage; auth URL hardcoded to cleartext `http://localhost:8080` | `api_client.dart` token fields; no `flutter_secure_storage` in either pubspec; `auth_repository.dart:65,114`, `remote_auth_data_source.dart:19` hardcode the Keycloak token URL (not `--dart-define`-able; breaks any non-localhost build and Android blocks cleartext in release) | M |
| SEC-19 | **M** | Actuator `metrics`/`prometheus` readable by any authenticated user | `application.yml:46-50` exposes them; `SecurityConfig` has no role restriction beyond `authenticated()` | S |
| SEC-20 | **M** | AWS keys on disk at repo root — flagged 2026-06-06, still unremediated | `.env:11-12` (IAM key pair; loaded by both `spring.config.import` and compose `env_file`) and `delivery-springboot-dev_accessKeys.csv`. **Both verified untracked by git** (`.gitignore` covers them) — but the June audit's top action (rotate + move out of repo) was never done | S |
| SEC-21 | **L** | Logout doesn't end the Keycloak SSO session; swagger/api-docs public in all profiles; `OPS` vs `OPERATIONS` role-name split makes `isAdminOrOperations()` dead | `app-shell.tsx:54-60`; `SecurityConfig.java:33`; `MarketplaceRole.java:11` + `CourierService.java:205` vs `hasRole('OPS')` everywhere else | S |

**Checked and clean:** all repository `@Query`s parameterized (no injection); CORS origin list explicit + env-driven; global exception handler leaks no internals; cart/order/tracking enforce customer ownership; notifications and support tickets ownership-scoped; no committed secrets found in tracked files; no prompt-injection content encountered.

---

## 4. Feature gaps (evidence-based)

| ID | Sev | Gap | Evidence: exists vs missing | Effort |
|---|---|---|---|---|
| GAP-01 | **C** | Real payments (M-Pesa) — UI sells it, backend is 100% mock | `Payment.java:49` hardcodes `LOCAL_MOCK`; checkout UIs offer M-Pesa/card/cash (`checkout/page.tsx:18-20`, `checkout_screen.dart:96-103`) but the chosen method is never even sent to the backend; intended provider named only in `docs/diagrams/01-context.drawio:36` | L |
| GAP-02 | **H** | Both Flutter apps ship in mock mode by default | `service_locator.dart:18`, `injection.dart:34,42` — `USE_MOCK_DATA` `defaultValue: true`; any build without the dart-define runs on fake data incl. `mock.jwt.token` | S |
| GAP-03 | **H** | Courier app never persists delivery progress — customer tracking is blind until completion | `PATCH /deliveries/{id}/status` (`DeliveryController.java:31`) has zero references in courier app; no `updateStatus` in datasource/repository interfaces; "Cheguei ao cliente" only navigates (`delivery_detail_screen.dart:69`) | M |
| GAP-04 | **H** | Ratings: schema+entity+repo exist, zero write path/API/UI | `Rating.java` + `RatingRepository` (V014) referenced nowhere; vendor `rating` field shown to customers is a denormalized stub never fed by real ratings | M |
| GAP-05 | **H** | Notifications are stored but never delivered; preferences table fully unwired | `NotificationService` has no push/email/SMS/websocket path; `UserNotificationPreference` (V009/V018) has no controller/service references — write-never/read-never | M–L |
| GAP-06 | **M** | Mobile self-registration dead despite live backend endpoint | delivery app `auth_repository.dart:97-100` throws "Registo não disponível" (TODO points at wrong path — `POST /customers/register` exists and web uses it); courier register button `onPressed: null` | S |
| GAP-07 | **M** | Flutter cart uses hardcoded `'customer-id'` | `service_locator.dart:30-31` (TODO in code) — live-mode carts hit a nonexistent customer path | S |
| GAP-08 | **M** | Courier wallet is 100% hardcoded; withdraw is a no-op; proof-photo toggle never sent | `wallet_screen.dart:31,33,50` (literals + empty `onPressed`); `remote_courier_data_source.dart:117-120` posts only the OTP, drops `hasProofPhoto` | M |
| GAP-09 | **M** | Courier earnings screens always empty in live mode (wrong endpoint shape, silently swallowed) | `remote_courier_data_source.dart:62-72` casts the earnings-summary object as a List → throws → `catch (_) => []`; history filters `/dispatch-jobs` for `DELIVERED`, which it never contains | M |
| GAP-10 | **M** | 36 static mockup screens shipped as live all-roles routes in the backoffice | `src/app/screens/*` render static HTML via `ImportedScreenView`; nav entry `roles: []` (`app-shell.tsx:30`); several imply features with **no backend** (roles/permissions, campaigns, segments, inventory logs, marketing ROI) | M |
| GAP-11 | **M** | Delivery-web cosmetic features on the money path | Payment method selector non-functional; loyalty/addresses/payments "em breve"; dead buttons (`profile/page.tsx:99-104`, `orders/page.tsx:77-79`); cart notes never reach the vendor (`cart-slice.ts:10-14`) | M |
| GAP-12 | **L** | `ServiceSlot` (scheduled ordering): table+entity+repo, no service/API/UI | V015 migration; zero controller/service references | M |
| GAP-13 | **L** | OpenSpec change `delivery-web-real-data` complete but never synced/archived | `openspec/changes/delivery-web-real-data/tasks.md` all `[x]` | S |

**Stale-doc corrections:** `docs/api-gaps.md` is wrong on three counts today — web apps ARE live (not "0 APIs connected"), `GET /api/v1/orders` exists, and `RemoteCourierDataSource` paths are correct (verified endpoint-by-endpoint). The real mobile gap is the mock default (GAP-02), not wrong paths.

---

## 5. Missing production essentials

| ID | Sev | Finding | Evidence | Effort |
|---|---|---|---|---|
| PROD-01 | **H** | Metrics pipeline is decorative: Prometheus can't scrape (401) | `SecurityConfig.java:33` permits only `/actuator/health/**`; `prometheus.yml:5-9` sends no auth. Zero metrics collected anywhere | S |
| PROD-02 | **H** | CI builds the dead prototype and a nonexistent dir; the 4 real apps have no CI | `.github/workflows/ci.yml:27` `cd web` (old prototype), `:37` `cd mobile` (doesn't exist — job permanently red). Only `backend/` is really covered. No caching, no branch filters | M |
| PROD-03 | **H** | Keycloak `RestTemplate` has no timeouts — IdP outage exhausts the request thread pool | `RestTemplateConfig.java:11` bare `new RestTemplate()`; used for every Keycloak admin call (`KeycloakAdminService.java:89-181`) | S |
| PROD-04 | **M** | Prod profile validates only S3 — Keycloak issuer, admin secret, CORS, DB creds silently fall back to dev/localhost defaults | `application-prod.yml` vs `application.yml` defaults | M |
| PROD-05 | **M** | Compose: Keycloak/backend/Prometheus have no healthcheck; backend starts on `service_started` (races realm import) | `docker-compose.yml` — healthchecks only for postgres/redis/minio | S |
| PROD-06 | **M** | Rate-limit 429 body bypasses the standard `ErrorResponse` shape | hand-written JSON in `RateLimitConfig` vs `GlobalExceptionHandler` contract | S |
| PROD-07 | **L** | No tracing; correlation ID not propagated to Keycloak calls | no micrometer-tracing/OTel dep; `CorrelationIdFilter` stops at the boundary | M |
| PROD-08 | **L** | Logging: single console appender, fragile manual JSON escaping, no prod/dev split | `logback-spring.xml` | S |
| PROD-09 | **L** | Upload-limit env vars defined but wired to nothing | `TMS_UPLOAD_*` in `.env`; no `spring.servlet.multipart.*` binding exists | S |
| PROD-10 | **L** | No Dockerfile/deploy story for either web app | no Dockerfiles; not in compose | M |
| PROD-11 | **L** | Repo hygiene: nested `backend/.git` (same remote as root), stale `backend/backend/target` + `backend/web/`, literal `~/` dir — all flagged in June, still present | `docs/audit-report.md:65-72` (that doc is itself now stale on structure: source moved to `backend/src`, inverting its claims) | S–M |

**Present and working:** global exception handler with correlation IDs (good shape), structured JSON console logging, Flyway with `ddl-auto: validate`, actuator health probes enabled, idempotency keys on checkout/refunds, audit logging on sensitive actions, `.env`/CSV properly gitignored.

---

## 6. Test coverage

| ID | Sev | Finding | Evidence | Effort |
|---|---|---|---|---|
| TEST-01 | **H** | Zero integration/DB tests; the Testcontainers config is dead code | `PostgresTestContainerConfig.java` referenced by nothing; 0 `@SpringBootTest`, 0 `@DataJpaTest`. Migrations, JPA mappings, tenant row-isolation, and multi-write transactions never run against a real DB | L |
| TEST-02 | **H** | Web apps: zero tests, no test harness installed | no `*.test.*`/`*.spec.*` under either `src/`; `pede-aqui-delivery/package.json` has no `test` script at all | M |
| TEST-03 | **M** | Backend: 27 test classes / 276 sources; critical flows only mock-verified | Exists: checkout security slice, vendor fulfillment, dispatch/courier/reassignment units, tenant-context units, OTP invalid-code unit, finance units. Missing: settlement/payout transitions (`FinanceService.java:121-128`), end-to-end order lifecycle, everything from §2/§3 above (none of the confirmed bugs has a failing test) | L |
| TEST-04 | **M** | Flutter: courier test asserts `1+1==2`; delivery test asserts a hardcoded route count | both `test/widget_test.dart` files | M |
| TEST-05 | **L** | 10 `@WebMvcTest` security slices are the strongest current layer — but they gate roles only, so none of SEC-01…05 (ownership gaps) could be caught | test inventory | — |

**Critical-and-untested (highest-value new tests):** inventory release lifecycle (COR-01), courier/vendor ownership enforcement (SEC-01…04), OTP lockout (SEC-05), checkout idempotency under concurrency (COR-06), tenant isolation against a real DB (SEC-06/TEST-01).

---

## Top 10 by leverage (severity ÷ effort)

1. **SEC-01/02 + SEC-05 + COR-03** — courier ownership + OTP lockout + completion state guard (all S, one service area): closes "any courier can mark any order delivered".
2. **COR-01** — inventory release/settle lifecycle (M): stops the business-breaking stock leak.
3. **SEC-03/04** — vendor ownership on fulfillment + order listing (M): closes cross-vendor refunds and PII exposure.
4. **COR-04/05/06/07/08** — small state-machine and idempotency fixes (all S).
5. **SEC-07 + PROD-04** — remove committed secret defaults, fail-fast prod config (S/M); rotate affected credentials (SEC-20).
6. **SEC-06** — stop trusting `X-Tenant-Id` for non-admins (M).
7. **PROD-02** — point CI at the four real apps (M): everything else lands safer after this.
8. **PROD-03 + PROD-01** — RestTemplate timeouts + let Prometheus scrape (S+S).
9. **TEST-01** — revive Testcontainers, add integration tests for the flows fixed above (L, pays for itself immediately).
10. **GAP-02 + SEC-18** — flip Flutter mock default, dart-define the Keycloak URL, secure token storage (S+M): makes the mobile apps actually usable against the backend.
