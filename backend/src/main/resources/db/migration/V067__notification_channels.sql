-- V067: multi-channel notifications foundation (LAC-48, W6-C).
--
-- 1) device_tokens: push tokens (FCM/APNs/Web Push) per user profile. A token is not a secret but it is personal
--    data: it is stored raw (the push provider needs it), never logged or returned by the API (only its last 4 chars),
--    and removed on unregister (DELETE /api/v1/me/devices, called by the apps on logout) or when the provider reports
--    it invalid. token_hash (SHA-256 hex of the token) is the global unique key: a device that logs in with another
--    account moves to that account instead of notifying both.
-- 2) notification_templates: pt-PT bodies per (template_key, channel, locale) with {placeholder}s. tenant_id NULL =
--    platform default (seeded below); a tenant row overrides the default (written by PUT
--    /api/v1/ops/notification-templates/{key}/{channel}, audited). allow_sensitive_placeholders is FALSE everywhere:
--    a template may only reference PINs/OTPs/codes when a DBA sets it explicitly (never via the API).
-- 3) notification_deliveries: outbox + per-channel delivery log. Rows are written by NotificationDispatcher in the
--    business transaction (or in the after-commit listener of OrderStatusChanged) and sent by the
--    notification-delivery job (claim with FOR UPDATE SKIP LOCKED + lease, retries with exponential backoff).
--    idempotency_key is UNIQUE: a repeated dispatch (at-least-once listener, retried request) inserts nothing.
--    No recipient address is stored: phone/e-mail/tokens are resolved at send time; recipient_masked holds only the
--    masked form written after the attempt. last_error is a short code, never a provider message.
--
-- FKs to tenants/app_user_profiles are ON DELETE CASCADE on purpose: tokens and delivery logs are personal data that
-- must follow a profile erasure (LAC-63), and they are not business records.
--
-- Locking: CREATE TABLE/INDEX on new tables only; the FKs take a SHARE ROW EXCLUSIVE lock on tenants and
--   app_user_profiles for the instant of the CREATE (no rows are validated: the new tables are empty).
-- Compatibility: additive; the previous application version ignores the new tables.
-- Rollback / forward-fix: DROP TABLE notification_deliveries, notification_templates, device_tokens;
--   (and DELETE FROM flyway_schema_history WHERE version = '067' before re-applying a corrected V067 in dev only).

CREATE TABLE device_tokens (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    platform        VARCHAR(10) NOT NULL,
    token           VARCHAR(4096) NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    last_seen_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_device_tokens_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    CONSTRAINT fk_device_tokens_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_device_tokens_user_profile_id FOREIGN KEY (user_profile_id) REFERENCES app_user_profiles (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_device_tokens_token_hash ON device_tokens (token_hash);
CREATE INDEX idx_device_tokens_tenant_profile ON device_tokens (tenant_id, user_profile_id);

CREATE TABLE notification_templates (
    id                           UUID PRIMARY KEY,
    tenant_id                    UUID,
    template_key                 VARCHAR(80) NOT NULL,
    channel                      VARCHAR(10) NOT NULL,
    locale                       VARCHAR(10) NOT NULL DEFAULT 'pt-PT',
    title                        VARCHAR(180),
    body                         TEXT NOT NULL,
    active                       BOOLEAN NOT NULL DEFAULT TRUE,
    allow_sensitive_placeholders BOOLEAN NOT NULL DEFAULT FALSE,
    version                      INTEGER NOT NULL DEFAULT 1,
    updated_by                   VARCHAR(255),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_templates_channel CHECK (channel IN ('IN_APP', 'PUSH', 'SMS', 'EMAIL')),
    CONSTRAINT ck_notification_templates_version CHECK (version >= 1),
    CONSTRAINT fk_notification_templates_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);
-- One platform default and at most one override per tenant for each (key, channel, locale).
CREATE UNIQUE INDEX uq_notification_templates_default ON notification_templates (template_key, channel, locale)
    WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uq_notification_templates_tenant ON notification_templates (tenant_id, template_key, channel, locale)
    WHERE tenant_id IS NOT NULL;

CREATE TABLE notification_deliveries (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    recipient_profile_id UUID NOT NULL,
    channel              VARCHAR(10) NOT NULL,
    template_key         VARCHAR(80) NOT NULL,
    template_version     INTEGER NOT NULL,
    locale               VARCHAR(10) NOT NULL,
    title                VARCHAR(180),
    body                 TEXT NOT NULL,
    business_reference   VARCHAR(120),
    status               VARCHAR(10) NOT NULL,
    attempts             INTEGER NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ NOT NULL,
    last_error           VARCHAR(80),
    provider_message_id  VARCHAR(255),
    recipient_masked     VARCHAR(80),
    idempotency_key      VARCHAR(200) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    sent_at              TIMESTAMPTZ,
    CONSTRAINT ck_notification_deliveries_channel CHECK (channel IN ('IN_APP', 'PUSH', 'SMS', 'EMAIL')),
    CONSTRAINT ck_notification_deliveries_status CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_notification_deliveries_attempts CHECK (attempts >= 0),
    CONSTRAINT uq_notification_deliveries_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_notification_deliveries_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_deliveries_recipient_profile_id FOREIGN KEY (recipient_profile_id) REFERENCES app_user_profiles (id) ON DELETE CASCADE
);
-- Work queue of the notification-delivery job (only QUEUED rows, oldest due first).
CREATE INDEX idx_notification_deliveries_due ON notification_deliveries (next_attempt_at) WHERE status = 'QUEUED';
-- SUPPORT delivery log (newest first, per tenant and per recipient).
CREATE INDEX idx_notification_deliveries_tenant_created ON notification_deliveries (tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_notification_deliveries_tenant_recipient_created
    ON notification_deliveries (tenant_id, recipient_profile_id, created_at DESC, id DESC);
CREATE INDEX idx_notification_deliveries_recipient ON notification_deliveries (recipient_profile_id);

-- Platform default templates (pt-PT). Only {orderReference} is used: no PII, no amounts, never a PIN/OTP.
-- IN_APP templates of the status keys are seeded inactive: the in-app inbox keeps its current content until the
-- clients show the new notifications (enable per tenant with PUT .../{key}/IN_APP {"active": true, ...}).
INSERT INTO notification_templates (id, tenant_id, template_key, channel, locale, title, body, active) VALUES
    (gen_random_uuid(), NULL, 'order_accepted', 'PUSH', 'pt-PT', 'Pedido aceite',
     'A loja aceitou o seu pedido {orderReference} e vai começar a prepará-lo.', TRUE),
    (gen_random_uuid(), NULL, 'order_accepted', 'IN_APP', 'pt-PT', 'Pedido aceite',
     'A loja aceitou o seu pedido {orderReference} e vai começar a prepará-lo.', FALSE),
    (gen_random_uuid(), NULL, 'order_ready_for_pickup', 'PUSH', 'pt-PT', 'Pedido pronto',
     'O seu pedido {orderReference} está pronto e aguarda o estafeta.', TRUE),
    (gen_random_uuid(), NULL, 'order_ready_for_pickup', 'IN_APP', 'pt-PT', 'Pedido pronto',
     'O seu pedido {orderReference} está pronto e aguarda o estafeta.', FALSE),
    (gen_random_uuid(), NULL, 'order_delivered', 'PUSH', 'pt-PT', 'Pedido entregue',
     'O seu pedido {orderReference} foi entregue. Obrigado por comprar no Pede Aqui.', TRUE),
    (gen_random_uuid(), NULL, 'order_delivered', 'IN_APP', 'pt-PT', 'Pedido entregue',
     'O seu pedido {orderReference} foi entregue. Obrigado por comprar no Pede Aqui.', FALSE),
    (gen_random_uuid(), NULL, 'order_rejected', 'PUSH', 'pt-PT', 'Pedido recusado',
     'A loja não pôde aceitar o seu pedido {orderReference}. O valor pago será devolvido.', TRUE),
    (gen_random_uuid(), NULL, 'order_rejected', 'SMS', 'pt-PT', NULL,
     'Pede Aqui: o seu pedido {orderReference} foi recusado pela loja. O valor pago será devolvido.', TRUE),
    (gen_random_uuid(), NULL, 'order_cancelled_vendor_timeout', 'PUSH', 'pt-PT', 'Pedido cancelado',
     'A loja não aceitou o seu pedido {orderReference} a tempo. O valor pago será devolvido.', TRUE),
    (gen_random_uuid(), NULL, 'order_cancelled_vendor_timeout', 'SMS', 'pt-PT', NULL,
     'Pede Aqui: o seu pedido {orderReference} foi cancelado porque a loja não o aceitou a tempo. O valor pago será devolvido.', TRUE),
    (gen_random_uuid(), NULL, 'order_cancelled', 'PUSH', 'pt-PT', 'Pedido cancelado',
     'O seu pedido {orderReference} foi cancelado.', TRUE),
    (gen_random_uuid(), NULL, 'order_cancellation_approved', 'PUSH', 'pt-PT', 'Cancelamento aprovado',
     'O cancelamento do seu pedido {orderReference} foi aprovado.', TRUE),
    (gen_random_uuid(), NULL, 'order_cancellation_rejected', 'PUSH', 'pt-PT', 'Cancelamento recusado',
     'O pedido de cancelamento da encomenda {orderReference} não foi aprovado; o pedido continua.', TRUE);
