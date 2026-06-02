-- Customer Favorite Vendors for Epic 4 - Geospatial Discovery System
-- Allows customers to save and quickly access their preferred vendors

CREATE TABLE IF NOT EXISTS customer_favorite_vendors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, customer_id, vendor_id)
);

-- Index for fast retrieval of customer's favorites
CREATE INDEX IF NOT EXISTS idx_customer_favorite_vendors_customer 
ON customer_favorite_vendors(tenant_id, customer_id);

-- Index for vendor popularity analytics
CREATE INDEX IF NOT EXISTS idx_customer_favorite_vendors_vendor 
ON customer_favorite_vendors(tenant_id, vendor_id);

-- Add comment for documentation
COMMENT ON TABLE customer_favorite_vendors IS 'Stores customer favorite vendor preferences for quick access in search';
COMMENT ON COLUMN customer_favorite_vendors.tenant_id IS 'Tenant context for multi-tenancy';
COMMENT ON COLUMN customer_favorite_vendors.customer_id IS 'Customer who favorited the vendor';
COMMENT ON COLUMN customer_favorite_vendors.vendor_id IS 'Favorited vendor ID';
COMMENT ON COLUMN customer_favorite_vendors.created_at IS 'When the vendor was favorited';