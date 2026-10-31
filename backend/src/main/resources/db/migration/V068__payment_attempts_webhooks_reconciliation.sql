-- V068: PSP integration seam (LAC-31), attempt states / reconciliation (LAC-32) — W7-A.
--
-- 1) payment_attempts: one row per "charge the customer's phone" request against a PaymentProvider (today only the
--    sandbox). State machine INITIATED -> PENDING_CUSTOMER -> SUCCEEDED | FAILED | EXPIRED | UNKNOWN (enforced in
--    payment.entity.PaymentAttemptStatus; the CHECK only pins the vocabulary). UNKNOWN = timeout/ambiguous answer, never
--    a failure — resolved by the payment-status-poller or reconciliation. our_reference is OUR id sent to the PSP
--    (unique platform-wide, webhooks are matched by it before the provider reference is known); provider_reference is
--    unique per provider. At most ONE active (INITIATED/PENDING_CUSTOMER/UNKNOWN) attempt per payment
--    (uq_payment_attempts_active_payment) so a customer cannot be pushed twice for the same order. The phone number is
--    never stored: only its SHA-256 (msisdn_hash) and last 3 digits (LAC-31 §4).
-- 2) payment_webhook_events: provider callbacks, deduplicated by (provider, provider_event_id) among events whose
--    signature verified (partial unique index — an attacker cannot pre-claim a real event id with a forged request).
--    Rows with signature_valid = FALSE are forensics only. Only the SHA-256 of the raw payload is kept (no PII).
-- 3) payment_reconciliation_cases: mismatches found by the payment path or the daily payment-reconciliation job
--    (SUCCEEDED attempt vs CONFIRMED payment, duplicates, amount mismatches, unresolved attempts) for OPS/FINANCE.
--    dedup_key (= case_type:attempt_or_payment_id) makes detection idempotent across runs.
--    No FKs on cases/webhook events on purpose (values, like audit_logs/ledger): they must outlive any cleanup.
--
-- Duplicate validation before the UNIQUE indexes: none needed — all three tables are new and empty.
-- Locking: CREATE TABLE/INDEX on new tables only; the FK payment_attempts -> payments takes a SHARE ROW EXCLUSIVE lock
--   on payments for the instant of the CREATE (no rows validated: the new table is empty). No rewrite of existing tables.
-- Compatibility: additive. The previous application version ignores the new tables; payments keep their columns
--   (a PSP-confirmed payment gets provider = the attempt's provider code, e.g. 'SANDBOX', instead of 'LOCAL_MOCK').
-- Rollback / forward-fix: DROP TABLE payment_reconciliation_cases, payment_webhook_events, payment_attempts;
--   (and DELETE FROM flyway_schema_history WHERE version = '068' before re-applying a corrected V068 in dev only).
--   Rolling back after real PSP traffic loses the attempt trail: export the tables first.

CREATE TABLE payment_attempts (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    payment_id          UUID NOT NULL,
    order_id            UUID NOT NULL,
    provider            VARCHAR(32) NOT NULL,
    our_reference       VARCHAR(40) NOT NULL,
    provider_reference  VARCHAR(128),
    status              VARCHAR(24) NOT NULL,
    amount              NUMERIC(14,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'MZN',
    msisdn_hash         VARCHAR(64),
    msisdn_last3        VARCHAR(3),
    error_code          VARCHAR(64),
    poll_count          INTEGER NOT NULL DEFAULT 0,
    next_poll_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_attempts_payment_id FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    CONSTRAINT uq_payment_attempts_our_reference UNIQUE (our_reference),
    CONSTRAINT ck_payment_attempts_status
        CHECK (status IN ('INITIATED', 'PENDING_CUSTOMER', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'UNKNOWN')),
    CONSTRAINT ck_payment_attempts_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_attempts_currency CHECK (currency = 'MZN'),
    CONSTRAINT ck_payment_attempts_poll_count CHECK (poll_count >= 0)
);

CREATE UNIQUE INDEX uq_payment_attempts_provider_reference
    ON payment_attempts (provider, provider_reference) WHERE provider_reference IS NOT NULL;
CREATE UNIQUE INDEX uq_payment_attempts_active_payment
    ON payment_attempts (payment_id) WHERE status IN ('INITIATED', 'PENDING_CUSTOMER', 'UNKNOWN');
-- Poller / expiry work queue (open attempts only, oldest due first).
CREATE INDEX idx_payment_attempts_open_due
    ON payment_attempts (next_poll_at) WHERE status IN ('INITIATED', 'PENDING_CUSTOMER', 'UNKNOWN');
CREATE INDEX idx_payment_attempts_open_created
    ON payment_attempts (created_at) WHERE status IN ('INITIATED', 'PENDING_CUSTOMER');
CREATE INDEX idx_payment_attempts_tenant_payment ON payment_attempts (tenant_id, payment_id, created_at DESC);
-- Daily reconciliation scans recent successes.
CREATE INDEX idx_payment_attempts_succeeded_completed ON payment_attempts (completed_at) WHERE status = 'SUCCEEDED';

CREATE TABLE payment_webhook_events (
    id                  UUID PRIMARY KEY,
    provider            VARCHAR(32) NOT NULL,
    provider_event_id   VARCHAR(128) NOT NULL,
    signature_valid     BOOLEAN NOT NULL,
    tenant_id           UUID,
    attempt_id          UUID,
    provider_reference  VARCHAR(128),
    reported_status     VARCHAR(24),
    payload_sha256      CHAR(64) NOT NULL,
    outcome             VARCHAR(32) NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL,
    processed_at        TIMESTAMPTZ,
    CONSTRAINT ck_payment_webhook_events_outcome
        CHECK (outcome IN ('PROCESSED', 'IGNORED', 'UNMATCHED', 'INVALID_SIGNATURE'))
);

CREATE UNIQUE INDEX uq_payment_webhook_events_provider_event
    ON payment_webhook_events (provider, provider_event_id) WHERE signature_valid;
CREATE INDEX idx_payment_webhook_events_received ON payment_webhook_events (received_at);

CREATE TABLE payment_reconciliation_cases (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    case_type           VARCHAR(48) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    dedup_key           VARCHAR(120) NOT NULL,
    payment_id          UUID,
    attempt_id          UUID,
    order_id            UUID,
    provider            VARCHAR(32),
    expected_amount     NUMERIC(14,2),
    observed_amount     NUMERIC(14,2),
    detail_code         VARCHAR(64),
    detected_at         TIMESTAMPTZ NOT NULL,
    resolved_at         TIMESTAMPTZ,
    resolved_by         VARCHAR(255),
    resolution_note     VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_reconciliation_cases_dedup UNIQUE (dedup_key),
    CONSTRAINT ck_payment_reconciliation_cases_type
        CHECK (case_type IN ('SUCCEEDED_NOT_CAPTURED', 'DUPLICATE_SUCCESS', 'AMOUNT_MISMATCH', 'ATTEMPT_UNRESOLVED',
                             'CONFIRMED_WITHOUT_ATTEMPT')),
    CONSTRAINT ck_payment_reconciliation_cases_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_payment_reconciliation_cases_tenant_status
    ON payment_reconciliation_cases (tenant_id, status, created_at DESC, id DESC);
