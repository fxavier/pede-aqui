# Spec 002 — Data Model & Migrations

All new columns/tables are tenant-scoped (`tenant_id uuid not null`) unless noted. Types
assume PostgreSQL + the existing UUID-PK convention. `ddl-auto: validate` → every change is a
Flyway `V<NNN>__*.sql`. Verify the current `Product`/`Order`/`OrderItem`/`Sku` entities before
writing the final `<NNN>` numbers; the columns below are additive.

## 1. Catalog

### 1.1 `product` (extend)

| Column | Type | Null | Notes |
|---|---|---|---|
| `description` | `text` | yes | **Add only if not already present** — verify entity first |
| `image_storage_key` | `varchar(512)` | yes | Object-storage key from presigned upload; null = no image |
| `updated_at` | `timestamptz` | no, default `now()` | Touched on any edit |

### 1.2 `sku` (extend) — single-price + pending review

| Column | Type | Null | Notes |
|---|---|---|---|
| `pending_price` | `numeric(12,2)` | yes | Awaiting moderation; null = none in flight |
| `pending_price_submitted_at` | `timestamptz` | yes | |
| `pending_price_submitted_by` | `varchar(255)` | yes | Actor subject/username |

`price numeric(12,2)` already exists and remains the authoritative selling price.
Constraint: `pending_price is null or pending_price >= 0`.

## 2. Ordering / discounts

### 2.1 `orders` (extend)

| Column | Type | Null | Notes |
|---|---|---|---|
| `discount_total` | `numeric(12,2)` | no, default `0` | Applied discount; 0 for legacy orders |
| `applied_promotion_id` | `uuid` | yes | FK → `promotion(id)`; null = none |

Invariant enforced in code (not a generated column, to avoid touching legacy rows):
`total = subtotal + fees + taxes − discount_total`.

### 2.2 `order_item` (extend — recommended, optional)

| Column | Type | Null | Notes |
|---|---|---|---|
| `category_id_snapshot` | `uuid` | yes | Category at order time, for stable category reports |

Populated at order creation for new orders; pre-existing rows stay null and fall back to the
live `product.category_id` join in category reports. Adopt on your confirmation (spec §7).

## 3. Marketing — promotions

### 3.1 `promotion` (new)

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `uuid` | no | PK |
| `tenant_id` | `uuid` | no | |
| `vendor_id` | `uuid` | yes | null = tenant-wide (OPS/ADMIN only) |
| `name` | `varchar(140)` | no | |
| `code` | `varchar(40)` | yes | present → coupon; null → automatic |
| `type` | `varchar(20)` | no | `PERCENTAGE` \| `FIXED_AMOUNT` |
| `value` | `numeric(12,2)` | no | percent (0,100] or MZN amount > 0 |
| `scope` | `varchar(20)` | no | `ORDER` \| `CATEGORY` \| `PRODUCT` |
| `target_category_id` | `uuid` | yes | required iff `scope=CATEGORY` |
| `target_product_id` | `uuid` | yes | required iff `scope=PRODUCT` |
| `min_order_total` | `numeric(12,2)` | yes | eligibility floor |
| `max_discount_amount` | `numeric(12,2)` | yes | cap for `PERCENTAGE` |
| `starts_at` | `timestamptz` | no | |
| `ends_at` | `timestamptz` | no | `starts_at < ends_at` |
| `usage_limit` | `integer` | yes | global cap; null = unlimited |
| `per_customer_limit` | `integer` | yes | per-customer cap; null = unlimited |
| `used_count` | `integer` | no, default `0` | atomically incremented at checkout |
| `status` | `varchar(20)` | no, default `DRAFT` | `DRAFT`\|`ACTIVE`\|`PAUSED`\|`EXPIRED` |
| `created_at` / `updated_at` | `timestamptz` | no | |

Indexes: `(tenant_id, code)` **unique where `code is not null`** (partial unique);
`(tenant_id, vendor_id, status)`; `(tenant_id, status, starts_at, ends_at)`.
Check constraints: type/value coherence; scope/target coherence.

### 3.2 `promotion_redemption` (new) — per-customer usage

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `uuid` | no | PK |
| `tenant_id` | `uuid` | no | |
| `promotion_id` | `uuid` | no | FK → `promotion(id)` |
| `customer_id` | `uuid` | no | |
| `order_id` | `uuid` | no | FK → `orders(id)` |
| `amount` | `numeric(12,2)` | no | discount granted |
| `created_at` | `timestamptz` | no | |

Unique `(promotion_id, order_id)` (idempotent redemption). Index `(promotion_id, customer_id)`
for per-customer limit checks.

### 3.3 `cart` / `cart_item` (extend)

Add to `cart`: `applied_promotion_id uuid null`, `coupon_code varchar(40) null`,
`discount_total numeric(12,2) not null default 0`. Carries the pending discount from
"apply coupon" through to checkout, where it is validated again before persisting on the order.

## 4. Indexes for reports & sales

Add supporting indexes (names indicative):

```sql
create index if not exists ix_orders_tenant_created      on orders (tenant_id, created_at);
create index if not exists ix_orders_tenant_vendor_created on orders (tenant_id, vendor_id, created_at);
create index if not exists ix_orders_tenant_status_created on orders (tenant_id, status, created_at);
create index if not exists ix_order_item_order            on order_item (order_id);
create index if not exists ix_refund_order_status         on refund (order_id, status);
create index if not exists ix_commission_order            on commission (order_id);
```

Partial-index the report hot path if profiling warrants:
`create index ix_orders_paid on orders (tenant_id, created_at) where status <> 'PENDING' and status <> 'PAYMENT_PENDING';`

## 5. Audit

No schema change: reuse `audit_logs (actor, target, action, result, timestamp, …)`.
New `action` values (string constants): `PRODUCT_UPDATED`, `PRODUCT_PRICE_UPDATED`,
`PRODUCT_PRICE_PENDING`, `PRODUCT_PRICE_APPROVED`, `PRODUCT_PRICE_REJECTED`,
`PRODUCT_IMAGE_SET`, `PRODUCT_IMAGE_CLEARED`, `SALE_CANCELLED`, `SALE_REFUNDED`,
`SALE_NOTIFICATION_RESENT`, `SALE_STATUS_OVERRIDDEN`, `PROMOTION_CREATED`,
`PROMOTION_UPDATED`, `PROMOTION_ACTIVATED`, `PROMOTION_PAUSED`, `PROMOTION_DELETED`.
Diffs (before/after) stored in the existing audit payload column.

## 6. Migration ordering (indicative)

1. `V<NNN>__catalog_product_image_desc.sql` — `product` columns.
2. `V<NNN+1>__sku_pending_price.sql` — `sku` pending-price columns + check.
3. `V<NNN+2>__orders_discount.sql` — `orders.discount_total`, `applied_promotion_id`.
4. `V<NNN+3>__promotion.sql` — `promotion`, `promotion_redemption` + indexes/constraints.
5. `V<NNN+4>__cart_discount.sql` — `cart` discount columns.
6. `V<NNN+5>__report_indexes.sql` — supporting indexes.
7. `V<NNN+6>__order_item_category_snapshot.sql` — **only if adopting** the category snapshot.

Each migration is additive and backward-compatible; legacy orders read as `discount_total = 0`
and null promotion/category-snapshot.

## 7. Entity/DTO impact (backend)

- `Product` entity: `+description`, `+imageStorageKey`, `+updatedAt`; response DTO exposes
  resolved image URL.
- `Sku` entity: `+pendingPrice`, `+pendingPriceSubmittedAt`, `+pendingPriceSubmittedBy`.
- `Order` entity: `+discountTotal`, `+appliedPromotionId`; `OrderItem`: optional
  `+categoryIdSnapshot`.
- New entities: `Promotion`, `PromotionRedemption`. New repositories:
  `PromotionRepository`, `PromotionRedemptionRepository`, plus report projection interfaces
  (e.g. `SalesReportRepository` with native `@Query` projections).
- `Cart`/`CartItem`: `+appliedPromotionId`, `+couponCode`, `+discountTotal`.
