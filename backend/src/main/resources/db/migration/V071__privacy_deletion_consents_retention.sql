-- W7-C (LAC-19, LAC-63) — eliminação de conta, consentimentos versionados e registo das execuções de retenção.
--
-- 1. account_deletion_requests: pedido de eliminação do titular. PENDING durante o período de reflexão
--    (app.privacy.deletion.reflection-period, 30 dias por defeito) e cancelável; depois o job
--    account-deletion pseudonimiza a conta (COMPLETED). Um só pedido PENDING por perfil (índice parcial).
--    keycloak_user_id é guardado no pedido para o job poder repetir a eliminação no Keycloak se esta falhar
--    depois de a base de dados já estar pseudonimizada (estado ERASED).
-- 2. consents: consentimentos por finalidade e versão do texto (termos, privacidade, marketing). Conceder cria uma
--    linha nova; retirar preenche withdrawn_at. Um só consentimento ativo por (titular, finalidade).
-- 3. data_retention_runs: uma linha por classe de dados e execução dos jobs retention-cleaner e account-deletion
--    (prova de execução da política de retenção; ela própria purgada pelo RetentionCleaner).
-- 4. orders_delivery_address_immutable(): o snapshot de morada continua imutável, EXCETO numa transação que faça
--    `SET LOCAL app.personal_data_erasure = 'on'` (pseudonimização pelo job; mesmo padrão das flags de purga de
--    audit_logs/status history). Substitui o procedimento manual "DISABLE TRIGGER" descrito em V047.
-- 5. Índices BRIN em created_at nas tabelas append-only/volumosas purgadas por data (o RetentionCleaner apaga por
--    lotes "created_at < cutoff" em todas as linhas, sem filtro de tenant, que os índices existentes não servem).
--
-- Validação de duplicados antes dos índices únicos: não aplicável — os únicos parciais são sobre tabelas novas e vazias.
--
-- Locking:
--   * CREATE TABLE / índices nas tabelas novas: instantâneo. As FKs para tenants/app_user_profiles tomam SHARE ROW
--     EXCLUSIVE nessas tabelas por instantes (bloqueia escritas, não leituras).
--   * CREATE OR REPLACE FUNCTION: sem lock de tabela; o trigger existente passa a usar a nova versão.
--   * CREATE INDEX ... USING brin em audit_logs, notifications, notification_deliveries, order_status_history,
--     payment_status_history: SHARE em cada tabela durante a construção (bloqueia escritas, não leituras). BRIN é
--     uma leitura sequencial sem ordenação — segundos mesmo com milhões de linhas. Em produção com tabelas muito
--     grandes, pode criar-se antes à mão com CREATE INDEX CONCURRENTLY IF NOT EXISTS (mesmo nome) — o IF NOT EXISTS
--     daqui torna-se então no-op.
-- Compatibilidade com a versão anterior da aplicação: total — tabelas novas que ela ignora; a função do trigger tem
--   o mesmo comportamento quando a flag não está ativa (a versão anterior nunca a ativa); índices só aceleram.
-- Rollback (preferir forward-fix; perde pedidos/consentimentos registados):
--   DROP TABLE data_retention_runs; DROP TABLE consents; DROP TABLE account_deletion_requests;
--   DROP INDEX IF EXISTS brin_audit_logs_created_at, brin_notifications_created_at,
--        brin_notification_deliveries_created_at, brin_order_status_history_created_at,
--        brin_payment_status_history_created_at;
--   e recriar orders_delivery_address_immutable() com o corpo de V047.

CREATE TABLE account_deletion_requests (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id),
    profile_id       UUID         NOT NULL REFERENCES app_user_profiles (id),
    keycloak_user_id VARCHAR(120) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    requested_at     TIMESTAMPTZ  NOT NULL,
    scheduled_for    TIMESTAMPTZ  NOT NULL,
    cancelled_at     TIMESTAMPTZ,
    erased_at        TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    attempts         INTEGER      NOT NULL DEFAULT 0,
    last_error       VARCHAR(80),
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_account_deletion_requests_status CHECK (status IN ('PENDING', 'CANCELLED', 'ERASED', 'COMPLETED')),
    CONSTRAINT ck_account_deletion_requests_schedule CHECK (scheduled_for >= requested_at)
);

-- Um só pedido em curso (PENDING ou ERASED) por perfil.
CREATE UNIQUE INDEX uq_account_deletion_requests_open
    ON account_deletion_requests (profile_id) WHERE status IN ('PENDING', 'ERASED');
-- Fila do job: pedidos vencidos, mais antigos primeiro.
CREATE INDEX idx_account_deletion_requests_due
    ON account_deletion_requests (scheduled_for) WHERE status IN ('PENDING', 'ERASED');
CREATE INDEX idx_account_deletion_requests_profile ON account_deletion_requests (tenant_id, profile_id, requested_at DESC);

COMMENT ON TABLE account_deletion_requests IS
    'LAC-19: account deletion requests — PENDING (reflection, cancellable) -> ERASED (DB pseudonymised, Keycloak deletion pending) -> COMPLETED; or CANCELLED';

CREATE TABLE consents (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID        NOT NULL REFERENCES tenants (id),
    subject_profile_id UUID        NOT NULL REFERENCES app_user_profiles (id),
    purpose            VARCHAR(40) NOT NULL,
    version            VARCHAR(40) NOT NULL,
    granted_at         TIMESTAMPTZ NOT NULL,
    withdrawn_at       TIMESTAMPTZ,
    CONSTRAINT ck_consents_purpose CHECK (purpose IN ('TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'MARKETING')),
    CONSTRAINT ck_consents_withdrawn CHECK (withdrawn_at IS NULL OR withdrawn_at >= granted_at)
);

CREATE UNIQUE INDEX uq_consents_active ON consents (subject_profile_id, purpose) WHERE withdrawn_at IS NULL;
CREATE INDEX idx_consents_tenant_subject ON consents (tenant_id, subject_profile_id, granted_at DESC);

COMMENT ON TABLE consents IS 'LAC-63: versioned consents per subject and purpose (grant = new row, withdraw = withdrawn_at)';

CREATE TABLE data_retention_runs (
    id            UUID PRIMARY KEY,
    job           VARCHAR(60)  NOT NULL,
    job_run_id    VARCHAR(64),
    data_class    VARCHAR(60)  NOT NULL,
    retention     VARCHAR(30),
    cutoff        TIMESTAMPTZ,
    rows_affected BIGINT       NOT NULL DEFAULT 0,
    result        VARCHAR(20)  NOT NULL,
    error_code    VARCHAR(120),
    started_at    TIMESTAMPTZ  NOT NULL,
    finished_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_data_retention_runs_result CHECK (result IN ('SUCCESS', 'FAILURE', 'SKIPPED'))
);

CREATE INDEX idx_data_retention_runs_class_started ON data_retention_runs (data_class, started_at DESC);
CREATE INDEX idx_data_retention_runs_started ON data_retention_runs (started_at);

COMMENT ON TABLE data_retention_runs IS 'LAC-63: one row per data class per retention/erasure job run (no personal data)';

-- O snapshot de morada só pode mudar numa transação de pseudonimização (SET LOCAL app.personal_data_erasure = 'on').
CREATE OR REPLACE FUNCTION orders_delivery_address_immutable() RETURNS trigger AS $$
BEGIN
    IF current_setting('app.personal_data_erasure', true) = 'on' THEN
        RETURN NEW;
    END IF;
    IF OLD.delivery_line1 IS NOT NULL AND (
           NEW.delivery_address_id IS DISTINCT FROM OLD.delivery_address_id
        OR NEW.delivery_recipient_name IS DISTINCT FROM OLD.delivery_recipient_name
        OR NEW.delivery_phone IS DISTINCT FROM OLD.delivery_phone
        OR NEW.delivery_line1 IS DISTINCT FROM OLD.delivery_line1
        OR NEW.delivery_line2 IS DISTINCT FROM OLD.delivery_line2
        OR NEW.delivery_city IS DISTINCT FROM OLD.delivery_city
        OR NEW.delivery_state IS DISTINCT FROM OLD.delivery_state
        OR NEW.delivery_postal_code IS DISTINCT FROM OLD.delivery_postal_code
        OR NEW.delivery_latitude IS DISTINCT FROM OLD.delivery_latitude
        OR NEW.delivery_longitude IS DISTINCT FROM OLD.delivery_longitude
        OR NEW.delivery_instructions IS DISTINCT FROM OLD.delivery_instructions) THEN
        RAISE EXCEPTION 'order delivery address snapshot is immutable (order %)', OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE INDEX IF NOT EXISTS brin_audit_logs_created_at ON audit_logs USING brin (created_at);
CREATE INDEX IF NOT EXISTS brin_notifications_created_at ON notifications USING brin (created_at);
CREATE INDEX IF NOT EXISTS brin_notification_deliveries_created_at ON notification_deliveries USING brin (created_at);
CREATE INDEX IF NOT EXISTS brin_order_status_history_created_at ON order_status_history USING brin (created_at);
CREATE INDEX IF NOT EXISTS brin_payment_status_history_created_at ON payment_status_history USING brin (created_at);
