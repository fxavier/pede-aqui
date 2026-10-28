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
  tenant-wide data.
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
