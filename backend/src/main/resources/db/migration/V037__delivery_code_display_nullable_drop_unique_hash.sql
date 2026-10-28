-- INC-02 (AS-08, AS-09): delivery PIN minimisation and capacity fix.
--
-- 1. The PIN is verified against ONE delivery, so per-tenant uniqueness of its hash is unnecessary.
--    The constraint added in V021 capped capacity at 10^6 active+historical orders per tenant and
--    raced between the exists-check and the INSERT. Drop it (and its backing index).
ALTER TABLE orders DROP CONSTRAINT IF EXISTS uq_orders_tenant_delivery_code_hash;

-- 2. The plain PIN stays visible to the customer while the delivery is active (trade-off kept on
--    purpose), but is cleared (NULL) when the order reaches DELIVERED or CANCELLED (incl. failed
--    delivery). Existing rows are not modified here.
ALTER TABLE orders ALTER COLUMN delivery_confirmation_code_display DROP NOT NULL;
