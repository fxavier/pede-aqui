CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    recipient_user_id UUID NOT NULL REFERENCES app_user_profiles(id),
    recipient_role VARCHAR(40) NOT NULL,
    type VARCHAR(80) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message TEXT NOT NULL,
    business_reference VARCHAR(120),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notifications_tenant_recipient_created ON notifications(tenant_id, recipient_user_id, created_at DESC);
CREATE INDEX idx_notifications_tenant_role_created ON notifications(tenant_id, recipient_role, created_at DESC);
