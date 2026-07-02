-- V016: Delivery Fee Rules and Vendor Zone
-- This migration adds delivery fee rules and vendor zone support

-- Table: delivery_fee_rules
CREATE TABLE delivery_fee_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    min_km NUMERIC(8,2) NOT NULL,
    max_km NUMERIC(8,2) NOT NULL,
    fee_amount NUMERIC(12,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add delivery_zone_id to vendors table
ALTER TABLE vendors ADD COLUMN IF NOT EXISTS delivery_zone_id UUID REFERENCES zones(id);

-- Indexes
CREATE INDEX idx_delivery_fee_rules_vendor ON delivery_fee_rules(vendor_id);

-- Comments for documentation
COMMENT ON TABLE delivery_fee_rules IS 'Distance-based delivery fee rules for vendors';
COMMENT ON COLUMN vendors.delivery_zone_id IS 'Geographic zone this vendor delivers to';