-- Add unique constraint on delivery confirmation code hash to prevent collisions
-- This ensures that no two orders can have the same delivery confirmation code within a tenant

ALTER TABLE orders ADD CONSTRAINT uq_orders_tenant_delivery_code_hash 
    UNIQUE (tenant_id, delivery_confirmation_code_hash);