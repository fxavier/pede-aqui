-- Spec 002 / A6: supporting indexes for sales listing and report aggregations.
-- Actual table names are plural (orders, order_items, refunds, commissions);
-- commissions stores the amount as commission_amount (see V008).
CREATE INDEX IF NOT EXISTS ix_orders_tenant_created ON orders (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS ix_orders_tenant_vendor_created ON orders (tenant_id, vendor_id, created_at);
CREATE INDEX IF NOT EXISTS ix_orders_tenant_status_created ON orders (tenant_id, status, created_at);
CREATE INDEX IF NOT EXISTS ix_order_items_order ON order_items (order_id);
CREATE INDEX IF NOT EXISTS ix_refunds_order_status ON refunds (order_id, status);
CREATE INDEX IF NOT EXISTS ix_commissions_order ON commissions (order_id);
