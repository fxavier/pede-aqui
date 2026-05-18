CREATE TABLE vendors (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(180) NOT NULL,
    category_id UUID,
    status VARCHAR(30) NOT NULL,
    verification_status VARCHAR(30) NOT NULL,
    rating DOUBLE PRECISION NOT NULL DEFAULT 0,
    estimated_delivery_minutes INTEGER NOT NULL DEFAULT 45,
    available BOOLEAN NOT NULL DEFAULT FALSE,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE vendor_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    document_type VARCHAR(80) NOT NULL,
    storage_key VARCHAR(300) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE vendor_opening_hours (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    opens_at TIME,
    closes_at TIME,
    closed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_vendors_tenant_status ON vendors(tenant_id, status);
CREATE INDEX idx_vendors_tenant_available ON vendors(tenant_id, available);
CREATE INDEX idx_vendors_tenant_category ON vendors(tenant_id, category_id);
CREATE INDEX idx_vendor_documents_tenant_vendor ON vendor_documents(tenant_id, vendor_id);
