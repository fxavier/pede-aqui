# Spec 002 — Technical Plan

Companion to `spec.md`. Records architecture, decisions, trade-offs, and risks.

## 1. Design principles (inherited)

- Stay in the **simple layered** model (`controller → service → repository → entity`,
  `dto`/`mapper` alongside). No DDD, CQRS, event sourcing, command buses, or Kafka.
- Schema changes only via **Flyway** `V<NNN>__*.sql`; Hibernate is `validate`-only.
- **Tenant scoping** in the service layer via `TenantContext`; repositories stay plain
  Spring Data JPA. VENDOR_ADMIN adds a `vendor_id` filter in-service.
- **Presigned uploads** unchanged: presign → browser PUT → link `storageKey`.
- **Audit** via the existing `audit_logs` writer for every sensitive mutation.

New backend code lands in existing domain packages: `catalog`, `sales` (new), `marketing`,
`report` (new), reusing `order`, `payment`, `finance`, `notification`, `upload`.

## 2. Feature 1 — Catalog & Product management

### 2.1 Price lives on SKU; UX is single-price

Data model keeps `Sku.price` authoritative. Because products are single-SKU in practice
(decision #1), the price endpoints operate on the product's one active SKU and reject
products with 0 or >1 active SKU (`409`) rather than silently guessing. This preserves the
multi-SKU data model without building multi-SKU UI (decision to defer).

### 2.2 Price-change review without taking the product offline

Decision (#2): threshold-based review, implemented so it never hides the product and never
serves an unapproved price.

- The SKU carries two price fields: `price` (live, approved, always what customers pay) and
  `pending_price` (nullable, awaiting approval).
- On price edit: compute `Δ% = |new − price| / price`.
  - Review disabled **or** `Δ% ≤ threshold-percent` → set `price = new` immediately (in-place, audit-only). This is the "pure audit" mode you can select by disabling review.
  - Review enabled and `Δ% > threshold-percent` → set `pending_price = new`, leave `price` and product status untouched. A moderator approves (`price = pending_price`) or rejects (discard).
- Product `status` is **not** used for price review, so an over-threshold change never
  removes the product from vendor search. This is a deliberate improvement over
  "flip to PENDING" (which would hide the product and could serve no/old price ambiguously).

Config (`application.yml`, overridable per profile):

```yaml
app:
  catalog:
    price-review:
      enabled: true          # false → all price edits in-place, audit-only
      threshold-percent: 20  # |Δ%| strictly greater triggers review
```

Trade-off: a single `pending_price` column supports exactly one in-flight change per SKU
(AC-2.4 returns `409` on a second). A dedicated `product_change_request` table would allow
history and multiple attribute reviews, but that is over-built for the single-price MVP.
If attribute-level review is ever needed, migrate the pending slot into such a table then.

### 2.3 Image

Single `image_storage_key` on `Product`, populated through the existing presigned flow.
`PUT /catalog/products/{id}/image` validates the key belongs to this tenant's image prefix
(defence against setting an arbitrary/foreign key) before persisting. Read DTOs resolve the
key to a URL per storage profile (presigned GET for private buckets; direct URL for public
MinIO dev). Schema is shaped so a later `product_images` gallery table is additive.

## 3. Feature 2 — Sales management

### 3.1 Sales as a read model, not a second order system

`/sales` is a **commercial projection over existing `Order`/`OrderItem`/`Payment`/`Refund`/
`Commission`** — no new order storage. `SalesService` uses `OrderRepository` (plus targeted
projection queries) and returns revenue-focused DTOs. This avoids divergence from the
fulfilment-focused `/orders` view: same source of truth, different lens/columns/filters.

### 3.2 Operational actions reuse existing paths

- **Cancel** → drives the Spec 001 order state machine (guarded transitions only). No raw
  status writes. Valid only in pre-dispatch states.
- **Refund** → creates a `Refund` through the existing finance/refund path, so the
  established `RefundRequested → settlement` in-process flow, commission reversal, and
  notifications all fire unchanged. Full or partial; capped at `paid − alreadyRefunded`.
- **Resend notification** → `NotificationService`. `DELIVERY_CODE` re-sends to the customer;
  the OTP is neither returned by the API nor logged (consistent with Spec 001).

### 3.3 Free-form status override is discouraged and gated

Recommendation / risk callout: arbitrary status overrides invite state-machine corruption
(orphaned deliveries, inconsistent finance). We therefore **do not** expose a free `PATCH status`.
`status-override` exists only behind `sales.status-override.enabled=false` (default off),
restricted to an allow-list transition matrix, ADMIN/OPS-only, reason-required, audited.
Prefer the explicit cancel/refund/resend actions. Recommended allow-list (initial):

```
PAYMENT_CONFIRMED → CANCELLED            (with refund side-effect)
ACCEPTED_BY_VENDOR → PREPARING           (unstick vendor)
DISPATCH_PENDING → ASSIGNED_TO_COURIER   (manual dispatch)
DELIVERING → DELIVERED                   (courier device failure; reason mandatory)
```

### 3.4 Discounts / promotions (decision 5d)

- **Promotion** aggregate in `marketing`: coupon (has `code`) vs automatic (no `code`);
  `PERCENTAGE`/`FIXED_AMOUNT`; scope `ORDER`/`CATEGORY`/`PRODUCT`; validity window; usage
  limits; vendor-scoped or tenant-wide (`vendor_id = null`).
- **Application** (single discount, no stacking):
  - Cart holds an optional applied promotion; `POST /cart/{id}/coupon` validates and attaches.
  - At checkout, `CheckoutService` resolves the effective discount **inside the existing
    single transaction**: if a coupon is attached and valid it wins; else the best-qualifying
    automatic promotion applies. It computes `discount_total`, sets `order.applied_promotion_id`
    and `order.discount_total`, recomputes `total`, and increments `used_count` / per-customer
    usage atomically. Limit breaches abort checkout (no usage consumed).
- **Pricing math change**: `total = subtotal + fees + taxes − discount_total`. Every consumer
  (checkout sequence, sales detail, reports) uses this formula. `discount_total` defaults to 0
  so existing orders are unaffected.

Risk: usage-limit races. Mitigation: increment guarded by the checkout transaction with a
conditional `UPDATE ... WHERE used_count < usage_limit` (row returns 0 → limit reached → abort).

## 4. Feature 3 — Sales reports

### 4.1 Compute strategy (decision #8)

On-the-fly parameterised aggregation using repository **projection queries** (native SQL for
`date_trunc` and `GROUP BY`), tenant-filtered, optional vendor filter. No materialised views.
CSV export streams via `StreamingResponseBody` / a JDBC streaming cursor so large ranges
don't materialise in memory. xlsx deferred.

### 4.2 Revenue semantics (decision #7)

- Gross = Σ `Order.total` for orders with confirmed payment (state ≥ `PAYMENT_CONFIRMED`,
  excluding pre-payment cancellations), by `created_at` in `[from,to]`.
- Discount = Σ `Order.discount_total` over the same set.
- Refunds = Σ `Refund.amount` for approved/settled refunds in the period.
- Net = Gross − Refunds. Commission = Σ `Commission.amount` for the same orders.
- All MZN, 2 decimals. `by-product` reads quantities and line revenue from `OrderItem`
  snapshots (immutable), never live SKU price.

Documented alternative (not default): **paid-at basis** (bucket by payment confirmation time
rather than order creation) — swap the timestamp column in the queries; called out so the
choice is explicit and reversible.

### 4.3 Category reporting caveat + recommendation

`OrderItem` snapshots name and price but not category. `by-category` therefore joins
`OrderItem → Sku → Product → Category` live, so recategorising a product retroactively
reclassifies its historical sales. Recommendation: add `category_id_snapshot` to `OrderItem`
(populated at order creation) for stable category history; pre-existing rows fall back to the
live join. Additive and cheap; flagged in `spec.md` §7 for your go/no-go.

### 4.4 Performance & rollup option

Supporting indexes (see `data-model.md`) cover the hot predicates
(`tenant_id`, `created_at`, `vendor_id`, `status`, `order_id`). If report queries over long
ranges become hot, add a nightly `sales_daily_rollup(tenant_id, vendor_id, day, …)` populated
by a scheduled job and have summary/timeseries read the rollup for closed days + live query
for the current day. **Not built now** — documented so it can be added without API changes
(endpoints stay identical; only the query source changes).

## 5. Backoffice (Next.js) plan

- New/extended routes:
  - `/catalogo` (extend): product edit drawer (attributes + single price + image uploader),
    "price change pending" badge, and an OPS/ADMIN moderation queue tab.
  - `/sales` (new): filterable sales table + detail panel with cancel/refund/resend actions.
  - `/marketing` (extend): promotions CRUD + activate/pause.
  - `/reports` (new): summary cards, time-series chart, by-vendor/product/category tables,
    date-range picker, CSV download.
- `services.ts` additions: extend `catalogService` (`updateProduct`, `updateProductPrice`,
  `setProductImage`, `deleteProductImage`, `listPendingPriceChanges`, `approvePriceChange`,
  `rejectPriceChange`); new `salesService`, `promotionService`, `reportService`. Types in
  `types.ts`.
- RBAC: gate routes/actions by role via the existing `AppShell`/route-guard pattern;
  hide (not just disable) actions the role can't perform; rely on backend `@PreAuthorize` as
  the real boundary.
- State: TanStack Query for all reads (report/list caching, invalidation on mutations); Redux
  only for cross-cutting UI (existing `ui-slice`) and the active tenant (`auth-slice`).
- Reuse the presigned upload helper already used by `/empresa` logo/documents for the product
  image uploader.

## 6. Testing strategy (summary; detail in `tasks.md`)

- **Backend unit/service**: price-review branch matrix (disabled / within / over threshold /
  second-pending `409` / non-single-SKU `409`); tenant + vendor isolation on every service;
  refund cap and state-machine guards on cancel; promotion application (coupon-wins,
  automatic, limits/races, min-order gating, percentage cap); report aggregation math on a
  seeded dataset including refunds and discounts; CSV streaming shape.
- **Web-layer/controller**: `@PreAuthorize` matrix (403 paths), validation `400`s, idempotent
  refund retries.
- **Frontend**: service-client contract tests against the OpenAPI fragment; RBAC visibility;
  report table/chart rendering with empty/error states.
- **Regression**: checkout single-transaction + idempotency unchanged with `discount_total`.

## 7. Risks & mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| `discount_total` alters total math everywhere | Wrong charges/reports | Default 0; single formula; regression tests on checkout + report reconciliation |
| Price-review hides products (if mis-implemented) | Lost sales | Design keeps product `ACTIVE`; `pending_price` slot; explicit AC-2.3 test |
| Free-form status override corrupts state | Data integrity, finance | Gated off by default, allow-list only, audited; prefer explicit actions |
| Promotion usage-limit races | Over-redemption | Conditional atomic increment in checkout tx |
| Category recategorisation rewrites history | Misleading reports | Recommend `category_id_snapshot`; document live-join fallback |
| Long-range report scans | Latency | Targeted indexes now; rollup table documented as drop-in later |
| Foreign/spoofed `storageKey` on image set | Data leak / broken links | Validate tenant image-prefix ownership before persist |

## 8. Rollout

1. Backend migrations + entities + services + controllers behind role checks.
2. Contract publish (OpenAPI fragment) → frontend service clients.
3. Backoffice screens, feature-by-feature (`/catalogo` edit → `/sales` → `/marketing`
   promotions → `/reports`).
4. Enable `price-review` in dev with a low threshold to exercise the moderation path; ship
   with production threshold per business preference. `status-override` stays disabled until
   explicitly requested.
