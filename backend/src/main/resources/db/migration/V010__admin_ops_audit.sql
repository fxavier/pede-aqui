CREATE TABLE zones (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    geometry_wkt TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE fee_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
    delivery_fee NUMERIC(12,2) NOT NULL,
    service_fee NUMERIC(12,2) NOT NULL,
    tax_rate NUMERIC(8,4) NOT NULL,
    commission_rate NUMERIC(8,4) NOT NULL,
    cancellation_policy VARCHAR(600) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    actor_user_id VARCHAR(120) NOT NULL,
    action VARCHAR(120) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(120),
    business_reference VARCHAR(120),
    result VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_zones_tenant_status ON zones(tenant_id, status);
CREATE INDEX idx_audit_logs_tenant_created ON audit_logs(tenant_id, created_at DESC);
