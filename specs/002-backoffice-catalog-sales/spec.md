# Spec 002 — Backoffice: Catalog Management, Sales Management & Sales Reports

Status: Draft · Owner: Pede Aqui · Target apps: `backend/`, `pede-aqui-backoffice/`
Depends on: Spec 001 (Delivery Marketplace MVP)

## 1. Summary

Add three capability areas to the backoffice, backed by new/extended backend endpoints:

1. **Catalog & Product management** — edit product attributes, edit the (single-SKU)
   price, and upload/replace the product image. Price changes above a configurable
   threshold go through a lightweight approval that never takes the product offline
   and never serves an unapproved price.
2. **Sales management** — a commercial (revenue) lens over existing orders with
   filtering and per-product breakdowns; guarded operational actions (cancel, refund,
   resend notification); and discount/promotion management applied at checkout.
3. **Sales reports** — parameterised, tenant/vendor-scoped reports (summary, time
   series, by vendor, by product/SKU, by category/vertical) computed on demand from
   immutable order snapshots, with CSV export.

All three respect the existing architecture: simple layered backend (no DDD/CQRS/Kafka),
Flyway migrations with `ddl-auto: validate`, Keycloak JWT + `@PreAuthorize`, `tenant_id`
scoping, presigned-URL uploads, and audit logging of sensitive actions.

## 2. Scope

### In scope

- Backend: entities, Flyway migrations, DTOs, mappers, services, controllers, `@PreAuthorize`
  rules, audit entries, and tests for all three areas.
- Backoffice (Next.js): routes/screens, `services.ts` clients, RBAC gating, loading/empty/error/forbidden states (per existing screen conventions).
- Promotion **application** logic at cart/checkout (backend) and the API surface the
  customer clients call to apply a coupon. The customer-app/web **UI** for entering a
  coupon and rendering the discount is flagged as a downstream change, not built here.

### Out of scope

- Multi-SKU / product variations UI (data model already supports SKUs; UI stays single-price).
- Stacking multiple promotions on one order (single best/explicit discount only).
- Manual/offline (POS) sale entry.
- Materialised views / pre-aggregated rollups (documented as a future option in `plan.md`, not built).
- xlsx/PDF export (CSV only in this iteration).
- Multi-currency (single-currency MZN assumption).
- Bulk product import.

## 3. Actors & authorization

Roles from Spec 001: `ADMIN`, `OPS`, `VENDOR_ADMIN`, `COURIER`, `CUSTOMER`, `FINANCE`, `SUPPORT`.

| Capability | VENDOR_ADMIN | OPS | ADMIN | FINANCE | SUPPORT |
|---|---|---|---|---|---|
| Edit product attributes / image | Own vendor | Tenant-wide | Tenant-wide | — | — |
| Edit product price | Own vendor | Tenant-wide | Tenant-wide | — | — |
| Approve/reject pending price change | — | ✔ | ✔ | — | — |
| Sales list & detail | Own vendor | Tenant-wide | Tenant-wide | Tenant-wide | Read-only |
| Cancel order (sales action) | Own vendor¹ | ✔ | ✔ | — | ✔ |
| Refund (full/partial) | — | ✔ | ✔ | ✔ | — |
| Resend order notification | Own vendor | ✔ | ✔ | — | ✔ |
| Force status override (config-gated) | — | ✔ | ✔ | — | — |
| Promotion CRUD (vendor-scoped) | Own vendor | ✔ | ✔ | — | — |
| Promotion CRUD (tenant-wide, `vendor_id = null`) | — | ✔ | ✔ | — | — |
| Reports (all dimensions) | Own vendor | ✔ | ✔ | ✔ | — |

¹ Only in pre-fulfilment states (see order state machine).
Platform super-admins (`ADMIN`, no `tenant_id`) operate on any tenant via `X-Tenant-Id` impersonation, as today.

Every tenant-scoped query and mutation is filtered by `TenantContext`; `VENDOR_ADMIN`
access is additionally filtered to the caller's `vendor_id` in the service layer.

## 4. User stories & acceptance criteria

### US-1 — Edit product attributes
As a VENDOR_ADMIN I edit my product's name, description, category, and prescription flag.
- **AC-1.1** `PATCH /catalog/products/{id}` updates only supplied fields (partial update); omitted fields are untouched.
- **AC-1.2** A VENDOR_ADMIN editing a product not owned by their vendor gets `403`.
- **AC-1.3** Editing name/description/category/flag on an `ACTIVE` product keeps it `ACTIVE` and visible.
- **AC-1.4** Every successful edit writes an `audit_logs` row (actor, target=productId, action, before/after diff of changed fields, result).
- **AC-1.5** Validation: name 1–140 chars, description ≤ 2000 chars, category must belong to the same tenant; invalid input → `400` with field-level errors.

### US-2 — Edit product price (single SKU)
As a VENDOR_ADMIN I change my product's selling price.
- **AC-2.1** `PATCH /catalog/products/{id}/price` accepts `{ "price": <MZN decimal ≥ 0> }` and targets the product's single active SKU.
- **AC-2.2** If price-change review is **disabled**, or the change is within `threshold-percent` of the current approved price, the SKU `price` updates immediately; product stays `ACTIVE`; response `reviewRequired=false`.
- **AC-2.3** If review is **enabled** and `|Δ%| > threshold-percent`, the new value is stored in `pending_price` (not `price`); product stays `ACTIVE` and continues selling at the current approved `price`; response `reviewRequired=true`.
- **AC-2.4** A product with an existing pending price rejects a second price edit with `409` until the pending change is approved or rejected (one pending change at a time).
- **AC-2.5** Price ≤ 0 or non-numeric → `400`. Products with zero or >1 active SKU → `409` with a message directing to SKU-level management (guards the single-SKU assumption).
- **AC-2.6** All price mutations and moderation decisions are audit-logged (old price, new/pending price, reviewRequired).

### US-3 — Upload / replace product image
As a VENDOR_ADMIN I set or replace my product's image.
- **AC-3.1** Client obtains a presigned URL from the existing `/uploads/images/presigned-url`, PUTs the file, then calls `PUT /catalog/products/{id}/image` with `{ "storageKey": "<key>" }`.
- **AC-3.2** The service validates the `storageKey` prefix belongs to this tenant's image namespace before persisting `image_storage_key`; foreign/malformed key → `400`.
- **AC-3.3** `DELETE /catalog/products/{id}/image` clears `image_storage_key`.
- **AC-3.4** Product read responses expose a resolvable image URL (presigned GET or public URL per storage profile) or `null`.
- **AC-3.5** Image set/replace/clear is audit-logged.

### US-4 — Moderate pending price changes
As OPS/ADMIN I review price changes that exceeded the threshold.
- **AC-4.1** `GET /catalog/moderation/price-changes` lists SKUs with a non-null `pending_price` (tenant-scoped), including current price, pending price, Δ%, submitter, submitted-at.
- **AC-4.2** `POST /catalog/moderation/price-changes/{skuId}/approve` sets `price = pending_price`, clears the pending slot, audit-logs; product remains `ACTIVE`.
- **AC-4.3** `POST /catalog/moderation/price-changes/{skuId}/reject` (reason required) clears the pending slot without changing `price`, audit-logs.
- **AC-4.4** VENDOR_ADMIN calling any moderation endpoint → `403`.

### US-5 — Sales list & detail
As OPS/ADMIN/FINANCE (or VENDOR_ADMIN for own vendor) I browse sales with a revenue lens.
- **AC-5.1** `GET /sales/orders` returns paginated sales rows filterable by `from`, `to`, `status`, `vendorId`, `skuId`/`productId`, `paymentProvider`, and free-text `q` (reference/customer).
- **AC-5.2** Each row exposes: reference, createdAt, vendor, item count, subtotal, fees, taxes, `discountTotal`, total, order status, payment status, provider. Customer PII is masked for SUPPORT.
- **AC-5.3** VENDOR_ADMIN results are forced to their `vendor_id` regardless of the `vendorId` filter.
- **AC-5.4** `GET /sales/orders/{id}` returns line items (from immutable snapshots), applied promotion, payment(s), refunds, and commission.
- **AC-5.5** Monetary values are MZN with 2 decimals; totals reconcile: `total = subtotal + fees + taxes − discountTotal`.

### US-6 — Sales operational actions
As OPS/ADMIN/SUPPORT I act on a sale.
- **AC-6.1** `POST /sales/orders/{id}/cancel` (reason) is permitted only in pre-dispatch states (`PENDING`, `PAYMENT_PENDING`, `PAYMENT_CONFIRMED`, `ACCEPTED_BY_VENDOR`, `PREPARING`); otherwise `409`. It drives the existing order state machine, not a raw status write.
- **AC-6.2** `POST /sales/orders/{id}/refund` creates a `Refund` via the existing finance/refund path (full or partial, `amount ≤ paid − alreadyRefunded`), emitting the same `RefundRequested → settlement` flow; invalid amount → `422`.
- **AC-6.3** `POST /sales/orders/{id}/resend-notification` (`type ∈ {CONFIRMATION, STATUS, DELIVERY_CODE}`) re-triggers the notification via `NotificationService`. `DELIVERY_CODE` re-sends the code to the customer only (never returns the OTP in the API response; the OTP is never logged).
- **AC-6.4** `POST /sales/orders/{id}/status-override` exists only when `sales.status-override.enabled=true`, requires a `reason`, accepts only transitions in an allow-list matrix, is ADMIN/OPS-only, and is audit-logged. Default config: disabled.
- **AC-6.5** All six actions are audit-logged with actor, target, action, reason, result.

### US-7 — Promotions / discounts
As VENDOR_ADMIN/OPS/ADMIN I manage discounts applied at checkout.
- **AC-7.1** `POST /marketing/promotions` creates a promotion: `type ∈ {PERCENTAGE, FIXED_AMOUNT}`, `value`, `scope ∈ {ORDER, CATEGORY, PRODUCT}`, optional target, optional `code` (present → coupon, absent → automatic), `minOrderTotal?`, `maxDiscountAmount?`, `startsAt`, `endsAt`, `usageLimit?`, `perCustomerLimit?`.
- **AC-7.2** Vendor-scoped promotions carry `vendor_id`; tenant-wide promotions (`vendor_id = null`) are OPS/ADMIN-only. VENDOR_ADMIN may only create/read/update/delete promotions for their own vendor.
- **AC-7.3** `POST /marketing/promotions/{id}/activate` and `/pause` move `status` between `DRAFT/ACTIVE/PAUSED`; expired promotions (`now > endsAt`) resolve as `EXPIRED` and cannot be activated.
- **AC-7.4** Overlap/validity validation: `startsAt < endsAt`; `PERCENTAGE.value ∈ (0,100]`; `FIXED_AMOUNT.value > 0`; targeted scope requires a matching target id in the same tenant.
- **AC-7.5** `POST /cart/{cartId}/coupon {code}` validates and applies the coupon to the cart; `DELETE /cart/{cartId}/coupon` removes it. Cart pricing response reflects `discountTotal` and the applied promotion.
- **AC-7.6** At checkout, exactly one discount is applied and persisted on the order (`applied_promotion_id`, `discount_total`); an automatic promotion applies when no coupon is entered and conditions match; if both an automatic promo and a coupon qualify, the coupon wins. `used_count`/per-customer usage is incremented atomically inside the checkout transaction; exceeding a limit fails checkout with a clear error and does not consume usage.
- **AC-7.7** Discount is reflected in `Order.total` and therefore in net sales in reports.

### US-8 — Sales reports
As FINANCE/ADMIN/OPS (or VENDOR_ADMIN scoped) I run sales reports.
- **AC-8.1** Reports are computed from **immutable order data** (`Order.subtotal/fees/taxes/discountTotal/total`, `OrderItem` snapshots), never from live SKU prices.
- **AC-8.2** Revenue basis: an order counts toward gross sales once **payment is confirmed** (state `PAYMENT_CONFIRMED` or later, excluding orders cancelled before payment). The reporting timestamp is the order's `created_at` (paid-at basis documented as an alternative in `plan.md`).
- **AC-8.3** `GET /reports/sales/summary` returns for `[from,to]`: order count, gross, discountTotal, refunds, net (= gross − refunds), platform commission, AOV, delivered count, cancelled count.
- **AC-8.4** `GET /reports/sales/timeseries?interval=day|week|month` returns the same KPIs bucketed via Postgres `date_trunc`.
- **AC-8.5** `GET /reports/sales/by-vendor`, `/by-product`, `/by-category` return per-dimension rows with gross, refunds, net, commission, qty (by-product), share %.
- **AC-8.6** VENDOR_ADMIN is forced to their vendor across all report endpoints; `by-vendor` returns a single row.
- **AC-8.7** `GET /reports/sales/export?report=<name>&format=csv&...` streams a CSV of the same data (`Content-Type: text/csv`, `Content-Disposition: attachment`), streamed row-by-row (no full-result materialisation in memory).
- **AC-8.8** All money is MZN, 2 decimals; refunds and commission are non-negative; net is never derived from live prices.

## 5. Cross-cutting requirements

- **Audit**: every mutation in US-1..US-7 writes `audit_logs` (existing table/schema).
- **Idempotency**: refund and status-override actions accept an optional `Idempotency-Key`
  header and are safe to retry (consistent with existing checkout idempotency).
- **Tenant isolation**: no endpoint may read or mutate rows outside the resolved tenant;
  VENDOR_ADMIN additionally confined to own `vendor_id`. This is enforced in the service layer and covered by tests.
- **Consistency of pricing math**: introducing `discount_total` changes the order total
  formula; checkout, sales detail, and reports must all use `total = subtotal + fees + taxes − discount_total`.
- **Historical immutability**: product name/price/image edits and recategorisation must
  never alter past orders or already-emitted report figures (guaranteed by order snapshots; see `data-model.md` for the category-snapshot recommendation).

## 6. Non-functional targets

- Report endpoints respond < 1.5 s p95 for a 90-day range at MVP data volumes with the
  indexes in `data-model.md`; CSV export streams > 50k rows without OOM.
- Product edit / price / image endpoints respond < 300 ms p95 (excluding the browser→S3 PUT, which is direct).
- No regression to the single-transaction, idempotent checkout guarantees from Spec 001.

## 7. Open items / assumptions to confirm during build

- `Product` currently has no `description` column in the ERD; if the entity already has
  one, skip that column in the migration (verify `Product` entity before applying `V<NNN>`).
- Category-on-`OrderItem` snapshot (for stable category reports) is **recommended** but
  additive; historical rows fall back to a live product→category join. Confirm whether to
  adopt the snapshot now or accept live-join semantics for pre-existing orders.
