-- V014: Customer Favorites and Ratings System
-- This migration adds customer favorite vendors and rating functionality

-- Table: customer_favorite_vendors
CREATE TABLE customer_favorite_vendors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    customer_id UUID NOT NULL,
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, customer_id, vendor_id)
);

-- Table: ratings
CREATE TABLE ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    rater_user_id UUID NOT NULL,
    rated_vendor_id UUID REFERENCES vendors(id),
    rated_courier_id UUID REFERENCES couriers(id),
    stars INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment VARCHAR(500),
    vendor_reply VARCHAR(500),
    vendor_replied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(order_id, rater_user_id)
);

-- Indexes for customer_favorite_vendors
CREATE INDEX idx_favorites_tenant_customer ON customer_favorite_vendors(tenant_id, customer_id);

-- Indexes for ratings
CREATE INDEX idx_ratings_tenant_vendor ON ratings(tenant_id, rated_vendor_id);
CREATE INDEX idx_ratings_tenant_courier ON ratings(tenant_id, rated_courier_id);

-- Comments for documentation
COMMENT ON TABLE customer_favorite_vendors IS 'Stores customer favorite vendors for quick access';
COMMENT ON TABLE ratings IS 'Stores customer ratings for vendors and couriers after order completion';