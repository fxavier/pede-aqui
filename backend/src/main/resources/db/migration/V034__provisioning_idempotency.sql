-- Durable idempotency ledger for the M2M tenant-provisioning endpoint.
CREATE TABLE provisioning_requests (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    owner_email VARCHAR(200) NOT NULL,
    external_ref VARCHAR(120),
    tenant_id UUID REFERENCES tenants(id),
    status VARCHAR(30) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_provisioning_requests_owner_email ON provisioning_requests(owner_email);
CREATE INDEX idx_provisioning_requests_created_at ON provisioning_requests(created_at);
