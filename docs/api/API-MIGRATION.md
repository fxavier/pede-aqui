# API migration notes

Contract changes introduced by the refactoring increments. Each entry says what changed, who is
affected, and whether existing clients break.

The machine-readable contract is `docs/api/openapi.json`; CI fails a PR that leaves it stale or that
breaks it without the `api-breaking` label. Every PR carrying that label needs an entry here. See
[README.md](README.md).

## INC-04 — Vendor order scope (SEC-03, SEC-04, COR-07)

### `GET /api/v1/orders` (unchanged shape, narrower data)

- Response is still a JSON array of `AdminOrderResponse` — no client change required.
- `VENDOR_ADMIN` / `VENDOR_STAFF` (without `ADMIN`/`OPS`/`FINANCE`) now receive **only their own
  vendor's orders** instead of every order in the tenant.
- If the caller's vendor cannot be resolved (no `vendor_id` JWT claim and the tenant has zero or
  several vendors) the endpoint answers **403** `vendor_scope_unresolved` instead of returning
  tenant-wide data. *(Resolution superseded by ADR-0001 below: the vendor now comes from `merchant_users`.)*
- Still unbounded; it is kept for backward compatibility with the backoffice `/orders` page.
  New clients should use the paginated endpoint below. Deprecation of the array endpoint will be
  announced separately.

### `GET /api/v1/orders/page?page=0&size=20` (new, additive)

- Same roles and vendor scoping as `GET /orders`.
- Response: `{ "content": AdminOrderResponse[], "page": number, "size": number, "totalElements": number }`
  (same shape as `GET /sales/orders`).
- Sorted by `createdAt` descending. `page < 0` is treated as 0; `size` is clamped to `1..100`
  (default 20).

### `PATCH /api/v1/vendor/orders/{id}/accept|reject|preparing|ready-for-pickup`

- A merchant user targeting another vendor's order now gets **404** (`Order was not found`) — the
  same answer as for a non-existent order. Previously the transition succeeded (and `reject`
  created a refund on the other vendor's payment).
- Unresolvable vendor scope → **403** `vendor_scope_unresolved`.

### Order status after vendor rejection

- `PATCH /vendor/orders/{id}/reject` now returns and persists `status: "REJECTED_BY_VENDOR"`
  (previously it immediately became `REFUND_PENDING`). The refund is represented only by the
  `Refund` record (`REQUESTED`), visible through sales detail / finance endpoints.
- Clients: the backoffice already maps `REJECTED_BY_VENDOR` ("Rejeitado", treated as done). The
  customer web app (`pede-aqui-delivery/src/lib/orderStatusLabels.ts`) and the Flutter apps have no
  label for either `REJECTED_BY_VENDOR` or `REFUND_PENDING`, so behaviour there is unchanged
  (raw/fallback status).
- Reports already count `REJECTED_BY_VENDOR` as a post-payment outcome; the dashboard
  "rejected" counter (`DashboardService`) now actually counts vendor rejections (it was always 0).

## Unified seller rejection (product decision; supersedes the INC-04 "Order status after vendor rejection" note)

### `PATCH /api/v1/vendor/orders/{id}/reject` (behaviour change)

- Every seller rejection now ends with `status: "CANCELLED"` and the new additive field
  `cancellationReason: "SELLER_REJECTED"`. `REJECTED_BY_VENDOR` is **no longer produced** by any
  endpoint; the enum value remains valid in responses for orders rejected before this change.
- The refund is still a `Refund` record (`REQUESTED`) visible through sales detail / finance
  endpoints, but it now covers only the eligible remainder (`payment.amount − refunds already
  requested or completed`): the full amount when nothing was refunded before, and no refund row at
  all when the payment is already fully covered. Its idempotency key is `seller-reject-<orderId>`
  (legacy rows carry `vendor-rejection-<orderId>`). Audit action: `ORDER_CANCELLED_SELLER_REJECTED`
  (the former `ORDER_REJECTED` is no longer written).
- Idempotent: repeating the call on an order that is already `CANCELLED` (for any reason) or in the
  legacy `REJECTED_BY_VENDOR` status answers **200** with the current order and no side effects
  (previously a repeat on `REJECTED_BY_VENDOR` was a 400). Other non-rejectable statuses still
  answer **400** `invalid_order_transition`.
- The customer `order_rejected` notification and the free-text `rejection_reason` are unchanged.

### `OrderResponse` / `AdminOrderResponse` (additive)

- New nullable field `cancellationReason` (currently the only value is `SELLER_REJECTED`; null for
  non-cancelled orders and for cancellations by ops/support or status override). Returned by every
  endpoint that serialises these DTOs (`/orders`, `/orders/page`, `/orders/{id}`, `/checkout`,
  `/vendor/orders/{id}/*`).

### Clients / consumers to review

- Backoffice: `CANCELLED` is already labelled "Cancelado" (`src/app/orders/page.tsx`,
  `src/components/ui/status-badge.tsx`, `src/components/sales/status-maps.ts`, `src/lib/utils.ts`,
  `src/app/vendors/page.tsx` `cancelledStatuses`). Seller rejections will now show as "Cancelado"
  instead of "Rejeitado" until the UI reads `cancellationReason` (no UI shows `rejectionReason`
  today). Keep the `REJECTED_BY_VENDOR` label for legacy rows.
- Customer web (`pede-aqui-delivery/src/lib/orderStatusLabels.ts`) and the Flutter apps: they
  label `CANCELLED` and had no label for `REJECTED_BY_VENDOR`, so a rejected order now renders a
  proper "Cancelado" state instead of a raw/fallback status. They may optionally read
  `cancellationReason` to say "rejeitado pelo vendedor".
- Dashboard `GET /dashboard/vendor` `rejectedOrdersCount` and reports `cancelledCount`
  (`/reports/sales/summary|timeseries` + CSV) count `CANCELLED + SELLER_REJECTED` plus legacy
  `REJECTED_BY_VENDOR` rows, so the figures mean the same before and after the change. Note that
  `cancelledCount` therefore now includes seller rejections (it previously did not).

## Refunded / cancelled orders can no longer be delivered (follow-up to the PIN/stock gaps fix)

### `POST /api/v1/deliveries/{id}/complete` and `PATCH /api/v1/deliveries/{id}/status` (new 409 code)

- New error **409 `order_not_deliverable`** when the delivery's order is `REFUNDED`, `CANCELLED`
  (sales cancel, override-cancel, seller rejection, failed delivery) or the legacy
  `REJECTED_BY_VENDOR`. Applies to the assigned courier and to OPS/ADMIN on `/status`.
- Check order: 404 (delivery not in tenant) → 403 `delivery_access_denied` (not the assigned
  courier) → the existing delivery-state 409s (`delivery_already_completed`,
  `invalid_delivery_transition`) → **409 `order_not_deliverable`** → 423 `delivery_code_locked` →
  PIN check. A refused call has no side effect: no attempt counter increment, no delivery event,
  no audit, no stock movement.
- A full refund (`FULLY_REFUNDED`), a sales cancel, an override to `CANCELLED` or a seller rejection
  now **retire** a delivery whose goods are still in the store: the delivery moves to `CANCELLED`
  (`delivery_events.event_type = RETIRED`) and its active dispatch job (`ASSIGNED`/`ACCEPTED`/
  `REASSIGNABLE`) to `CANCELLED`, which also frees the courier. A courier acting on a retired
  delivery gets the existing **409 `invalid_delivery_transition`**. After pickup the delivery is
  not touched; the courier then gets `order_not_deliverable` on a refunded order.
- A full refund now always clears the customer's plain PIN (`orders.delivery_confirmation_code_display`),
  also when a courier is engaged.

### Clients / consumers to review (delivery 409)

- Courier app (`pede_aqui_courier_app`): only calls `POST /deliveries/{id}/complete`
  (`remote_courier_data_source.dart` `confirmDelivery`) and never PATCHes `/status`.
  `DeliveryCubit.confirmDelivery` catches every error with a generic "Não foi possível confirmar a
  entrega." — it does not read the error `code`, so `order_not_deliverable` shows that generic
  message. Recommended: map 409 `order_not_deliverable` / `invalid_delivery_transition` to "Pedido
  cancelado/reembolsado — não entregar" and drop the job from the courier's list. Not changed here.
- Backoffice / ops tools calling `PATCH /deliveries/{id}/status`: handle the new code.

## INC-14 — Error contract and correlation (AS-15, AS-17)

Full contract: [ERROR-CONTRACT.md](ERROR-CONTRACT.md).

### All error responses (additive, non-breaking)

- Every 4xx/5xx body is now an RFC 9457 problem document (`application/problem+json` when the
  client accepts it): `type`, `title`, `status`, `detail`, `instance`, plus extension members
  `code`, `details` (optional), `correlationId`, `traceId`.
- The legacy fields `code`, `message`, `correlationId`, `timestamp`, `fieldErrors` and `details`
  keep their names, types and values. `message` and `timestamp` are deprecated (use `detail`);
  removal will be announced here.
- 401/403 decided by the security chain now carry the same body (previously empty);
  `WWW-Authenticate` is unchanged. The 429 `rate_limited` body gains the common members.

### Status code changes (previously all 500 `internal_error`)

- Unknown path → **404** `not_found` (also `/api-docs`, Swagger UI and public-port
  `/actuator/health` in `prod`).
- Unsupported method → **405** `method_not_allowed` (with `Allow`).
- Unsupported `Content-Type` → **415** `unsupported_media_type`.
- Malformed JSON → **400** `malformed_request`; missing parameter/header → **400**
  `missing_parameter`; mistyped path/query parameter → **400** `invalid_parameter`.

### `X-Correlation-Id`

- Returned on every response (including 401/403). A client-supplied value is reused only if it
  matches `[A-Za-z0-9._:-]{1,128}`; otherwise a UUID is generated. Allowed and exposed in CORS.

## ADR-0001 — Tenant = marketplace operator: merchant scope and registration (2026-10-28)

Decision: [`docs/adr/ADR-0001-tenancy-model.md`](../adr/ADR-0001-tenancy-model.md). Steps 1 (INC-11)
and 2–4 are in place; steps 5–6 are prepared (see the dated note at the end).

### Merchant scope now comes from `merchant_users` (step 3)

- All merchant-scoped endpoints (`GET /orders`, `GET /orders/page`, `PATCH /vendor/orders/{id}/*`,
  `GET /sales/orders`, `GET /sales/orders/{id}` + actions, `POST /catalog/products`, `POST /catalog/skus`,
  product/variation writes, `PATCH /inventory/{id}/stock`, `GET /reports/sales/*`) resolve the caller's
  vendor through the `merchant_users` table (V049) via the shared `MerchantScopeGuard`. The optional
  `vendor_id` JWT claim (new optional mapper on the `delivery-app` client) only selects **one of the
  caller's associations**; a claim outside them is **403** `vendor_access_denied`.
- `VENDOR_STAFF` is now a real role with its own association (no longer treated as `VENDOR_ADMIN`).
- Legacy behaviour ("claim, else the tenant's single vendor") remains **only** for profiles without a
  `merchant_users` row and only while `app.merchant-scope.single-vendor-fallback-enabled=true`
  (default for this release). Operators switch it off per environment once
  `backend/scripts/adr-0001-merchant-users-report.sql` §4 reports 0.
- **Breaking for misconfigured callers only:** an unresolvable vendor scope is now **403**
  `vendor_scope_unresolved` on every merchant path. Previously `GET /sales/orders` silently widened to
  tenant-wide data and `GET /sales/orders/{id}` let the call through.

### `POST /api/v1/register` no longer creates a tenant (step 4)

- Request shape unchanged. `companySlug` and `defaultCurrency` are still validated as before but no
  longer create anything (kept for backward compatibility with the backoffice `/register` form;
  they will become optional in a later contract bump). `tenant_slug_exists` is **no longer returned**
  by this endpoint (only by the M2M provisioning path).
- Effect: Keycloak user with `tenant_id` = operator tenant + `VENDOR_ADMIN`, an `AppUserProfile` in
  the operator tenant, a vendor with `verificationStatus = PENDING` / `available = false`, and a
  `merchant_users` row with role `OWNER`.
- Response (additive): `tenantId`/`tenantSlug` are now the **operator tenant's**; new fields
  `vendorId` and `vendorVerificationStatus` (`"PENDING"`). The backoffice `MerchantRegistrationResponse`
  type gained the two optional fields; the `/register` page ignores the body, so no UI change.
- `POST /api/v1/provisioning/tenants` (M2M, V034 ledger) is unchanged: it still creates a tenant +
  owner + bootstrap vendor, now also with the `merchant_users` row.

### Dev data (step 5)

`backend/scripts/adr-0001-consolidate-dev-tenants.sql` moves legacy per-merchant tenants into the
operator tenant (dry-run by default, apply only after approval, idempotent, never deletes). Not a
Flyway migration; not run anywhere yet.

### Contract removals announced (step 6) — note dated 2026-10-28

Not removed yet; removal lands once step 5 has run in every environment and the fallback flag is off.

1. The single-vendor-per-tenant heuristic (`MerchantScopeGuard` legacy branch, flag
   `app.merchant-scope.single-vendor-fallback-enabled`) and the `vendor_id` claim as a *source* of
   scope (it stays as a selector among `merchant_users` associations).
2. Anonymous/customer **cross-tenant browsing** (`TenantContext.browseTenantId()`, marked
   `TODO(ADR-0001 step 6)`): `GET /catalog/categories`, `GET /catalog/vendors/{id}/products`,
   `GET /search/vendors` will answer only for the tenant mapped to the calling client/host
   (`app.tenancy.customer-clients`). Clients see the same data as today once all merchants live in the
   operator tenant; a white-label host will need its own mapping.

## LAC-09 — Realm password policy at registration (2026-10-28)

The Keycloak realm now enforces `length(10) and digits(1) and upperCase(1) and lowerCase(1) and notUsername and passwordHistory(3)`.

### `POST /api/v1/register` and `POST /api/v1/customers/register` (stricter validation, new 400 code)

- `password` must have ≥ 10 characters, at least one digit, one upper-case and one lower-case letter,
  and must not equal the e-mail. Previously only `@Size(min = 8)` was checked.
- A non-compliant password is now refused **before** Keycloak is called: **400** `validation_failed`,
  one `fieldErrors` entry per unmet rule, all with `field: "password"` (e.g. `must have at least 10 characters`,
  `must contain at least one digit`, `must contain at least one upper-case letter`,
  `must contain at least one lower-case letter`, `must not be equal to the e-mail`).
- New code **400** `password_policy_violation`: Keycloak itself rejected the password (realm policy stricter
  than the API rules, e.g. password history on a future change-password flow). Previously this surfaced as
  **500** `keycloak_error`. Other Keycloak failures still answer 500 `keycloak_error`.
- Breaking for clients that accepted 8–9 character passwords. Updated: backoffice `/register` and customer
  web `/register` (client-side rule + hint, both 400 codes displayed). Flutter customer app register screen: updated in W4-E
  (same client-side rule; both 400 codes shown as a password-policy message).

## W1-A — Security leftovers (LAC-02, LAC-06, LAC-12, LAC-13)

### LAC-02 — Anonymous writes are always 401

- `SecurityConfig` public rules are now pinned to **`GET`**: `/api/v1/catalog/categories`,
  `/api/v1/catalog/vendors/*`, `/api/v1/catalog/vendors/*/products`, `/api/v1/search/vendors` (plus
  health/probes/OpenAPI). Public `GET` responses are unchanged.
- Any `POST`/`PUT`/`PATCH`/`DELETE` without a token now answers **401** `unauthorized` (problem document)
  before reaching the controller. Previously e.g. an anonymous `POST /api/v1/catalog/categories` reached
  the controller and answered 400/403. The only anonymous writes are `POST /api/v1/register` and
  `POST /api/v1/customers/register` (unchanged).
- `X-Tenant-Id` from an anonymous caller, or from a non-admin naming a tenant other than its own claim,
  is ignored (as before) and now logged as a `SECURITY` WARN. No response change.
- Clients: none affected; every frontend already sends a bearer token on writes.

### LAC-06 — Category writes are OPS/ADMIN only

- `POST /api/v1/catalog/categories`, `PUT /api/v1/catalog/categories/{id}` and
  `DELETE /api/v1/catalog/categories/{id}` now answer **403** `forbidden` to `VENDOR_ADMIN` (previously
  allowed). Categories are tenant-wide and, with tenant = operator, shared by every merchant. Reads are
  unchanged. Vertical writes were already OPS/ADMIN.
- Backoffice: `/catalogo/categorias` and `/catalogo/verticais` hide create/edit/delete for roles other
  than OPS/ADMIN. A merchant that needs a new category asks OPS.

### LAC-12 — Upload presign: role gate, required size, signed limits (breaking for upload clients)

- `POST /api/v1/uploads/images/presigned-url` now requires `ADMIN`, `OPS`, `VENDOR_ADMIN`, `VENDOR_STAFF` or
  `COURIER` (same as documents); any other role → **403**.
- Both presign requests now **require** `contentLength` (file size in bytes, `> 0`): missing → **400**
  validation error. Above the limit (images 5 MB, documents 10 MB; `APP_UPLOAD_MAX_IMAGE_BYTES` /
  `APP_UPLOAD_MAX_DOCUMENT_BYTES`) → **400** `upload_too_large`. Wrong type → 400 `invalid_content_type`.
- Response gains `maxBytes` (additive): `{ uploadUrl, storageKey, expiresInSeconds, maxBytes }`.
- The presigned PUT signs `Content-Type` and `Content-Length`: the client must PUT exactly the declared
  file with the declared `Content-Type` (browsers set `Content-Length` from the body). Any mismatch is a
  **403** from S3/MinIO (`SignatureDoesNotMatch`).
- Linking a `storageKey` (`POST /catalog/products` `primaryImageKey`/`imageGallery`,
  `PUT /catalog/products/{id}/image`, vendor create/update `logoStorageKey`, `POST /vendors/{id}/documents`,
  `POST /couriers/{id}/documents`, `PATCH /deliveries/{id}/status` `proofPhotoStorageKey`) now checks the
  key namespace (`tenants/{tenant}/uploads/` images, `tenants/{tenant}/documents/` documents) and the stored
  object via HeadObject: **400** `invalid_storage_key` (other tenant / wrong kind), `upload_not_found`,
  `upload_too_large`, `invalid_content_type`; **503** `storage_unavailable` if storage cannot be reached.
  Vendor/courier documents must now come from the documents presign endpoint.
- Clients: the backoffice now sends `contentLength: file.size` (all six upload call sites). The customer
  web app and the Flutter apps do not call these endpoints today; when the courier app starts uploading
  proof photos it must send `contentLength` and PUT the exact bytes.

### LAC-13 — `POST /api/v1/admin/users` is ADMIN-only, roles allow-listed and synced to Keycloak

- `SUPPORT` now gets **403** on `POST /api/v1/admin/users` (was 201). `GET /admin/users` and
  `GET /admin/users/{id}` stay open to `ADMIN` and `SUPPORT`.
- `roles` must be non-empty (**400** `roles_required`). An `ADMIN` may assign every role except `ADMIN`;
  only the platform admin (ADMIN without `tenant_id`) may also assign `ADMIN`. Otherwise **403**
  `role_assignment_forbidden`.
- The roles are added as realm roles to the existing Keycloak user `keycloakUserId`, so the next token
  carries them. Unknown Keycloak user/role → **404** `keycloak_user_not_found`; other Keycloak failure →
  **500** `keycloak_error`; in both cases no profile is created. Audited as `USER_ROLES_ASSIGNED`.
- Clients: the backoffice `/users` page is ADMIN-only already; no request-shape change.

## W2-B — Bounded lists and audit context (LAC-14, LAC-15)

### LAC-14 — Every list is bounded (array contract kept)

Conventions (`backend/.../common/pagination`): every list is ordered **newest first** by
`(createdAt DESC, id DESC)` within the caller's tenant/scope.

**Legacy array endpoints keep returning a plain JSON array** but now return at most the **newest 200**
rows. When more rows exist the response carries **`X-Truncated: true`** (exposed to browsers via CORS);
otherwise the header is absent. Affected: `GET /tenants`, `GET /admin/tenants`, `GET /orders`,
`GET /couriers`, `GET /vendors`, `GET /admin/users`, `GET /admin/audit`, `GET /notifications`,
`GET /support/tickets`, `GET /support/tickets/mine`. Behaviour notes:

- Ordering is now explicit newest-first (several of these were unordered before).
- No consumer breaks: the backoffice (`services.ts`), the customer web and the Flutter apps all read an
  array and none has more than 200 rows per tenant today. Clients that may exceed 200 rows must move to
  the paged endpoints below; the capped arrays stay until a deprecation window is announced here.
- `GET /orders` keeps the INC-04 scoping (merchant → own vendor, OPS/ADMIN → tenant, platform admin →
  every tenant).

**New keyset (cursor) endpoints** — large / append-only lists. `?limit=50&cursor=<opaque>`; `limit` is
clamped to `1..200` (default 50); omit `cursor` for the first page. Response
`{ "items": [...], "nextCursor": "<opaque>" | null }`; `nextCursor` is null on the last page. The cursor
is an opaque base64url token of `(createdAt, id)`: rows inserted after the first page never shift or
duplicate later pages. A malformed cursor → **400** `invalid_cursor`. A cursor carries no tenant; the
caller's scope always applies.

| Endpoint | Roles / scope |
|---|---|
| `GET /api/v1/admin/audit/cursor` | ADMIN; current tenant |
| `GET /api/v1/notifications/cursor` | any authenticated user; own notifications only |
| `GET /api/v1/orders/cursor` | ADMIN, OPS, VENDOR_ADMIN, VENDOR_STAFF; same scoping as `GET /orders` |

**New offset endpoints** — smaller admin lists. `?page=0&size=20`; `page < 0` → 0, `size` clamped to
`1..100` (default 20). Response `{ "content": [...], "page", "size", "totalElements" }` (same shape as
`/orders/page` and `/sales/orders`).

| Endpoint | Roles / scope |
|---|---|
| `GET /api/v1/tenants/page` | platform admin |
| `GET /api/v1/couriers/page` | ADMIN, OPS (platform admin: all tenants) |
| `GET /api/v1/vendors/page?available=` | VENDOR_ADMIN, VENDOR_STAFF, ADMIN (same scoping as `GET /vendors`) |
| `GET /api/v1/admin/users/page` | ADMIN, SUPPORT; current tenant |
| `GET /api/v1/support/tickets/page` | SUPPORT, ADMIN; current tenant |

`GET /orders/page` (INC-04) is unchanged. Database: V054 adds `(tenant_id, created_at DESC, id DESC)`
indexes (plus `(tenant_id, vendor_id, …)`, `(tenant_id, recipient_user_id, …)`,
`(tenant_id, creator_user_id, …)` and a cross-tenant `orders (created_at DESC, id DESC)`).

### LAC-15 — Audit entries carry request context; `audit_logs` is append-only

- `GET /api/v1/admin/audit` (and `/admin/audit/cursor`) items gain four **additive, nullable** fields:
  `ip` (remote address of the request — `X-Forwarded-For` is not trusted), `userAgent` (≤ 512 chars),
  `correlationId` (the request's `X-Correlation-Id`) and `details` (JSON object; keys that look like
  secrets — pin, code, otp, password, token, secret … — are stored as `"***"`). Entries written before
  V053, or outside an HTTP request, have them null.
- New audited actions: `FEE_POLICY_UPDATED` (`PUT /admin/policy`, details = new values + version),
  `ZONE_CREATED` (`POST /admin/zones`), `TENANT_CREATED` / `TENANT_STATUS_CHANGED` (platform admin,
  recorded against the affected tenant, details `from`/`to`), `COURIER_APPROVED` / `COURIER_REJECTED`,
  `VENDOR_VERIFICATION_APPROVED` / `VENDOR_VERIFICATION_REJECTED` (details `reason`).
- `audit_logs` rejects `UPDATE`, `DELETE` and `TRUNCATE` (trigger, SQLSTATE 42501). Retention target is
  24 months (LAC-63) but **no purge job exists yet**; the future job (W2-A scheduling) must run its
  `DELETE` inside a transaction that first executes `SET LOCAL app.audit_logs_purge = 'on'` (UPDATE stays
  forbidden). Operational scripts that tried to rewrite audit rows will now fail — by design.
- Not in this change: `order_status_history` / `payment_status_history` (register LAC-15 first bullet)
  belong to the order/payment state machine (W4-A, LAC-25) and will be written there.
- Clients: none affected (additive fields; no frontend renders the audit list yet).

## W2-D — Store hours and closures, pharmacy gate, cart SKU check (LAC-27, LAC-64)

### LAC-27 — Opening hours and closures are enforced (behaviour change)

- A store is **open** when its weekly opening hours (`PUT /vendors/{id}/opening-hours`, evaluated in
  `Africa/Maputo`, `APP_STORE_TIMEZONE`) allow the current local time **and** no exceptional closure covers
  now. A vendor that never configured opening hours stays always open (no change for existing data); once
  any hours exist, a weekday without a row is closed. `vendors.available` is **not** enforced unless
  `APP_STORE_ENFORCE_AVAILABLE_FLAG=true` (the column defaults to `false` and has no UI yet).
- `POST /api/v1/customers/{customerId}/cart/items` and `POST /api/v1/checkout` answer **422**
  `store_closed` when the store is closed, with `details: { vendorId, nextOpeningAt }`. A checkout refused
  this way creates no order and leaves the cart `ACTIVE` (no repricing).
- New endpoints (OPS, ADMIN; VENDOR_ADMIN only for its own vendor → otherwise 403 `vendor_access_denied`),
  audited as `VENDOR_CLOSURE_CREATED` / `VENDOR_CLOSURE_DELETED`:
  - `GET /api/v1/vendors/{vendorId}/closures` — current and future closures, earliest first.
  - `POST /api/v1/vendors/{vendorId}/closures` `{ startsAt, endsAt, reason }` → **201**
    `{ id, vendorId, startsAt, endsAt, reason, createdAt }`; the closure covers `[startsAt, endsAt)`.
    `endsAt` ≤ `startsAt` or already past → **400** `invalid_closure_period`.
  - `DELETE /api/v1/vendors/{vendorId}/closures/{closureId}` → **204** (404 if not found).
- `GET /api/v1/search/vendors` rows gain `open` (boolean) and `nextOpeningAt` (ISO-8601 or `null`)
  (additive). Closed stores are listed **after** open ones (relevance/distance/rating order is kept
  within each group); `totalCount` is unchanged. The open state is computed on every request *after* the
  cached result set is read, so closures and opening times take effect immediately despite the 60 s
  `vendorSearch` cache. `available` is unchanged.
- Clients: the customer web app shows the Portuguese message for `store_closed` (with "Abre às …") in the
  vendor page, the cart drawer and checkout, and the vendor cards use `open`/`nextOpeningAt` for the
  "Aberto"/"Fechado · Abre …" badge (falling back to `available`). Backoffice: no closures UI yet (API only).
  Flutter apps: not yet mapped (they show their generic error).

### LAC-64 — Pharmacy vertical blocked by configuration

- `app.verticals.pharmacy.enabled` (`APP_VERTICALS_PHARMACY_ENABLED`, default **false**) until a legal opinion.
  While disabled, a product with `requires_prescription_metadata=true` or whose category's `vertical` is
  `pharmacy` → **422** `pharmacy_not_available` in add-to-cart and checkout. With the vertical enabled,
  prescription products still answer **422** `prescription_required` (no prescription flow exists).
- Catalog browse/search are unchanged (pharmacy products remain visible; ordering is what is blocked).

### Cart — SKU must belong to the request's vendor

- `POST /api/v1/customers/{customerId}/cart/items` with a `skuId` sold by a vendor other than `vendorId`
  → **422** `sku_vendor_mismatch` (previously the item was added to that vendor's cart).

## W3-B — Distributed rate limiting (LAC-11)

No change to request/response shapes and no OpenAPI change (429 is not documented per operation, as
before). What clients can observe:

- **429 `rate_limited` now carries `Retry-After`** (whole seconds, ≥ 1) and
  `details.retryAfterSeconds` (same value). Body otherwise unchanged (problem document, legacy
  `message` = `Too many requests for sensitive endpoint`). Clients should wait at least `Retry-After`
  before retrying and must not retry 429 in a tight loop.
- **Newly limited:** `POST /api/v1/register` and `POST /api/v1/customers/register` — 5 requests per
  15 minutes per client IP. A registration form that retries on validation errors can hit this;
  surface the 429 as "too many attempts, try again later".
- **Limits are now per logical endpoint (route pattern) and account, shared by all backend instances**
  (Redis). Previously each instance counted 20 requests/minute per user *and exact URI*, so
  `/deliveries/A/complete` and `/deliveries/B/complete` had separate counters. Defaults per group:
  delivery PIN completion 10 / 5 min per courier; checkout 10 / min; presigned uploads 30 / min;
  payments/refunds/sales cancel-refund 20 / min; provisioning 20 / min; deliveries, dispatch, ops,
  support tickets, admin and `/me` endpoints 120 / min; public catalog/search `GET` 300 / min.
  Buckets refill continuously (token bucket), so a client that pauses briefly regains capacity.
- Routes outside these groups are no longer limited (the old substring matcher also limited any
  path containing `/me`, `/admin`, `/auth`, `/login`, …).
- Anonymous callers are keyed by the connecting IP; `X-Forwarded-For` is ignored unless the
  deployment configures `app.rate-limit.trusted-proxies`, so clients cannot obtain fresh buckets by
  sending that header.

## W3-D — Tenancy leftovers (LAC-16, LAC-14) — 2026-10-29

### SKU code unique per vendor (V056)

- `skus.sku_code` is now unique per **(tenant, vendor, sku_code)** (`uq_skus_tenant_vendor_code`) instead of per
  tenant (`uq_skus_tenant_code`, dropped). Two stores of the operator may use the same code.
- `POST /api/v1/catalog/skus` with a code the **same vendor** already uses → **409** `sku_code_conflict`
  (previously the tenant-wide duplicate surfaced as the neutral 409 `conflict`). A code used only by another
  vendor is now accepted (201) — that was a 409 before.
- `skus.vendor_id` is a new column derived by trigger from `products.vendor_id` (not exposed in `SkuResponse`).
- No response shape changes.

### `GET /api/v1/admin/tenants` — tenant ADMIN sees only its own tenant (behaviour change, security fix)

- Platform super-admin (`ADMIN` without `tenant_id` claim): unchanged — newest 200 tenants, `X-Truncated` when capped.
- Tenant-scoped `ADMIN` (has a `tenant_id` claim): a **one-element array** with its own tenant (previously every
  tenant). Chosen over 403 so the array contract and the backoffice consumers stay unchanged; `X-Tenant-Id` is
  ignored for this caller, as for every non-platform-admin.

### `GET /api/v1/orders`, `/orders/page`, `/orders/cursor` — platform admin impersonation is honoured

- Platform super-admin **with** `X-Tenant-Id` (impersonating): only that tenant's orders (previously every tenant
  even while impersonating).
- Platform super-admin **without** `X-Tenant-Id`: unchanged, every tenant.
- Merchant users (own vendor) and OPS/ADMIN of a tenant (whole tenant): unchanged.
- `/orders/page` now orders by `(createdAt DESC, id DESC)` (stable tie-break; previously `createdAt DESC` only).

### `GET /api/v1/vendors/{vendorId}/closures` — capped list

- Still a plain array of current/future closures, earliest first, but capped at **200** rows with
  `X-Truncated: true` when more exist (LAC-14 convention). No consumer is expected to have more than a handful.

### Unchanged on purpose (ADR-0001 step 6 still pending)

- The single-vendor fallback (`app.merchant-scope.single-vendor-fallback-enabled`) and the cross-tenant
  anonymous/customer browse stay until the step-5 dev-data consolidation is confirmed in every environment.

## W3-C — Customer order history (LAC-21, history half of LAC-59)

### `GET /api/v1/me/orders?limit=50&cursor=<opaque>` (new, additive)

- **CUSTOMER only** (other roles → 403, anonymous → 401). Returns the caller's own orders in their tenant
  (resolved from the OIDC client, ADR-0001), newest first, with the LAC-14 keyset contract:
  `{ "items": [...], "nextCursor": "<opaque>" | null }`, `limit` clamped to `1..200` (default 50), malformed
  cursor → 400 `invalid_cursor`.
- Item (`CustomerOrderSummaryResponse`): `id`, `reference`, `status`, `cancellationReason`
  (`SELLER_REJECTED` or null), `vendorId`, `vendorName`, `itemCount`, `subtotal`, `fees`, `taxes`,
  `discountTotal`, `total`, `createdAt`. No PIN and no address in the list.
- A customer without a profile in the tenant gets an empty page.

### `GET /api/v1/me/orders/{id}` (new, additive)

- **CUSTOMER only**; an order that is not the caller's (other customer, other tenant, unknown id) → **404**
  (existence never revealed).
- Body (`CustomerOrderDetailResponse`): summary fields plus `items[]` (snapshot lines), `deliveryAddress`
  (checkout snapshot; null for pre-INC-12 orders), `deliveryCode` (plain PIN while the order is active, null
  once delivered/cancelled/refunded), and the quote breakdown `deliveryFee`, `serviceFee`,
  `deliveryFeeBasis`, `distanceKm` (null for pre-INC-13 orders).
- Not included yet (LAC-21 leftovers): `?status=` filter and courier name/masked phone.

### Clients

- **Customer web**: `/orders`, `/orders/:id` and `/orders/:id/confirmation` read these endpoints; the
  localStorage order cache (`pede-aqui:orders`) is gone and purged on startup. The detail page no longer
  calls `GET /orders/{id}/tracking` (still available, unchanged).
- **Flutter customer app**: not migrated yet.

## W4-A — Order state machine, status history and timeouts (LAC-25, LAC-15) — 2026-10-29

### Illegal order transitions are `409 invalid_order_transition` (behaviour change)

- Every order status change now goes through `OrderStateMachine` (transition table in `CLAUDE.md`, Key API
  Flows). A transition outside the table answers **409** `invalid_order_transition` and changes nothing.
- `PATCH /api/v1/vendor/orders/{id}/accept|preparing|ready-for-pickup|reject` from a wrong source status:
  **409** (was **400**, same code `invalid_order_transition`). The message now reads
  `Order cannot move from <FROM> to <TO>` (reject: `Order cannot be rejected in status <STATUS>`).
- Approving a refund that would fully refund an order that was never paid (`PAYMENT_PENDING`) is now refused
  with 409 `invalid_order_transition` (the whole approval rolls back).
- Unchanged: `sale_not_cancellable`, `transition_not_allowed` (sales override allow-list, a narrower policy),
  `order_not_ready_for_dispatch`, `order_not_deliverable`.

### Order status follows the courier (behaviour change)

- Courier `PATCH /deliveries/{id}/status` `PICKED_UP` moves the order `ASSIGNED_TO_COURIER → PICKED_UP`;
  `ON_ROUTE_TO_CUSTOMER` / `ARRIVED_AT_CUSTOMER` move it to `DELIVERING`. Before W4-A the order stayed
  `ASSIGNED_TO_COURIER` until `DELIVERED`. Clients that only knew `ASSIGNED_TO_COURIER` for "on the way" must
  treat `PICKED_UP` and `DELIVERING` the same way (the backoffice and customer web already have labels).
- The sales status override `DELIVERING → DELIVERED` is now reachable for orders whose courier is on the way.

### Automatic cancellations (behaviour change)

- `PAYMENT_PENDING` for longer than `app.orders.payment-timeout` (env `APP_ORDERS_PAYMENT_TIMEOUT`, default
  `PT20M`) → `CANCELLED` with `cancellationReason = PAYMENT_EXPIRED`; payment `CANCELLED`; no refund.
- `PAYMENT_CONFIRMED` not accepted within `app.orders.acceptance-timeout` (env `APP_ORDERS_ACCEPTANCE_TIMEOUT`,
  default `PT10M`) after payment → `CANCELLED` with `cancellationReason = VENDOR_TIMEOUT`, refund of the
  eligible remainder (`REQUESTED`, key `vendor-timeout-<orderId>`), customer notification
  `order_cancelled_vendor_timeout`.
- `cancellationReason` (`OrderResponse`, `AdminOrderResponse`, `/me/orders`) gains the values
  `PAYMENT_EXPIRED` and `VENDOR_TIMEOUT` (was only `SELLER_REJECTED`/null) — new values of a response enum in
  the OpenAPI contract; clients that switch on it must handle them (or unknown values).
- Payment status `CANCELLED` is now written (payment expiry). Sales reports exclude `PAYMENT_EXPIRED`
  cancellations (never paid).
- A mock confirmation (`POST /payments/{id}/confirm`) racing the expiry: one wins; the loser gets 409
  (`concurrent_modification` or `payment_not_confirmable`).

### `GET /api/v1/orders/{orderId}/history` (new, additive)

- Roles: `ADMIN`, `OPS`, `SUPPORT` (tenant-wide), `VENDOR_ADMIN`/`VENDOR_STAFF` (own vendor; another vendor's
  order → 404). Other roles → 403.
- Body: `{ orderId, currentStatus, orderStatus: [...], paymentStatus: [...] }`, oldest first; each entry has
  `fromStatus` (null for the creation row), `toStatus`, `actorType` (`USER`/`SYSTEM`), `actorId` (Keycloak
  subject or job name; **null for merchant callers**), `reason` (fixed code, never free text), `correlationId`,
  `createdAt`; payment entries also carry `paymentId`.
- Orders created before V058 have no history rows before their first transition after deploy.

### Clients / consumers to review

- **Backoffice**: vendor action buttons should handle 409 (they treated any non-2xx as an error, so no
  breakage); a "history" timeline can use the new endpoint.
- **Customer web / Flutter**: `cancellationReason` values above; `PICKED_UP`/`DELIVERING` statuses.

## W4-C — Customer web on Authorization Code + PKCE; ROPC removed for `pede-aqui-web` (LAC-10, web part)

No REST endpoint changed. The change is in the identity contract of the Keycloak client `pede-aqui-web`.

### Keycloak client `pede-aqui-web` (breaking for password-grant callers)

- `directAccessGrantsEnabled=false`: `POST /realms/delivery/protocol/openid-connect/token` with
  `grant_type=password&client_id=pede-aqui-web` now answers **400 `unauthorized_client`**. Any script, test
  or tool that obtained customer tokens this way must use the code flow (or another client).
- Code flow requires PKCE S256 (unchanged); implicit flow explicitly off. Post-logout redirect URIs added
  (`http://localhost:5173/`, `http://localhost:4173/`), so `end_session` with `post_logout_redirect_uri` works.
- Tokens are still issued to `azp=pede-aqui-web`, so the backend's tenant derivation for customers (ADR-0001)
  and `POST /api/v1/customers/register` are unchanged.
- Existing Keycloak containers keep the old client until recreated (`docker compose rm -sf keycloak && docker compose up -d keycloak`).

### Customer web app (`pede-aqui-delivery`)

- Login redirects to the Keycloak login page; `/login` is a landing with "Entrar"/"Criar conta". Registration
  still posts to `/customers/register` and then starts the PKCE login with `login_hint`.
- Tokens are in memory only; `sessionStorage.auth_token` is no longer written (a leftover copy is deleted on
  startup). 401 → one single-flight renewal + replay, then redirect to login.
- Other clients are unaffected: `delivery-app` (backoffice, W4-D), `pede-aqui-mobile` and
  `pede-aqui-courier-mobile` keep their current settings.

## W4-D — Backoffice BFF, ROPC removed from `delivery-app` (LAC-10)

No backend endpoint changed. The change is in **who may obtain tokens and how**:

### Keycloak client `delivery-app` (breaking for password-grant callers)

- `directAccessGrantsEnabled: false`: `grant_type=password` with `client_id=delivery-app` now fails
  (`400 unauthorized_client`). Only Authorization Code + PKCE (S256) is accepted.
- Redirect URIs narrowed to `http://localhost:3000|3001/api/auth/callback/keycloak` (Auth.js callback);
  post-logout redirect `http://localhost:3000|3001/login*`; web origins listed explicitly instead of `+`.
- Effect: the realm's TOTP browser flow (`browser-privileged-mfa`) now applies to every backoffice login of
  `ADMIN`/`FINANCE`/`OPS`.
- Token claims are unchanged (`realm_access.roles`, `tenant_id`, optional `vendor_id`, `azp=delivery-app`).
- Existing local realms keep the old client until re-imported: `docker compose rm -sf keycloak && docker compose up -d keycloak`.

### Clients / consumers to review

- **Backoffice**: migrated. Logs in through Auth.js (server-side PKCE) and calls the API only through
  its BFF (`/api/bff/*`); the browser holds no token. Platform-admin impersonation (`X-Tenant-Id`) is set
  server-side from a signed HttpOnly cookie.
- **Scripts / manual curl**: use the dev-only client from `scripts/lib/keycloak-dev-ropc-client.sh`
  (`backoffice-dev-cli`, local Keycloak only). `scripts/spec-002-smoke.sh` does this automatically
  (override with `KEYCLOAK_CLIENT_ID`).
- **Backend tests**: `KeycloakMerchantRegistrationE2ETest` asserts `delivery-app` refuses ROPC and gets its
  token from a throwaway test client created in its Testcontainers Keycloak (mappers copied from `delivery-app`).
- **Flutter customer app** (`pede_aqui_delivery_app/lib/features/auth/data/auth_repository.dart`): still does
  ROPC + refresh on `delivery-app` — **broken by this change**; must move to `pede-aqui-mobile` with PKCE
  (follow-up — done in W4-E, below). The courier app uses `courier-app`, which is not in the realm (unchanged).
- `pede-aqui-web` (customer web) is untouched here (W4-C).

## W4-E — Flutter apps move to Authorization Code + PKCE (auth part of LAC-61, LAC-62)

No backend API change; this records a client change that the Keycloak ROPC shutdown (W4-C/W4-D) depends on.

- **Mobile apps no longer use ROPC.** Before: the customer app posted `grant_type=password` to the
  Keycloak token endpoint with client `delivery-app`; the courier app did the same with `courier-app`, a client
  that does not exist in the realm (courier login could never work against real Keycloak). Now both use
  Authorization Code + PKCE (S256) through `flutter_appauth` ^11.0.0 on the existing public clients:
  customer `pede-aqui-mobile` (`com.pedeaqui.delivery:/oauthredirect`), courier `pede-aqui-courier-mobile`
  (`com.pedeaqui.courier:/oauthredirect`). Turning off `directAccessGrantsEnabled` on `delivery-app` /
  `pede-aqui-web` therefore does not break the mobile apps.
- Tokens: refresh + id token in `flutter_secure_storage`, access token in memory; refresh is single-flight and
  stores the rotated refresh token (`revokeRefreshToken=true`, `refreshTokenMaxReuse=0`); logout calls the
  end-session endpoint with `id_token_hint` and `post_logout_redirect_uri` = the client's redirect URI, then
  wipes local tokens. Every interactive login sends `prompt=login`.
- Unchanged API usage: customer registration still `POST /api/v1/customers/register` (then PKCE with
  `login_hint`=email); profile reads still `GET /api/v1/me` (customer) and `GET /api/v1/couriers/me` (courier).
- Realm: `pede-aqui-mobile` and `pede-aqui-courier-mobile` now declare `post.logout.redirect.uris` equal to
  their redirect URI (`com.pedeaqui.delivery:/oauthredirect` / `com.pedeaqui.courier:/oauthredirect`), so the
  end-session call with `post_logout_redirect_uri` ends the Keycloak SSO session and returns to the app (before,
  Keycloak rejected the URI and showed an error page). Existing local realms need a re-import
  (`docker compose rm -sf keycloak && docker compose up -d keycloak`).
- Dev: tokens carry `iss` = the host the app used. The backend validates `iss` against
  `http://localhost:8081/realms/delivery`, so on an Android emulator use `adb reverse` + `localhost` URLs rather
  than `10.0.2.2` (see CLAUDE.md, Courier/Customer mobile app).

## W5-A — Customer cancellation with a per-phase policy (LAC-26) — 2026-10-29

All additive; no existing endpoint changes shape.

### `POST /api/v1/me/orders/{id}/cancel` (new, CUSTOMER)

Optional body `{ "reason": "…" }` (≤ 500 chars, else 400 `validation_failed`; kept only on a cancellation
request, never in history/audit). Another customer's or tenant's order → **404**. Policy
(`order/service/CustomerCancellationPolicy`):

| Order status | Result |
|---|---|
| `PAYMENT_PENDING` | **200**, `outcome=CANCELLED`, `cancellationReason=CUSTOMER_CANCELLED`; payment `PENDING_CONFIRMATION → CANCELLED`, no refund |
| `PAYMENT_CONFIRMED` | **200**, `outcome=CANCELLED`, `CUSTOMER_CANCELLED`; refund of the eligible remainder created `REQUESTED` (key `customer-cancel-<orderId>`), `refundAmount` in the body |
| `ACCEPTED_BY_VENDOR`, `PREPARING` | **202**, `outcome=REQUESTED`, `request` = the open cancellation request with the quoted `feeAmount` (`app.orders.cancellation.fee-rate` × subtotal, HALF_UP 2 dp; default 10 %); the order continues |
| `CANCELLED` | **200**, current state (idempotent; `refundAmount` = the customer-cancel refund, if any) |
| `READY_FOR_PICKUP` onwards, `REFUNDED`, legacy rows | **409 `order_not_cancellable`**, `details.orderStatus` |

Free cancellations also retire the delivery/dispatch job, release the stock reservation, notify the customer
(`order_cancelled_by_customer`) and audit `ORDER_CANCELLED_BY_CUSTOMER` (`refund_remaining=<amount>`).
Repeats are idempotent (one refund, one history row, one audit); a repeat in the request phase returns the same
open request. A vendor action racing the call (accept/preparing/ready) has exactly one winner: the loser gets
409 `concurrent_modification` or `invalid_order_transition` with no effect. Rate-limit group `payments` (20/min).

Response `CustomerCancellationResponse`: `{ orderId, orderStatus, cancellationReason, outcome, refundAmount, request }`.

### OPS queue `/api/v1/ops/cancellation-requests` (new, OPS/ADMIN, tenant-scoped)

- `GET ?status=REQUESTED|APPROVED|REJECTED&page=&size=` → `{ content, page, size, totalElements }` (default
  `REQUESTED`, newest first, size ≤ 100). Items `CancellationRequestResponse` = `{ id, orderId, orderReference,
  orderStatus, status, closureReason, reason, feeRate, feeAmount, refundAmount, requestedAt, decidedAt }`.
- `POST /{id}/approve` → order `CANCELLED` / `CUSTOMER_CANCELLED_WITH_FEE`, delivery retired, stock released,
  refund = `refundable − fee` where the fee is the quoted fee capped at what is still refundable (never negative;
  no refund row when nothing remains), customer notified (`order_cancellation_approved`), audit
  `ORDER_CANCELLATION_APPROVED` (`refund_remaining=…;fee=…`). Repeat on an APPROVED request → 200 same body;
  on a REJECTED one → 409 `cancellation_request_closed`. If the order already moved on (READY_FOR_PICKUP onwards
  or ended by another path) → **409 `order_not_cancellable`** and the request is closed (`REJECTED`,
  `closureReason=ORDER_ADVANCED|ORDER_CLOSED`, committed with the 409).
- `POST /{id}/reject` → `REJECTED` / `OPS_REJECTED`, the order continues, customer notified
  (`order_cancellation_rejected`), audit `ORDER_CANCELLATION_REJECTED`. Repeat → 200; on an APPROVED request →
  409 `cancellation_request_closed`. The customer may file a new request afterwards (one **open** request per
  order, V059 partial unique index).
- Another tenant's request → 404. Approve is in rate-limit group `payments`.

### `GET /api/v1/me/orders/{id}` (additive fields)

`cancellationPhase` (`FREE` | `REQUEST` | `ALREADY_CANCELLED` | `NOT_CANCELLABLE`), `cancellationFeeEstimate`
(REQUEST phase only, else null), `cancellationRequest` (the order's latest request or null).

### New `cancellationReason` values (clients that switch on it)

`CUSTOMER_CANCELLED`, `CUSTOMER_CANCELLED_WITH_FEE` (on `OrderResponse`, `AdminOrderResponse`, the customer
summary/detail). Unknown values must be treated as a generic cancellation. Backoffice: no OPS screen yet —
use the API (follow-up). Customer web: `OrderCancellationCard` on the order detail.

## W5-B — Courier status/capacity and courier GPS (LAC-42, LAC-40) — 2026-10-29

### `CourierResponse` (additive)

`GET /couriers/me`, `GET /couriers`, `/couriers/page`, `/couriers/{id}`, approve/reject and the new status
endpoints now return, in addition to the existing fields: `status` (`OFFLINE` | `AVAILABLE` | `BUSY` |
`SUSPENDED`), `statusChangedAt`, `suspensionReason` (OPS text, set only while `SUSPENDED`),
`maxActiveDeliveries` (always 1 — V035 allows one active delivery per courier), `locationConsentAcceptedAt`,
`locationConsentVersion`.

- `available` is **deprecated** (still present): it equals `status` `AVAILABLE` or `BUSY`. Clients should read
  `status`.
- `vehicleType` stays a string but its values are now `MOTORBIKE`, `BICYCLE`, `CAR`, `ON_FOOT`, `OTHER` (or
  null). V060 mapped existing free text by prefix (pt/en: "Mota"/"Motorcycle" → `MOTORBIKE`, "Bicicleta" →
  `BICYCLE`, "Carro"/"Viatura" → `CAR`, "A pé" → `ON_FOOT`); anything else became `OTHER`.

### `POST /api/v1/couriers` — vehicle validated (behaviour change)

`vehicleType` accepts the enum names (case-insensitive) or the same pt/en aliases; any other text is now
**400** `invalid_vehicle_type` (it was stored verbatim). No client currently sends it.

### Courier status (LAC-42)

- **New** `PUT /api/v1/couriers/me/status` `{ "status": "AVAILABLE" | "OFFLINE" }` (COURIER). Going online
  while working an accepted delivery returns `BUSY`. Errors: 400 `invalid_courier_status` (`BUSY`/`SUSPENDED`
  requested), 400 `validation_failed` (missing status), 409 `courier_suspended`, 409 `courier_not_verified`,
  409 `courier_has_active_delivery` (going offline mid-delivery).
- **Deprecated** `PATCH /api/v1/couriers/me/availability` `{ "available": bool }`: same rules (true =
  `AVAILABLE`, false = `OFFLINE`). Behaviour change: going offline while working an accepted delivery is now
  409 `courier_has_active_delivery` (it used to flip the flag). The courier Flutter app now uses the new
  endpoint.
- **New** `PATCH /api/v1/couriers/{id}/suspend` `{ "reason": "…" }` (1..500 chars) and
  `PATCH /api/v1/couriers/{id}/reinstate` (OPS/ADMIN, tenant-scoped → 404, audited `COURIER_SUSPENDED` /
  `COURIER_REINSTATED`). Suspend is idempotent; reinstate of a courier that is not suspended → 409
  `courier_not_suspended`; a reinstated courier is `OFFLINE` (or `BUSY` mid-delivery).
- Dispatch (`/ops/dispatch`, assignment) now offers only to `status = AVAILABLE` couriers. `BUSY` is set when
  the courier accepts an offer (`POST /dispatch-jobs/{id}/accept`; a suspended courier gets 409
  `courier_suspended`) and cleared when the delivery is completed, fails or is retired.
- Presence sweeper job: `BUSY` without an accepted delivery → `AVAILABLE`. The "no location for 2 min →
  `OFFLINE`" rule exists but is **off** (`APP_COURIER_PRESENCE_ENFORCE_TIMEOUT=false`) until the courier app
  sends locations — turning it on before that would take every online courier offline after 2 minutes.

### Courier GPS (LAC-40, all new, additive)

- `GET /api/v1/couriers/me/location-consent` → `{ currentVersion, acceptedVersion, acceptedAt, valid }`.
- `PUT /api/v1/couriers/me/location-consent` `{ "version": "<currentVersion>" }` → records the explicit consent
  (timestamp + text version, audited). Another version → 409 `location_consent_outdated`. The current version
  is `app.courier-location.consent-version` (env `APP_COURIER_LOCATION_CONSENT_VERSION`, default
  `2026-10-v1`); bumping it requires every courier to consent again.
- `DELETE /api/v1/couriers/me/location-consent` → withdraws it; the courier goes `OFFLINE` and their latest
  position is deleted. 409 `courier_has_active_delivery` while working a delivery.
- `POST /api/v1/couriers/me/locations` (COURIER, rate-limit group `courier-location`, 30/min per courier):
  `{ "points": [ { "latitude", "longitude", "accuracyMeters", "speedMps"?, "headingDegrees"?, "recordedAt" } ] }`,
  1..50 points. Out-of-range values → 400 `validation_failed`. 409 `location_consent_required` (no consent to
  the current version), 409 `courier_off_duty` (not `AVAILABLE`/`BUSY` and no accepted delivery). Response
  `{ accepted, dropped, droppedByReason }`; dropped reasons: `low_accuracy` (> 100 m), `too_old` (> 5 min),
  `future_timestamp` (> 5 min ahead), `implausible_speed` (> 120 km/h from the previous point), `duplicate`.
  The app should send a batch every ~10 s during a delivery and every 30–60 s when idle, buffering offline.
- `GET /api/v1/ops/couriers/locations?zoneId=<uuid>` (OPS/ADMIN): latest positions of the zone's on-duty
  couriers, most recently seen first, capped at 200 (`X-Truncated`). Items: `courierId`, `status`, `latitude`,
  `longitude`, `accuracyMeters`, `speedMps`, `headingDegrees`, `recordedAt`, `receivedAt`, `stale` (nothing
  received within 2 min).
- Privacy: positions are only accepted on duty and with consent; the latest position is deleted when the
  courier goes off duty; route history is kept only for deliveries and deleted after 30 days.

### Clients / consumers to review

- **Courier app**: online toggle migrated to `PUT /couriers/me/status`; handle 409 `courier_has_active_delivery`
  and `courier_suspended`. Consent screen and GPS sender shipped in W6-D (see the W6-D section).
- **Backoffice** (`/couriers`): may show `status` instead of `available`, and add suspend/reinstate actions and a
  map from `/ops/couriers/locations`.

## W5-C — Vendor search on PostGIS (LAC-39) — 2026-10-29

### `GET /api/v1/search/vendors` (same parameters; behaviour and one nullability change)

- **Radius filtering is real.** With `lat`/`lng` the result set is the vendors whose stored position lies
  within `radius` metres (default 5000) of the point — `ST_DWithin` on `vendors.location` (geography, GiST
  index), boundary inclusive — that sell at least one ACTIVE product in scope. `distanceKm` (2 decimals) and
  `distanceMeters` are the geodesic (spheroid) distance the filter used, and `sort=DISTANCE` orders by it.
- **Vendors without a usable position are excluded when `lat`/`lng` are sent** (coordinates missing, half-set,
  `(0,0)` placeholder or out of range). Before, they appeared with a made-up "pseudo distance" derived from the
  vendor id. OPS should fix those vendors (report query in V062's header).
- **`distanceKm` is now nullable** (`number | null`, was always a number) and `distanceMeters` is null in the same
  case: when the request has no usable `lat`/`lng` (missing, `(0,0)`, out of range). Then no radius is applied —
  every vendor in scope is returned, as before, but without the fake distance. `RELEVANCE` scoring treats an
  unknown distance as contributing 0; `DISTANCE` sorts unknown distances last.
- `deliveryFee`/`deliveryFeeBasis` are unchanged (same pricing engine; its distance now reads `vendors.location`,
  which yields the same value as the previous lat/lng computation).
- Cache (`vendorSearch`, 60 s) and the request-time `open`/`nextOpeningAt` are unchanged; the key still rounds
  the point to 3 decimals and the search runs on the rounded point.
- `GET /api/v1/search/vendors/legacy` and `GET /api/v1/customers/me/favorites` (built on the same search)
  inherit the same rules.

### Clients / consumers to review

- Customer web (`pede-aqui-delivery`): `VendorSearchResult.distanceKm` is typed `number | null` and `VendorCard`
  hides the distance when it is null (it called `toFixed` on it). Updated in this package.
- Customer Flutter app: already maps a missing/null `distanceKm` to 0 (`catalog_repository.dart`); no change.
- Integrators sending `lat`/`lng`: expect fewer results if some stores have no position.

## W5-D — Customer phone + SMS login (LAC-18, ADR-0004 Proposed)

Additive only; no client breaks.

- **New login method, no new endpoint.** The Keycloak login page of the customer clients `pede-aqui-web` and
  `pede-aqui-mobile` now starts with "phone number → 6-digit SMS code" (authenticator `pede-aqui-phone-otp`,
  flow `browser-customer-phone`), with "Entrar com email e palavra-passe" falling through to the old form. The
  apps keep the same Authorization Code + PKCE redirect; nothing changes in their code. A non-phone `login_hint`
  (the email sent after `POST /customers/register`) skips straight to the password form; a phone `login_hint`
  pre-fills the number. `delivery-app` (backoffice) and `pede-aqui-courier-mobile` are unchanged.
- **Tokens of the customer clients** carry two new OIDC standard claims when the user has a phone:
  `phone_number` (E.164, e.g. `+258841234567`) and `phone_number_verified` (boolean). Phone-created users have an
  opaque `preferred_username` (`phone-<uuid>`) and may have **no `email` claim**.
- **`GET /api/v1/me`** (`MeResponse`): new nullable fields `phoneE164` (string, E.164) and `phoneVerifiedAt`
  (ISO-8601 instant). Filled for customers from a verified `phone_number` claim (unique per tenant: a number already
  verified on another profile is not linked). The old free-text `phone` field is unchanged and not verified.
- Consumers that assumed `email` is always present in a customer token or in `MeResponse.email` semantics: for a
  phone-only customer `MeResponse.email` falls back to the Keycloak subject (column is NOT NULL), as it already did
  for any token without an email claim.

## W6-A — Checkout v2: VAT included, quote with TTL, service area (LAC-22, LAC-20) — 2026-10-30

> **Fiscal assumptions — "a validar com contabilista" (LAC-38).** VAT (IVA) 16 % in Mozambique; catalogue prices are
> published **with VAT included** (B2C) and VAT is *extracted* (`gross × 0.16 / 1.16`, per line/fee, HALF_UP 2 dp),
> never added; the platform is a collection agent (the merchant is the seller and owns the VAT on goods); platform
> delivery/service fees are also treated as VAT-inclusive at 16 %; a discount reduces the goods VAT pro rata;
> ZERO/EXEMPT product categories exist for future use.

### Totals change (behaviour change, same shape)

- **The `taxes` line is now 0** on the cart, `GET /cart/pricing` (`tax`), `GET /checkout/quote`, `POST /checkout`
  and the order (`orders.taxes`), and **totals drop by the former 8 %**: `total = subtotal + fees − discount`.
  Example (18.00 cart, seeded policy): was 18.00 + 5.00 + 0.90 + 1.44 = **25.34**, now **23.90** (IVA incluído 3.29).
  The formula `total = subtotal + fees + taxes − discountTotal` still holds (taxes = 0), so client arithmetic does
  not break.
- Data change (V064): every `fee_policies.tax_rate = 0.08` row (the V048 seed / old default; seeded and
  admin-set rows cannot be told apart) becomes **0.16** (version + 1) and every policy gets `tax_inclusive = true`;
  rows with another rate keep it but are now read as VAT-inclusive. A tenant that must keep the old behaviour sets
  `taxInclusive=false` (VAT added on top, pre-W6-A formula) through `PUT /admin/policy`. Orders record engine
  `pricing-v2`.
- Orders placed before W6-A keep their stored totals (taxes added at 8 %); nothing is recomputed.

### `GET /api/v1/checkout/quote` (additive fields; now stores the quote)

New fields: `quoteId`, `expiresAt` (now + `app.checkout.quote-ttl`, default 10 min), `taxInclusive`, `vatRate`,
`vatIncluded` (VAT contained in `total`), `goodsVat`, `feesVat`, `netTotal` (= total − vatIncluded). Each call stores a
row in `checkout_quotes` (purged daily by the `checkout-quote-cleanup` job, 1 day after expiry). With an `addressId` it
can now answer **422 `out_of_service_area`** or **422 `address_too_far`** (below). Because every call writes a row,
the quote is **rate-limited** in its own group `checkout-quote` (GET, 30/min per account): the 31st call within a
minute answers **429 `rate_limited`** with `Retry-After`. Re-quote on address/cart change, not on a timer.

### `POST /api/v1/checkout` (optional request field + new errors)

- Optional **`quoteId`**. With it the order is charged exactly the quoted prices/fees, or the call fails with
  **409 `quote_changed`**: `details.reason` = `expired` | `amounts_changed` (fee policy, distance band or subtotal
  moved) | `prices_changed` (a SKU price moved; the cart is repriced as for `price_changed` and `details.items` lists
  the SKUs); `details.quote` = a **fresh quote** (new `quoteId`) to show and resubmit. The discount is not compared
  (promotions are re-resolved at checkout as before). A `quoteId` of another customer/tenant → 404; issued for
  another cart or address → **422 `quote_mismatch`**.
- **Without `quoteId` nothing changes** (backward compatible): price drift still answers 409 `price_changed`.
- **Service area (LAC-20)**: with an `addressId`, the address point must be covered by the vendor's
  `delivery_zone_id` polygon (`geo.api.ServiceArea.coverage`, `ST_Covers`), else **422 `out_of_service_area`**.
  Allowed without check: vendor without zone, zone without polygon (`zones.area` NULL), address without
  coordinates, legacy requests without `addressId`. A zone that is not `ACTIVE` serves nobody.
- **Maximum distance**: straight-line distance vendor → address above `vendors.max_delivery_distance_km` (or, when
  null, `fee_policies.max_delivery_distance_km`) → **422 `address_too_far`** with `details.distanceKm` /
  `details.maxDistanceKm`. No limit configured (the default) or unknown distance → allowed.
- The legacy no-address window (`app.checkout.legacy-without-address-enabled`, INC-12) is **unchanged** by W6-A.

### Other additive fields

- `GET /api/v1/me/orders/{id}`: `taxInclusive`, `vatIncluded` (null for pre-W6-A orders).
- `GET/PUT /api/v1/admin/policy`: `taxInclusive` (bool) and `maxDeliveryDistanceKm` (km; `0` removes the limit),
  both optional on PUT (omitted = keep). `taxRate` now means the standard VAT rate (16 %).
- Sales reports `summary` / `timeseries`: `vatIncluded` (VAT contained in `gross`; pre-W6-A orders contribute their
  added tax line) and `grossExVat`. CSV exports unchanged.
- DB only: `orders.quote_id`, `quote_tax_inclusive`, `quote_goods_{standard,zero,exempt}_gross`, `quote_goods_vat`,
  `quote_fees_vat`, `quote_vat_total`, `quote_net_total` (immutable, V048 trigger extended);
  `order_items.tax_category/vat_rate/vat_amount`; `products.tax_category` (STANDARD default; no API yet);
  `vendors.max_delivery_distance_km` (no API yet).

### Clients / consumers to review

- **Customer web** (`pede-aqui-delivery`): updated — shows "IVA incluído (x MT)" from the quote, hides the `IVA`
  line when it is 0, sends `quoteId` when the quote was computed for the submitted address, swaps in
  `details.quote` on `quote_changed`, and shows Portuguese messages for `out_of_service_area` / `address_too_far` /
  `quote_changed` / `quote_mismatch` / `price_changed`.
- **Customer Flutter app / backoffice**: totals change only (taxes 0, lower totals); no required change. The
  backoffice fee-policy screen should label `taxRate` as "IVA (incluído)".
- **Deploy**: run V064 with **no overlap** between old and new instances — an old instance reading the corrected
  `tax_rate = 0.16` would *add* 16 % on top.

## W6-B — Settlement ledger and commissions (LAC-33, LAC-34, ADR-0005 Proposed) — 2026-10-30

Additive only; no existing request/response shape changes.

### `GET /api/v1/finance/merchants/{vendorId}/balance` (new)

- Roles: FINANCE/ADMIN (any vendor of the tenant), VENDOR_ADMIN (own vendor only — another vendor → 403
  `vendor_access_denied`, unresolved scope → 403 `vendor_scope_unresolved`). Other roles → 403. Unknown vendor in the
  tenant → 404. No tenant → 403 `tenant_required`.
- Response `MerchantBalanceResponse`: `{ vendorId, currency: "MZN", balance, totalCredits, totalDebits, lastPostedAt }`
  — `balance` = what the platform owes the merchant (MERCHANT_PAYABLE credits − debits); 0 and `lastPostedAt: null`
  when nothing was posted yet. Money is a JSON number with 2 decimals (ADR-0003).

### `GET /api/v1/finance/ledger/transactions?limit=&cursor=&orderId=&vendorId=` (new, FINANCE/ADMIN)

- Keyset page `{ items, nextCursor }`, newest first (`createdAt DESC, id DESC`), `limit` 1..200 (default 50),
  malformed cursor → 400 `invalid_cursor`. Optional filters `orderId`, `vendorId`.
- Item: `{ id, type (PAYMENT_CAPTURED | ORDER_DELIVERED | REFUND_BEFORE_DELIVERY | REFUND_AFTER_DELIVERY |
  CANCELLATION_FEE_RETAINED), idempotencyKey, sourceType, sourceId, orderId, vendorId, currency, occurredAt,
  createdAt, description, entries: [{ accountType, ownerId, debit, credit, currency }] }`. Clients must tolerate new
  `type`/`accountType` values (courier pay and payouts will add some).

### Behaviour notes for existing endpoints

- `commissions` now gets rows (one per delivered order, or per order cancelled with a retained fee), so the
  `commission` figures of `GET /api/v1/reports/*`, the finance dashboard and the sale detail stop being 0 for orders
  delivered after the deploy. They show the **gross** accrued commission; refund reversals are only in the ledger.
- `GET /api/v1/finance/commissions` (FinanceController) lists those rows; shape unchanged.
- No backfill: orders delivered before the deploy have no ledger postings or commission rows.

### Clients / consumers to review

- Backoffice `/finance` and `/reports`: may add a merchant balance card and a ledger journal view (no screen yet).

## W6-C — Notifications foundation: push/SMS/e-mail channels (LAC-48) — 2026-10-30

Additive only; no existing contract changes. The in-app inbox (`/api/v1/notifications*`) keeps its shape and content.

### `POST /api/v1/me/devices` (new, any authenticated user with a profile in the tenant)

- Body `{ "platform": "ANDROID" | "IOS" | "WEB", "token": "<FCM/APNs/Web Push token, ≤ 4096>" }` →
  **200** `{ id, platform, tokenMasked: "****abcd", lastSeenAt }`. Idempotent: re-registering refreshes
  `lastSeenAt`/platform. A token already registered by another account **moves** to the caller (shared device,
  new login). The token is never echoed back. 400 `validation_failed` for an unknown platform or blank token;
  403 `profile_required` when the caller has no profile in the tenant. Rate-limit group `sensitive`.

### `DELETE /api/v1/me/devices` (new)

- Body `{ "token": "..." }` (in the body, so the token never reaches access logs) → **204** always; deletes the
  token only if it belongs to the caller. Apps must call it **before logout** (the backend also drops tokens the
  provider reports as unregistered).

### `GET /api/v1/ops/notification-templates` · `PUT /api/v1/ops/notification-templates/{templateKey}/{channel}` (new, OPS/ADMIN)

- `GET` → array of the tenant's effective templates `{ templateKey, channel, locale, title, body, active, version,
  source: "DEFAULT" | "TENANT", updatedAt }` (config data, bounded by the key × channel set; not paged).
- `PUT` body `{ title?, body, active }` creates/replaces the tenant override (title ignored for SMS; SMS body
  ≤ 320 chars, others ≤ 2000) → 200 with the override (`source: "TENANT"`, `version` + 1). Only keys/channels that
  have a platform default can be overridden (else 404). Allowed placeholder: `{orderReference}`; anything else —
  in particular PIN/OTP/code/token names — → 400 `template_placeholder_not_allowed`. Audited
  `NOTIFICATION_TEMPLATE_UPDATED`. `channel` ∈ `IN_APP|PUSH|SMS|EMAIL` (other values → 400).

### `GET /api/v1/support/notification-deliveries` (new, SUPPORT/OPS/ADMIN, tenant-scoped)

- Query `status` (`QUEUED|SENT|FAILED|SKIPPED`), `channel`, `recipientProfileId`, `businessReference` (all optional),
  `page`, `size` (1..100, default 20) → `{ content, page, size, totalElements }`, newest first. Rows:
  `{ id, channel, templateKey, templateVersion, status, attempts, lastError, recipientProfileId, recipientMasked,
  businessReference, providerMessageId, createdAt, nextAttemptAt, sentAt }` — masked: no rendered text, recipient
  only as e.g. `+25884*****67` / `a***@e***.mz` / `****abcd`. `lastError` is a short code (`preference_disabled`,
  `no_device`, `no_verified_phone`, `no_email`, `invalid_recipient`, `provider_timeout`, …).

### Behaviour (no request/response change)

- Customers with a registered device now get pushes for: order accepted, ready for pickup, delivered (after-commit
  listener of `OrderStatusChanged`), seller rejection, vendor-timeout cancellation, sales cancellation, cancellation
  request approved/rejected (fan-out of the existing in-app notifications). SMS templates exist for seller
  rejection and vendor timeout but SMS is **off by default** (`user_notification_preferences.sms_enabled`, no
  endpoint to change it yet) and only goes to a **verified** phone. Delivery-code and OTP texts are never sent on
  external channels.

### Clients / consumers to review

- Customer Flutter app / courier app / web: register the push token after login and on token refresh; call
  `DELETE /me/devices` before logout. Not implemented in the clients by this package (no FCM project yet).
- Backoffice: template editor and delivery-log screens are not built (LAC-60).

## W6-D — Courier app consumes the GPS endpoints (LAC-40, LAC-62; client only, no API change) — 2026-10-30

- The courier Flutter app now calls `GET/PUT/DELETE /couriers/me/location-consent` (consent at onboarding and in
  settings) and `POST /couriers/me/locations` (batches of ≤ 50 every 15 s or at 20 points, only on duty with
  consent, offline buffer pruned below the 5-minute `too_old` limit, `Retry-After` honoured on 429). It reads
  `status` from `GET /couriers/me`.
- Ops: `APP_COURIER_PRESENCE_ENFORCE_TIMEOUT=true` (no location for 2 min → `OFFLINE`) may be turned on once couriers
  run a build with W6-D; with an older build every online courier would be taken offline after 2 minutes.

## W7-C — Profile management, account deletion, data export and consents (LAC-19, LAC-63) — 2026-10-31

Additive only; no existing request/response changes (`GET /api/v1/me` keeps its shape). Retention periods and the
deletion reflection period are configurable defaults "a validar com jurista" — see `docs/privacy/DATA-PRIVACY.md`.

### `GET /api/v1/me/profile` (new, any authenticated user with a profile in the tenant)

- → **200** `{ id, email, displayName, phoneE164, phoneVerifiedAt, preferredLocale, notificationPreferences: { push, sms,
  email } }`. No profile in the resolved tenant (or a pseudonymised one) → 404 `not_found`; no tenant → 403
  `tenant_required`.

### `PATCH /api/v1/me` (new)

- Body (all optional, null = unchanged) `{ displayName? (1..120, not blank, trimmed), preferredLocale? ("pt-PT" | "pt-MZ"
  | "en", `app.privacy.supported-locales`), notificationPreferences?: { push?, sms?, email? } }` → **200** with the
  `GET /me/profile` shape. 400 `validation_failed` (blank/too long name), 400 `unsupported_locale`.
- Display name and locale are mirrored to the Keycloak user (first name = first word, last name = the rest; attribute
  `locale`) in the same transaction: a Keycloak failure → 500 `keycloak_error` and nothing is saved. Notification
  preferences write `user_notification_preferences` (this is the endpoint W6-C lacked). Audited `PROFILE_UPDATED`
  (changed field names only).
- **E-mail and phone are not editable here.** E-mail changes go through the Keycloak account console (with
  verification); the verified phone comes from the phone + SMS login (LAC-18, synced on each request). Unknown fields
  in the body are ignored.

### `POST /api/v1/me/deletion-request` · `DELETE …` · `GET …` (new, CUSTOMER only)

- `POST` → **202** `{ id, status: "PENDING", requestedAt, scheduledFor, cancelledAt: null, completedAt: null }`
  (`scheduledFor` = `requestedAt` + `app.privacy.deletion.reflection-period`, default P30D); **200** with the same
  body when a request is already open (idempotent). **409 `active_orders_present`** while the customer has an order in
  progress (any status except `DELIVERED`, `CANCELLED`, `REFUNDED` and the never-written legacy values). A principal
  that is not customer-only (e.g. CUSTOMER + COURIER) → 403 `account_deletion_not_supported`; staff → 403.
  Rate-limit group `account-deletion` (10/h). Audited `ACCOUNT_DELETION_REQUESTED`.
- `DELETE` → **200** with `status: "CANCELLED"`; 404 `deletion_request_not_found` when nothing is pending; 409
  `deletion_already_processed` once the job has pseudonymised the account. Audited `ACCOUNT_DELETION_CANCELLED`.
- `GET` → **200** latest request (any status) or 404 `deletion_request_not_found`.
- After `scheduledFor`, job `account-deletion` (hourly) pseudonymises the account (field map in
  `docs/privacy/DATA-PRIVACY.md` §6): profile name/e-mail/phones/NIF/… replaced or cleared, addresses, favourites,
  push devices, inbox and preferences deleted, order delivery snapshots stripped of identifying fields (amounts,
  items, reference, city kept), Keycloak user disabled then deleted. Still an order in progress at that time → the
  request stays `PENDING` and is retried on the next run.

### `GET /api/v1/me/data-export` (new, any authenticated user with a profile)

- → **200** `application/json` with `Content-Disposition: attachment; filename="pede-aqui-dados-YYYY-MM-DD.json"` and
  `Cache-Control: no-store`: `{ formatVersion: 1, generatedAt, profile, notificationPreferences, registeredPushDevices,
  addresses[], favouriteVendorIds[], orders[] (summaries), consents[], deletionRequests[] }`. Push tokens are never
  exported. Rate-limit group `data-export` (3/h per account, `APP_RATE_LIMIT_DATA_EXPORT_CAPACITY`) → 429
  `rate_limited`. Audited `PERSONAL_DATA_EXPORTED`.

### `GET /api/v1/me/consents` · `PUT /api/v1/me/consents/{purpose}` · `DELETE /api/v1/me/consents/{purpose}` (new)

- `purpose` ∈ `TERMS_OF_SERVICE | PRIVACY_POLICY | MARKETING` (else 400 `invalid_consent_purpose`).
- `GET` → `[{ purpose, currentVersion, version, grantedAt, withdrawnAt, active }]` (one entry per purpose).
- `PUT` body `{ version }` must equal `app.privacy.consent-versions.<PURPOSE>` (else 409 `consent_version_outdated`) →
  200; same version again is idempotent, a newer version closes the previous grant. Audited `CONSENT_GRANTED`.
- `DELETE` → 204; 404 `consent_not_found` when none is active. Audited `CONSENT_WITHDRAWN`.

### Behaviour (no request/response change)

- New jobs `account-deletion` (hourly at :20) and `retention-cleaner` (daily 05:15 Africa/Maputo) — see CLAUDE.md
  "Scheduled jobs". Retention deletes `audit_logs` > 24 months, `notification_deliveries` (not QUEUED) and in-app
  `notifications` > 180 days, order/payment status history > 10 years, finished `provisioning_requests` > 24 h.
- Structured (JSON) logs mask e-mails, phone numbers and tokens in every string field.

### Clients / consumers to review

- Customer web: `/profile` now edits name/locale/notification preferences, exports the data and requests/cancels the
  account deletion (W7-C). Flutter customer app and backoffice: not changed by this package.
