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

## INC-12 — Customer addresses and order delivery address (AS-02, FR-003)

### `GET|POST /api/v1/customers/me/addresses`, `GET|PUT|DELETE /api/v1/customers/me/addresses/{id}` (new, additive)

- `CUSTOMER` only. The owner is the caller's profile in the resolved tenant (ADR-0001); another
  customer's address answers **404** for every operation.
- Body (`POST`/`PUT`, full replace): `label`*, `recipientName`*, `phone`, `line1`*, `line2` (bairro),
  `city`*, `state`, `postalCode`, `latitude`, `longitude`, `deliveryInstructions` (* required).
  Coordinates are optional but must come together (**400** `invalid_coordinates`); ranges are
  validated (**400** `validation_failed`). At most 20 addresses per customer (**409**
  `address_limit_reached`). `DELETE` → **204**.
- The GPS pin is also stored as `geography(Point,4326)` (V046); clients keep sending lat/lng.

### `POST /api/v1/checkout` — new request version with `addressId`

- New field `addressId` (one of the caller's saved addresses). The address is copied into an
  **immutable snapshot** on the order (recipient, phone, lines, city, coordinates, instructions);
  later edits or deletion of the saved address do not change the order.
- Another customer's (or unknown) `addressId` → **404**, no order and no stock reservation.
- Replaying an idempotency key with a *different* `addressId` → **409** `idempotency_key_reused`.
- Response `OrderResponse` gains `deliveryAddress` (object or `null`), filled only for the order's
  customer.

#### Compatibility window for requests without `addressId`

| | |
|---|---|
| Behaviour during the window | Accepted as before; the order has **no** address snapshot (`deliveryAddress: null`); the response carries `Deprecation: true`; the backend logs a WARN per request (no PII) to track remaining callers |
| Switch | `app.checkout.legacy-without-address-enabled` (env `APP_CHECKOUT_LEGACY_WITHOUT_ADDRESS_ENABLED`), default `true` |
| After the window | Set to `false` → **400** `address_required` |
| Known callers | Customer web (`pede-aqui-delivery`) sends `addressId` from this increment on. The Flutter customer app does not call `/checkout` yet (its checkout screen is a static mock), so it is not a legacy caller. Old web builds still cached in browsers are the only expected legacy traffic |
| Closing criterion | No `Checkout without addressId` WARN for 14 days after the web release **and** the Flutter app shipping real checkout with `addressId`; then flip the switch, and remove the legacy branch in a later increment |

### `GET /api/v1/deliveries/{deliveryId}/address` (new)

- Full delivery address (the order snapshot) for the **courier currently assigned** to the delivery
  only (`COURIER` role + courier profile match, same guard as `/complete`). Other couriers, vendors,
  customers, ops → **403**.
- Only while the courier is working the delivery: `ACCEPTED`, `ARRIVED_AT_VENDOR`, `PICKED_UP`,
  `ON_ROUTE_TO_CUSTOMER`, `ARRIVED_AT_CUSTOMER`. Offered-but-not-accepted (`ASSIGNED`) or finished
  → **409** `delivery_address_unavailable`. A reassigned courier loses access with the assignment.
- Orders placed without an address (pre-INC-12 / legacy window) → **404**.

### `GET /api/v1/orders`, `GET /api/v1/orders/page` (additive field)

- `AdminOrderResponse` gains `deliveryArea` (city of the snapshot, or `null`). Vendors and ops see
  only this coarse area — enough to anticipate route/preparation — never street, phone, recipient
  or GPS pin.
