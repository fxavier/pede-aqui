-- INC-07 (COR-06, COR-01): additive columns on orders.
-- cart_id: the cart an order was checked out from, so a reused checkout idempotency key can be
--   compared against the request (same key + other cart => 409). Nullable: legacy orders have no link.
-- stock_status: per-order stock lifecycle marker (RESERVED -> RELEASED | COMMITTED) that makes
--   stock release/commit idempotent. Every existing order reserved stock at checkout, so RESERVED
--   is the truthful default for legacy rows (constant default => metadata-only on PostgreSQL 11+).
ALTER TABLE orders ADD COLUMN cart_id UUID;
ALTER TABLE orders ADD COLUMN stock_status VARCHAR(16) NOT NULL DEFAULT 'RESERVED';

-- NOT VALID + VALIDATE keeps the ACCESS EXCLUSIVE lock to a catalog-only change; validation scans
-- under SHARE UPDATE EXCLUSIVE, which does not block reads or writes.
ALTER TABLE orders ADD CONSTRAINT chk_orders_stock_status
    CHECK (stock_status IN ('RESERVED', 'RELEASED', 'COMMITTED')) NOT VALID;
ALTER TABLE orders VALIDATE CONSTRAINT chk_orders_stock_status;
