CREATE TABLE support_tickets (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    creator_user_id VARCHAR(120) NOT NULL,
    order_id UUID REFERENCES orders(id),
    subject VARCHAR(220) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    classification VARCHAR(40) NOT NULL,
    internal_notes TEXT,
    assignee_user_id VARCHAR(120),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_support_tickets_tenant_status ON support_tickets(tenant_id, status);
CREATE INDEX idx_support_tickets_tenant_creator ON support_tickets(tenant_id, creator_user_id);
CREATE INDEX idx_support_tickets_tenant_order ON support_tickets(tenant_id, order_id);
CREATE INDEX idx_support_tickets_tenant_assignee ON support_tickets(tenant_id, assignee_user_id);
