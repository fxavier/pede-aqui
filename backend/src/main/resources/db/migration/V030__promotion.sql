-- Spec 002 / A4: promotion + promotion_redemption tables.
-- Distinct from the legacy V013 "promotions"/"coupons" tables, which remain untouched;
-- spec-002 promotions (coupon and automatic) live in the singular "promotion" table.
CREATE TABLE promotion (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vendor_id UUID,
    name VARCHAR(140) NOT NULL,
    code VARCHAR(40),
    type VARCHAR(20) NOT NULL,
    value NUMERIC(12,2) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    target_category_id UUID,
    target_product_id UUID,
    min_order_total NUMERIC(12,2),
    max_discount_amount NUMERIC(12,2),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    usage_limit INTEGER,
    per_customer_limit INTEGER,
    used_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_promotion_type CHECK (type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT ck_promotion_scope CHECK (scope IN ('ORDER', 'CATEGORY', 'PRODUCT')),
    CONSTRAINT ck_promotion_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED')),
    -- Type/value coherence: percentage in (0,100], fixed amount > 0.
    CONSTRAINT ck_promotion_type_value CHECK (
        (type = 'PERCENTAGE' AND value > 0 AND value <= 100)
        OR (type = 'FIXED_AMOUNT' AND value > 0)
    ),
    -- Scope/target coherence: target required iff scope matches.
    CONSTRAINT ck_promotion_scope_target CHECK (
        (scope = 'ORDER' AND target_category_id IS NULL AND target_product_id IS NULL)
        OR (scope = 'CATEGORY' AND target_category_id IS NOT NULL AND target_product_id IS NULL)
        OR (scope = 'PRODUCT' AND target_product_id IS NOT NULL AND target_category_id IS NULL)
    ),
    CONSTRAINT ck_promotion_window CHECK (starts_at < ends_at),
    CONSTRAINT ck_promotion_used_count CHECK (used_count >= 0)
);

-- Coupon codes are unique per tenant; automatic promotions (code IS NULL) are exempt.
CREATE UNIQUE INDEX ux_promotion_tenant_code ON promotion (tenant_id, code) WHERE code IS NOT NULL;
CREATE INDEX ix_promotion_tenant_vendor_status ON promotion (tenant_id, vendor_id, status);
CREATE INDEX ix_promotion_tenant_status_window ON promotion (tenant_id, status, starts_at, ends_at);

-- Per-customer usage ledger; unique (promotion_id, order_id) keeps redemption idempotent.
CREATE TABLE promotion_redemption (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    promotion_id UUID NOT NULL REFERENCES promotion(id),
    customer_id UUID NOT NULL,
    order_id UUID NOT NULL REFERENCES orders(id),
    amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_promotion_redemption_promotion_order UNIQUE (promotion_id, order_id)
);

CREATE INDEX ix_promotion_redemption_promotion_customer ON promotion_redemption (promotion_id, customer_id);

-- Deferred from V029: orders.applied_promotion_id references promotion.
ALTER TABLE orders ADD CONSTRAINT fk_orders_applied_promotion
    FOREIGN KEY (applied_promotion_id) REFERENCES promotion(id);
