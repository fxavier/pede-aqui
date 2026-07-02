CREATE TABLE categories (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_tenant_slug UNIQUE (tenant_id, slug)
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vendor_id UUID NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
    name VARCHAR(180) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL,
    requires_prescription_metadata BOOLEAN NOT NULL,
    manual_validation_required BOOLEAN NOT NULL,
    prohibited_fuel BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE skus (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    product_id UUID NOT NULL REFERENCES products(id),
    sku_code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_skus_tenant_code UNIQUE (tenant_id, sku_code)
);

CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vendor_id UUID NOT NULL,
    sku_id UUID NOT NULL REFERENCES skus(id),
    quantity_available INTEGER NOT NULL CHECK (quantity_available >= 0),
    quantity_reserved INTEGER NOT NULL CHECK (quantity_reserved >= 0),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_items_tenant_sku UNIQUE (tenant_id, sku_id)
);

CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    customer_id UUID NOT NULL,
    label VARCHAR(80) NOT NULL,
    recipient_name VARCHAR(160) NOT NULL,
    phone VARCHAR(40),
    line1 VARCHAR(200) NOT NULL,
    line2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(80) NOT NULL,
    postal_code VARCHAR(30) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    delivery_instructions TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    customer_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    fees NUMERIC(12,2) NOT NULL,
    taxes NUMERIC(12,2) NOT NULL,
    discounts NUMERIC(12,2) NOT NULL,
    total NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    sku_id UUID NOT NULL REFERENCES skus(id),
    product_name_snapshot VARCHAR(180) NOT NULL,
    sku_name_snapshot VARCHAR(180) NOT NULL,
    unit_price_snapshot NUMERIC(12,2) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    reference VARCHAR(80) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    vendor_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    fees NUMERIC(12,2) NOT NULL,
    taxes NUMERIC(12,2) NOT NULL,
    discounts NUMERIC(12,2) NOT NULL,
    total NUMERIC(12,2) NOT NULL,
    checkout_idempotency_key VARCHAR(160) NOT NULL,
    delivery_confirmation_code_hash VARCHAR(128) NOT NULL,
    delivery_confirmation_code_display VARCHAR(6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_tenant_checkout_key UNIQUE (tenant_id, checkout_idempotency_key)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    sku_id UUID NOT NULL REFERENCES skus(id),
    product_name_snapshot VARCHAR(180) NOT NULL,
    sku_name_snapshot VARCHAR(180) NOT NULL,
    unit_price_snapshot NUMERIC(12,2) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    provider VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(40) NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    courier_id UUID,
    status VARCHAR(40) NOT NULL,
    confirmation_code_hash VARCHAR(128) NOT NULL,
    confirmation_attempts INTEGER NOT NULL,
    proof_photo_storage_key VARCHAR(300),
    cash_collected_amount NUMERIC(12,2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_deliveries_tenant_order UNIQUE (tenant_id, order_id)
);

CREATE TABLE delivery_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    delivery_id UUID NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_products_tenant_vendor_status ON products(tenant_id, vendor_id, status);
CREATE INDEX idx_skus_tenant_product ON skus(tenant_id, product_id);
CREATE INDEX idx_inventory_tenant_vendor ON inventory_items(tenant_id, vendor_id);
CREATE INDEX idx_carts_tenant_customer_status ON carts(tenant_id, customer_id, status);
CREATE INDEX idx_orders_tenant_customer ON orders(tenant_id, customer_id);
CREATE INDEX idx_orders_tenant_status ON orders(tenant_id, status);
CREATE INDEX idx_payments_tenant_order ON payments(tenant_id, order_id);
CREATE INDEX idx_deliveries_tenant_status ON deliveries(tenant_id, status);
