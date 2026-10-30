-- LAC-33 (W6-B, ADR-0005 *Proposed*): razão de partidas dobradas do módulo `settlement`.
--
-- Modelo: a plataforma é AGENTE DE COBRANÇA (o comerciante é o vendedor de registo). O razão regista o dinheiro
-- recebido por conta de terceiros, o que se deve aos comerciantes (e, mais tarde, aos estafetas) e a receita própria
-- da plataforma (comissão + taxas). Os lançamentos estão descritos em docs/adr/ADR-0005-ledger.md e estão
-- "a validar" com o contabilista/parecer jurídico (LAC-38).
--
-- Tabelas:
--   ledger_accounts      — uma conta por (tenant, tipo, dono, moeda); contas da plataforma têm owner_id NULL,
--                          MERCHANT_PAYABLE tem owner = vendor, COURIER_PAYABLE owner = courier (ainda sem lançamentos).
--                          Criadas a pedido pelo módulo (INSERT ... ON CONFLICT DO NOTHING).
--   ledger_transactions  — um lançamento (cabeçalho). idempotency_key UNIQUE por tenant: um evento entregue duas
--                          vezes (at-least-once) não duplica lançamentos.
--   ledger_entries       — linhas: débito OU crédito (> 0), NUMERIC(14,2) (D5/ADR-0003), moeda explícita 'MZN'.
--
-- Invariantes na base de dados:
--   * Append-only: UPDATE/DELETE/TRUNCATE em ledger_transactions e ledger_entries falham com SQLSTATE 42501
--     (sem válvula de purga, ao contrário de audit_logs — correções fazem-se com lançamentos de estorno).
--     ledger_accounts aceita UPDATE só de `name`; DELETE/TRUNCATE recusados.
--   * Equilíbrio: CONSTRAINT TRIGGER DEFERRABLE INITIALLY DEFERRED em ledger_entries verifica, no COMMIT, que
--     Σ débitos = Σ créditos por transação e moeda; outro em ledger_transactions recusa uma transação sem linhas.
--     Um lançamento desequilibrado faz rollback da transação inteira (SQLSTATE 23514).
--   * Sem FKs para tenants/orders/vendors/couriers de propósito: o razão é um registo histórico imutável que nunca
--     deve bloquear (nem ser bloqueado por) limpezas de dados mestre; as referências são por valor, como em
--     audit_logs. FKs internas (entries -> transactions, entries -> accounts) existem.
--
-- Locking: só CREATE TABLE/INDEX/FUNCTION/TRIGGER sobre tabelas novas — nenhum lock em tabelas existentes.
-- Compatibilidade com a versão anterior da aplicação: total (tabelas novas que a versão anterior ignora).
-- Rollback / forward-fix (nova migração; nunca editar esta). Perde o razão — só antes de haver dinheiro real:
--   DROP VIEW IF EXISTS ledger_account_balances;
--   DROP TABLE IF EXISTS ledger_entries; DROP TABLE IF EXISTS ledger_transactions; DROP TABLE IF EXISTS ledger_accounts;
--   DROP FUNCTION IF EXISTS ledger_reject_mutation(); DROP FUNCTION IF EXISTS ledger_accounts_reject_mutation();
--   DROP FUNCTION IF EXISTS ledger_entries_check_balance(); DROP FUNCTION IF EXISTS ledger_transactions_check_not_empty();
--   (V066 depende de V065 só por convenção — reverter V066 primeiro.)

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    account_type VARCHAR(40) NOT NULL CHECK (account_type IN (
        'PLATFORM_CASH_CLEARING', 'CUSTOMER_FUNDS_HELD', 'CUSTOMER_REFUNDS_PAYABLE', 'MERCHANT_PAYABLE',
        'COURIER_PAYABLE', 'PLATFORM_REVENUE_COMMISSION', 'PLATFORM_REVENUE_FEES', 'PLATFORM_PROMOTION_EXPENSE',
        'PLATFORM_VAT_PAYABLE')),
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('ASSET', 'LIABILITY', 'REVENUE', 'EXPENSE')),
    owner_type VARCHAR(20) NOT NULL CHECK (owner_type IN ('PLATFORM', 'MERCHANT', 'COURIER')),
    owner_id UUID,
    currency CHAR(3) NOT NULL DEFAULT 'MZN' CHECK (currency = 'MZN'),
    name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_ledger_accounts_owner CHECK (
        (owner_type = 'PLATFORM' AND owner_id IS NULL) OR (owner_type <> 'PLATFORM' AND owner_id IS NOT NULL)),
    CONSTRAINT uq_ledger_accounts_tenant_type_owner_currency UNIQUE NULLS NOT DISTINCT (tenant_id, account_type, owner_id, currency)
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    transaction_type VARCHAR(40) NOT NULL CHECK (transaction_type IN (
        'PAYMENT_CAPTURED', 'ORDER_DELIVERED', 'REFUND_BEFORE_DELIVERY', 'REFUND_AFTER_DELIVERY',
        'CANCELLATION_FEE_RETAINED')),
    source_type VARCHAR(30) NOT NULL,
    source_id UUID NOT NULL,
    order_id UUID,
    vendor_id UUID,
    currency CHAR(3) NOT NULL DEFAULT 'MZN' CHECK (currency = 'MZN'),
    occurred_at TIMESTAMPTZ NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_ledger_transactions_tenant_idempotency UNIQUE (tenant_id, idempotency_key)
);

-- Keyset listing (GET /finance/ledger/transactions, newest first) and per-order / per-vendor lookups.
CREATE INDEX idx_ledger_transactions_tenant_created_id ON ledger_transactions (tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_ledger_transactions_tenant_order ON ledger_transactions (tenant_id, order_id) WHERE order_id IS NOT NULL;
CREATE INDEX idx_ledger_transactions_tenant_vendor_created ON ledger_transactions (tenant_id, vendor_id, created_at DESC, id DESC)
    WHERE vendor_id IS NOT NULL;

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transaction_id UUID NOT NULL REFERENCES ledger_transactions (id),
    account_id UUID NOT NULL REFERENCES ledger_accounts (id),
    debit NUMERIC(14,2) NOT NULL DEFAULT 0,
    credit NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'MZN' CHECK (currency = 'MZN'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_ledger_entries_one_side CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);

CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_entries_account ON ledger_entries (account_id);
CREATE INDEX idx_ledger_entries_tenant ON ledger_entries (tenant_id);

-- Append-only for transactions and entries (no purge escape: corrections are reversing postings).
CREATE OR REPLACE FUNCTION ledger_reject_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% rejected)', TG_TABLE_NAME, TG_OP USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER trg_ledger_transactions_append_only
    BEFORE UPDATE OR DELETE ON ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION ledger_reject_mutation();
CREATE TRIGGER trg_ledger_transactions_no_truncate
    BEFORE TRUNCATE ON ledger_transactions
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_reject_mutation();
CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_reject_mutation();
CREATE TRIGGER trg_ledger_entries_no_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_reject_mutation();

-- Accounts: only the display name may change; identity columns are fixed, rows are never deleted.
CREATE OR REPLACE FUNCTION ledger_accounts_reject_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.id = OLD.id AND NEW.tenant_id = OLD.tenant_id AND NEW.account_type = OLD.account_type
       AND NEW.kind = OLD.kind AND NEW.owner_type = OLD.owner_type
       AND NEW.owner_id IS NOT DISTINCT FROM OLD.owner_id AND NEW.currency = OLD.currency
       AND NEW.created_at = OLD.created_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'ledger_accounts identity is immutable (% rejected)', TG_OP USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER trg_ledger_accounts_immutable
    BEFORE UPDATE OR DELETE ON ledger_accounts
    FOR EACH ROW EXECUTE FUNCTION ledger_accounts_reject_mutation();
CREATE TRIGGER trg_ledger_accounts_no_truncate
    BEFORE TRUNCATE ON ledger_accounts
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_reject_mutation();

-- Σ debit = Σ credit per transaction and currency, checked at COMMIT (deferred), once per inserted entry.
CREATE OR REPLACE FUNCTION ledger_entries_check_balance() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    unbalanced RECORD;
BEGIN
    SELECT currency, SUM(debit) AS debits, SUM(credit) AS credits INTO unbalanced
      FROM ledger_entries
     WHERE transaction_id = NEW.transaction_id
     GROUP BY currency
    HAVING SUM(debit) <> SUM(credit)
     LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'ledger transaction % is unbalanced in % (debits %, credits %)',
            NEW.transaction_id, unbalanced.currency, unbalanced.debits, unbalanced.credits
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_entries_balanced
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_check_balance();

-- A transaction header without lines is rejected at COMMIT too.
CREATE OR REPLACE FUNCTION ledger_transactions_check_not_empty() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ledger_entries WHERE transaction_id = NEW.id) THEN
        RAISE EXCEPTION 'ledger transaction % has no entries', NEW.id USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_transactions_not_empty
    AFTER INSERT ON ledger_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_transactions_check_not_empty();

-- Balances are derived (no stored running balance): debit-normal accounts read debit - credit, the others credit - debit.
CREATE VIEW ledger_account_balances AS
SELECT a.id AS account_id, a.tenant_id, a.account_type, a.kind, a.owner_type, a.owner_id, a.currency,
       COALESCE(SUM(e.debit), 0) AS total_debit,
       COALESCE(SUM(e.credit), 0) AS total_credit,
       CASE WHEN a.kind IN ('ASSET', 'EXPENSE') THEN COALESCE(SUM(e.debit), 0) - COALESCE(SUM(e.credit), 0)
            ELSE COALESCE(SUM(e.credit), 0) - COALESCE(SUM(e.debit), 0) END AS balance
  FROM ledger_accounts a
  LEFT JOIN ledger_entries e ON e.account_id = a.id
 GROUP BY a.id;

COMMENT ON TABLE ledger_transactions IS 'LAC-33 double-entry ledger header; append-only; idempotency_key unique per tenant (ADR-0005)';
COMMENT ON TABLE ledger_entries IS 'LAC-33 ledger lines; append-only; balanced per transaction at COMMIT (deferred constraint trigger)';
