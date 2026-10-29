-- LAC-25 / LAC-15 (W4-A): histórico de estados da encomenda e do pagamento + índice dos jobs de timeout.
--
-- order_status_history   — uma linha por transição da encomenda, escrita pela OrderStateMachine na MESMA transação
--                          que muda orders.status (criação no checkout: from_status NULL -> PAYMENT_PENDING).
-- payment_status_history — uma linha por mudança de payments.status (criação, confirmação, reembolso parcial/total,
--                          expiração), escrita pela PaymentService na mesma transação.
-- Colunas comuns: actor_type USER (actor_id = sub Keycloak) | SYSTEM (actor_id = nome do job, ou NULL);
--   reason = motivo curto (ex.: PAYMENT_EXPIRED, VENDOR_TIMEOUT, SELLER_REJECTED, texto do override) — nunca PINs,
--   segredos nem dados pessoais; correlation_id = X-Correlation-Id do pedido ou jobRunId do job.
--
-- Append-only (mesmo padrão de audit_logs, V053): trigger BEFORE UPDATE OR DELETE rejeita (SQLSTATE 42501) e um
--   trigger de instrução BEFORE TRUNCATE também. Exceções:
--   * DELETE em cascata a partir de orders/payments (pg_trigger_depth() > 1: a FK ON DELETE CASCADE corre dentro do
--     trigger de RI do pai). A aplicação nunca apaga encomendas nem pagamentos; a retenção futura (LAC-63) apaga a
--     encomenda e o seu histórico juntos.
--   * DELETE explícito numa transação com `SET LOCAL app.status_history_purge = 'on'` (purga controlada). UPDATE
--     nunca é permitido.
--   Mesmo dono da tabela para migrações e app, por isso REVOKE não teria efeito (ver V053).
--
-- Índice dos jobs (PaymentExpiryJob / AcceptanceTimeoutJob): orders (status, created_at) parcial em
--   PAYMENT_PENDING/PAYMENT_CONFIRMED — o conjunto de encomendas vivas nesses estados é pequeno, o índice também.
--
-- Locking:
--   * CREATE TABLE x2 (novas) com FKs: SHARE ROW EXCLUSIVE em orders e payments só durante o CREATE (instantâneo,
--     sem linhas a validar).
--   * CREATE INDEX nas tabelas novas: vazias, instantâneo.
--   * CREATE INDEX idx_orders_status_timeouts em orders: SHARE (bloqueia escritas em orders, não leituras) durante a
--     construção; parcial, só lê as linhas existentes uma vez. Sem CONCURRENTLY porque o Flyway corre a migração
--     numa transação (mesmo critério de V046/V057). Em produção com muitas encomendas: janela de manutenção ou
--     criar o índice antes com CONCURRENTLY e manter o IF NOT EXISTS.
--   * CREATE FUNCTION / CREATE TRIGGER nas tabelas novas: sem impacto em tabelas existentes.
-- Compatibilidade com a versão anterior da aplicação: total — tabelas novas que ela ignora; o índice parcial não
--   muda nenhum plano existente de forma incompatível. Encomendas anteriores a esta migração não têm histórico
--   (o histórico começa na primeira transição depois do deploy); não há backfill (não há fonte fiável).
-- Rollback / forward-fix (nova migração; nunca editar esta):
--   DROP TABLE IF EXISTS order_status_history; DROP TABLE IF EXISTS payment_status_history;
--   DROP FUNCTION IF EXISTS status_history_reject_mutation();
--   DROP INDEX IF EXISTS idx_orders_status_timeouts;
--   (a aplicação desta versão deixaria de arrancar — ddl-auto validate — por isso rollback = voltar também a app.)
-- Sem UNIQUE: nada a validar quanto a duplicados.

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(40),
    to_status VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id VARCHAR(255),
    reason VARCHAR(500),
    correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_order_status_history_order ON order_status_history (tenant_id, order_id, created_at, id);

CREATE TABLE payment_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    order_id UUID NOT NULL,
    from_status VARCHAR(40),
    to_status VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id VARCHAR(255),
    reason VARCHAR(500),
    correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_status_history_payment ON payment_status_history (tenant_id, payment_id, created_at, id);
CREATE INDEX idx_payment_status_history_order ON payment_status_history (tenant_id, order_id, created_at, id);

CREATE INDEX IF NOT EXISTS idx_orders_status_timeouts ON orders (status, created_at)
    WHERE status IN ('PAYMENT_PENDING', 'PAYMENT_CONFIRMED');

CREATE OR REPLACE FUNCTION status_history_reject_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND (pg_trigger_depth() > 1 OR current_setting('app.status_history_purge', true) = 'on') THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION '% is append-only (% rejected)', TG_TABLE_NAME, TG_OP USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER trg_order_status_history_append_only
    BEFORE UPDATE OR DELETE ON order_status_history
    FOR EACH ROW EXECUTE FUNCTION status_history_reject_mutation();

CREATE TRIGGER trg_order_status_history_no_truncate
    BEFORE TRUNCATE ON order_status_history
    FOR EACH STATEMENT EXECUTE FUNCTION status_history_reject_mutation();

CREATE TRIGGER trg_payment_status_history_append_only
    BEFORE UPDATE OR DELETE ON payment_status_history
    FOR EACH ROW EXECUTE FUNCTION status_history_reject_mutation();

CREATE TRIGGER trg_payment_status_history_no_truncate
    BEFORE TRUNCATE ON payment_status_history
    FOR EACH STATEMENT EXECUTE FUNCTION status_history_reject_mutation();
