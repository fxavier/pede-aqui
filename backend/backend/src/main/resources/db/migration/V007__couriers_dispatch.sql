CREATE TABLE couriers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_profile_id UUID NOT NULL REFERENCES app_user_profiles(id),
    verification_status VARCHAR(30) NOT NULL,
    available BOOLEAN NOT NULL DEFAULT FALSE,
    operating_zone_id UUID,
    rating DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_couriers_tenant_user UNIQUE (tenant_id, user_profile_id)
);

CREATE TABLE dispatch_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    delivery_id UUID NOT NULL REFERENCES deliveries(id),
    courier_id UUID REFERENCES couriers(id),
    status VARCHAR(30) NOT NULL,
    rejection_reason VARCHAR(500),
    assigned_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_couriers_tenant_verification_available ON couriers(tenant_id, verification_status, available);
CREATE INDEX idx_dispatch_jobs_tenant_status ON dispatch_jobs(tenant_id, status);
CREATE INDEX idx_dispatch_jobs_tenant_courier ON dispatch_jobs(tenant_id, courier_id);
