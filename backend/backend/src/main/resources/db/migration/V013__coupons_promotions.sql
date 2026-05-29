CREATE TABLE coupons (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(50) NOT NULL,
    discount_type VARCHAR(30) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    min_order_amount DECIMAL(12,2),
    max_uses INTEGER,
    uses_count INTEGER NOT NULL DEFAULT 0,
    vendor_id UUID,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_coupons_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(180) NOT NULL,
    description TEXT,
    discount_type VARCHAR(30) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    vendor_id UUID,
    applies_to VARCHAR(60) NOT NULL DEFAULT 'ALL_ORDERS',
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_coupons_tenant_active ON coupons(tenant_id, active);
CREATE INDEX idx_promotions_tenant_active ON promotions(tenant_id, active);
