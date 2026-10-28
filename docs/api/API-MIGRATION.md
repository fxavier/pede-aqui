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
