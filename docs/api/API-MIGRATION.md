# API migration notes

Contract changes introduced by the refactoring increments. Each entry says what changed, who is
affected, and whether existing clients break.

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
  web `/register` (client-side rule + hint, both 400 codes displayed). **Not updated:** the Flutter customer app
  register screen still checks only 8 characters, so it will get the 400 from the API.

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
