-- INC-07 follow-up (COR-01): data fix. Reconciles stock for orders created before INC-07.
-- V041 marked every existing order stock_status='RESERVED' (true at checkout: OrderService.checkout
-- has always called InventoryService.reserve, i.e. available -= qty, reserved += qty). Before INC-07
-- nothing ever released or committed those reservations, so legacy terminal orders still hold
-- quantity_reserved, and cancelled/rejected ones never returned units to quantity_available.
--
-- This migration replays, once, what OrderStockService does today (per order_item, inventory row
-- matched by (orders.tenant_id, order_items.sku_id) exactly like lockByTenantIdAndSkuId):
--   release (InventoryItem.release): available += n, reserved -= n, n = min(qty, reserved)
--   commit  (InventoryItem.commit):  reserved -= n,                 n = min(qty, reserved)
-- Classification of orders still stock_status='RESERVED' (o = order, d = its delivery row):
--   COMMIT  - goods left the store: d.status IN (PICKED_UP, ON_ROUTE_TO_CUSTOMER, ARRIVED_AT_CUSTOMER,
--             DELIVERED, FAILED_DELIVERY) or o.status IN (PICKED_UP, DELIVERING, DELIVERED). Mirrors the
--             app: commit at delivery PICKED_UP and at complete(); FAILED_DELIVERY is only reachable from
--             ARRIVED_AT_CUSTOMER (after pickup), so its release() is a no-op after the commit.
--   RELEASE - order died before pickup: o.status IN (CANCELLED, REJECTED_BY_VENDOR) (the app releases
--             unconditionally on cancel/reject), or o.status = REFUNDED with no courier engaged
--             (d absent or d.status NOT IN (ASSIGNED, ACCEPTED, ARRIVED_AT_VENDOR, REASSIGNED)).
--             A refunded order whose courier is engaged could still be picked up -> left RESERVED.
--   none    - every other (active) order stays RESERVED; the app releases/commits it later.
-- When an inventory row's reservation is smaller than the legacy demand (stock edited by hand),
-- commits are applied first, then releases with what remains: favouring commit never inflates
-- sellable stock. quantity_reserved is clamped at >= 0 (GREATEST) on top of the CHECK.
--
-- Idempotent: only orders still 'RESERVED' are touched, and they are marked RELEASED/COMMITTED in the
-- same transaction as the inventory update, so a re-run finds nothing to do. No DELETEs.
--
-- Validation queries (run before; read-only):
--   -- 1. orders that will change, per target state
--   SELECT CASE WHEN d.status IN ('PICKED_UP','ON_ROUTE_TO_CUSTOMER','ARRIVED_AT_CUSTOMER','DELIVERED','FAILED_DELIVERY')
--                 OR o.status IN ('PICKED_UP','DELIVERING','DELIVERED') THEN 'COMMIT'
--               WHEN o.status IN ('CANCELLED','REJECTED_BY_VENDOR')
--                 OR (o.status = 'REFUNDED' AND (d.status IS NULL OR d.status NOT IN ('ASSIGNED','ACCEPTED','ARRIVED_AT_VENDOR','REASSIGNED')))
--               THEN 'RELEASE' ELSE 'KEEP' END AS action, o.status, count(*)
--     FROM orders o LEFT JOIN deliveries d ON d.tenant_id = o.tenant_id AND d.order_id = o.id
--    WHERE o.stock_status = 'RESERVED' GROUP BY 1, 2 ORDER BY 1, 2;
--   -- 2. expected vs actual reserved per SKU (expected = sum of qty of orders that remain RESERVED)
--   WITH cls AS (<the CASE above> AS action, o.id, o.tenant_id ... WHERE o.stock_status = 'RESERVED'),
--        exp AS (SELECT c.tenant_id, oi.sku_id, sum(oi.quantity) FILTER (WHERE c.action = 'KEEP') AS expected_reserved,
--                       sum(oi.quantity) FILTER (WHERE c.action = 'RELEASE') AS to_release,
--                       sum(oi.quantity) FILTER (WHERE c.action = 'COMMIT') AS to_commit
--                  FROM cls c JOIN order_items oi ON oi.order_id = c.id GROUP BY 1, 2)
--   SELECT i.sku_id, i.quantity_available, i.quantity_reserved AS actual_reserved, e.expected_reserved,
--          e.to_release, e.to_commit
--     FROM inventory_items i LEFT JOIN exp e ON e.tenant_id = i.tenant_id AND e.sku_id = i.sku_id
--    WHERE i.quantity_reserved > 0 OR e.sku_id IS NOT NULL ORDER BY i.sku_id;
--   After the migration actual_reserved should equal coalesce(expected_reserved, 0) unless stock was
--   edited by hand (then the clamp applies).
--
-- Locking: row locks on the matched orders rows (UPDATE ... WHERE stock_status='RESERVED', re-checked
--   under the lock) and on the affected inventory_items rows; no DDL, no table rewrite. Both tables
--   are small, so the transaction is short; concurrent checkouts on the same SKU wait on the row lock
--   (the app takes the same lock, SELECT ... FOR UPDATE, so no deadlock-prone ordering is introduced
--   beyond a single multi-row UPDATE).
-- Compatibility: columns and values already exist since V041; the current app reads them as-is.
--   version is bumped on both tables so a stale entity from an instance still running fails
--   optimistically (409/retry) instead of overwriting the reconciled numbers. A pre-INC-07 app ignores
--   stock_status and never releases/commits, so it is unaffected. orders.updated_at is left unchanged.
-- Rollback / forward-fix: no automatic rollback (Flyway community). Forward-fix: the affected orders are
--   identifiable (stock_status RELEASED/COMMITTED on a pre-INC-07 order); to revert one SKU, apply the
--   inverse (release: available -= n, reserved += n; commit: reserved += n) in a new migration from the
--   validation output captured before applying this one. Keep that output.

CREATE TEMP TABLE v043_stock_fix (
    order_id  UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    action    VARCHAR(8) NOT NULL
) ON COMMIT DROP;

-- 1. Classify and mark the orders in one statement (UPDATE re-checks stock_status under the row lock).
WITH classified AS (
    SELECT o.id,
           CASE
               WHEN d.status IN ('PICKED_UP', 'ON_ROUTE_TO_CUSTOMER', 'ARRIVED_AT_CUSTOMER', 'DELIVERED', 'FAILED_DELIVERY')
                 OR o.status IN ('PICKED_UP', 'DELIVERING', 'DELIVERED')
                   THEN 'COMMIT'
               WHEN o.status IN ('CANCELLED', 'REJECTED_BY_VENDOR')
                 OR (o.status = 'REFUNDED'
                     AND (d.status IS NULL OR d.status NOT IN ('ASSIGNED', 'ACCEPTED', 'ARRIVED_AT_VENDOR', 'REASSIGNED')))
                   THEN 'RELEASE'
           END AS action
      FROM orders o
      LEFT JOIN deliveries d ON d.tenant_id = o.tenant_id AND d.order_id = o.id
     WHERE o.stock_status = 'RESERVED'
),
marked AS (
    UPDATE orders o
       SET stock_status = CASE c.action WHEN 'RELEASE' THEN 'RELEASED' ELSE 'COMMITTED' END,
           version = o.version + 1
      FROM classified c
     WHERE o.id = c.id
       AND c.action IS NOT NULL
       AND o.stock_status = 'RESERVED'
    RETURNING o.id, o.tenant_id, c.action
)
INSERT INTO v043_stock_fix (order_id, tenant_id, action)
SELECT id, tenant_id, action FROM marked;

-- 2. Apply the aggregated release/commit per inventory row. Commit first, then release with what is
--    left, both clamped to the reservation: min(c, r) + min(rel, r - min(c, r)) = min(c + rel, r).
--    Computed from the row being updated (not a pre-read snapshot), so it is correct under the row lock.
WITH demand AS (
    SELECT f.tenant_id,
           oi.sku_id,
           COALESCE(SUM(oi.quantity) FILTER (WHERE f.action = 'COMMIT'), 0)  AS to_commit,
           COALESCE(SUM(oi.quantity) FILTER (WHERE f.action = 'RELEASE'), 0) AS to_release
      FROM v043_stock_fix f
      JOIN order_items oi ON oi.order_id = f.order_id
     GROUP BY f.tenant_id, oi.sku_id
)
UPDATE inventory_items i
   SET quantity_reserved  = GREATEST(i.quantity_reserved - d.to_commit - d.to_release, 0),
       quantity_available = i.quantity_available + LEAST(d.to_release, GREATEST(i.quantity_reserved - d.to_commit, 0)),
       updated_at         = now(),
       version            = i.version + 1
  FROM demand d
 WHERE i.tenant_id = d.tenant_id
   AND i.sku_id = d.sku_id
   AND i.quantity_reserved > 0;
