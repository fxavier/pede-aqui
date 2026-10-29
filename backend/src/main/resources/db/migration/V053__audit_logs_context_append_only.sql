-- LAC-15 (FR-026, NFR-011): contexto de pedido em audit_logs + tabela append-only.
--
-- Colunas novas (todas NULLABLE, sem default — linhas antigas ficam NULL):
--   ip             — endereço remoto do pedido (request.getRemoteAddr(); não confia em X-Forwarded-For).
--   user_agent     — cabeçalho User-Agent truncado a 512 caracteres.
--   correlation_id — X-Correlation-Id do pedido (MDC do CorrelationIdFilter, INC-14).
--   details        — JSONB com contexto estruturado da ação; chaves sensíveis (pin, code, otp, password,
--                    token, secret) já chegam mascaradas pela AuditLogService. Nunca segredos nem PINs.
--
-- Append-only: um trigger BEFORE UPDATE/DELETE rejeita a operação (SQLSTATE 42501) e um trigger de
--   instrução BEFORE TRUNCATE faz o mesmo. A aplicação corre com o dono da tabela (as migrações e a app
--   usam o mesmo utilizador), por isso REVOKE UPDATE/DELETE não teria efeito; quando houver um utilizador
--   de aplicação separado, acrescentar REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM <app_user>.
--   Saída prevista para a retenção (LAC-63): um job futuro (W2-A) pode apagar linhas antigas dentro de uma
--   transação que faça `SET LOCAL app.audit_logs_purge = 'on'` — só DELETE é desbloqueado, UPDATE nunca.
--   Retenção alvo: 24 meses. Ainda NÃO existe job de purga; até lá a tabela cresce sem limite.
--
-- Locking:
--   * ADD COLUMN sem default (x4, numa só ALTER): ACCESS EXCLUSIVE breve, só metadados (sem rewrite).
--   * CREATE INDEX parcial em correlation_id: SHARE (bloqueia escritas, não leituras) durante a construção;
--     as linhas existentes têm correlation_id NULL, logo o índice nasce vazio e a construção é instantânea.
--     (Sem CONCURRENTLY: o Flyway corre a migração numa transação — mesmo critério de V046.)
--   * CREATE FUNCTION: sem lock de tabela.
--   * CREATE TRIGGER (x2): SHARE ROW EXCLUSIVE breve (bloqueia escritas concorrentes durante o instante da DDL).
-- Compatibilidade com a versão anterior da aplicação: total — colunas nullable que a versão anterior não
--   mapeia, e a versão anterior nunca fazia UPDATE/DELETE em audit_logs (só INSERT e SELECT).
-- Rollback / forward-fix (nova migração; nunca editar esta):
--   DROP TRIGGER IF EXISTS trg_audit_logs_append_only ON audit_logs;
--   DROP TRIGGER IF EXISTS trg_audit_logs_no_truncate ON audit_logs;
--   DROP FUNCTION IF EXISTS audit_logs_reject_mutation();
--   DROP INDEX IF EXISTS idx_audit_logs_correlation_id;
--   ALTER TABLE audit_logs DROP COLUMN IF EXISTS details, DROP COLUMN IF EXISTS correlation_id,
--     DROP COLUMN IF EXISTS user_agent, DROP COLUMN IF EXISTS ip;   -- perde o contexto já gravado
--
-- Fora de âmbito (documentado em docs/gaps): order_status_history e payment_status_history pertencem à
--   máquina de estados (W4-A / LAC-25).

ALTER TABLE audit_logs
    ADD COLUMN ip VARCHAR(64),
    ADD COLUMN user_agent VARCHAR(512),
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN details JSONB;

CREATE INDEX IF NOT EXISTS idx_audit_logs_correlation_id ON audit_logs (correlation_id) WHERE correlation_id IS NOT NULL;

CREATE OR REPLACE FUNCTION audit_logs_reject_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.audit_logs_purge', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_logs is append-only (% rejected)', TG_OP USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_mutation();

CREATE TRIGGER trg_audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_reject_mutation();
