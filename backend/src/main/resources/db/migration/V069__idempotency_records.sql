-- V069: generic Idempotency-Key support for money-moving endpoints (LAC-24 generalisation) — W7-A.
--
-- idempotency_records holds one row per (tenant, caller, Idempotency-Key) seen on an endpoint listed in
-- app.idempotency.endpoints (common/idempotency/IdempotencyFilter). request_fingerprint = SHA-256 of
-- method + path + query + canonical JSON body: the same key with another fingerprint is a 409 idempotency_key_reused.
-- state IN_PROGRESS while the first request runs (a concurrent duplicate gets 409 idempotency_request_in_progress),
-- COMPLETED with the stored 2xx response (status, content type, body capped by app.idempotency.max-response-bytes),
-- replayed verbatim to later requests with the same key + fingerprint. Non-2xx outcomes are not stored (the row is
-- deleted, so the client may retry with the same key). Rows expire after app.idempotency.ttl (24 h) and are purged by
-- the idempotency-cleanup job. actor_subject = JWT subject (opaque id, as in audit_logs) — keys never collide across
-- callers. The domain idempotency that already exists (checkout key, refund keys, Sales Idempotency-Key header) is
-- untouched and still applies behind this layer.
--
-- Duplicate validation before the UNIQUE index: none needed — new, empty table.
-- Locking: CREATE TABLE/INDEX on a new table only (no FKs: values, the rows are short-lived).
-- Compatibility: additive; the previous application version ignores the table and the optional header.
-- Rollback / forward-fix: DROP TABLE idempotency_records;
--   (and DELETE FROM flyway_schema_history WHERE version = '069' before re-applying a corrected V069 in dev only).

CREATE TABLE idempotency_records (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    actor_subject         VARCHAR(255) NOT NULL,
    idempotency_key       VARCHAR(128) NOT NULL,
    request_fingerprint   CHAR(64) NOT NULL,
    state                 VARCHAR(16) NOT NULL,
    response_status       INTEGER,
    response_content_type VARCHAR(255),
    response_body         BYTEA,
    created_at            TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_records_tenant_actor_key UNIQUE (tenant_id, actor_subject, idempotency_key),
    CONSTRAINT ck_idempotency_records_state CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_records_completed
        CHECK (state = 'IN_PROGRESS' OR response_status IS NOT NULL)
);

CREATE INDEX idx_idempotency_records_expires ON idempotency_records (expires_at);
