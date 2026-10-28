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
