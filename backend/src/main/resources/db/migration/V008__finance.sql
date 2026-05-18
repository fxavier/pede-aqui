CREATE TABLE commissions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    vendor_id UUID NOT NULL,
    basis_amount NUMERIC(12,2) NOT NULL,
    commission_rate NUMERIC(6,4) NOT NULL,
    commission_amount NUMERIC(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cash_reconciliations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    courier_id UUID,
    delivery_id UUID NOT NULL REFERENCES deliveries(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    amount NUMERIC(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    reconciled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_commissions_tenant_status ON commissions(tenant_id, status);
CREATE INDEX idx_commissions_tenant_created ON commissions(tenant_id, created_at);
CREATE INDEX idx_cash_reconciliations_tenant_status ON cash_reconciliations(tenant_id, status);
