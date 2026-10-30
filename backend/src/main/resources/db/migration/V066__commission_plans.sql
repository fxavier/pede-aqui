-- LAC-34 (W6-B, ADR-0005 *Proposed*): planos de comissão e escritor da tabela `commissions`.
--
-- commission_plans            — taxa (fração 0..1 sobre o subtotal de bens do comerciante) com vigência
--                               [effective_from, effective_to). is_default = plano do tenant para comerciantes sem
--                               override. Vários planos por defeito podem coexistir no tempo; vence o de
--                               effective_from mais recente ainda em vigor (o índice parcial só impede dois com o
--                               mesmo início). Alterar uma taxa = novo plano/vigência; o razão grava o valor aplicado.
-- vendor_commission_overrides — override por comerciante: plano específico OU taxa própria (exatamente um), com
--                               vigência. Vence o override em vigor com effective_from mais recente.
-- Resolução no momento DELIVERED: override do comerciante → plano por defeito do tenant →
--   app.settlement.default-commission-rate (0.15). `fee_policies.commission_rate` (V010) NÃO é lido — fica obsoleto.
--
-- Seed: um plano por defeito "Comissão padrão 15%" (0.15) por tenant existente, em vigor desde 2026-01-01.
--
-- commissions (V008, até aqui sem escritor) passa a ser escrita pelo módulo settlement no mesmo commit do lançamento
-- ORDER_DELIVERED / CANCELLATION_FEE_RETAINED (comissão BRUTA acumulada; estornos por reembolso ficam só no razão).
-- Colunas novas (todas NULLABLE ou com default — linhas antigas, que não existem em nenhum ambiente conhecido, ficam
-- com os defaults): plan_id, currency ('MZN'), source ('ORDER_DELIVERED' | 'CANCELLATION_FEE'), ledger_transaction_id.
-- Índice único (tenant_id, order_id): uma comissão por encomenda — garante a idempotência do escritor.
--   Validação prévia de duplicados (a tabela não tinha escritor; esperado 0 linhas):
--     SELECT tenant_id, order_id, COUNT(*) FROM commissions GROUP BY 1, 2 HAVING COUNT(*) > 1;
--   Se houver duplicados a migração falha (bloco DO abaixo) — resolver manualmente, nunca apagar sem aprovação.
--
-- Locking:
--   * CREATE TABLE x2 + seed: só tabelas novas.
--   * ALTER TABLE commissions ADD COLUMN (x4, com default constante => sem rewrite em PG11+): ACCESS EXCLUSIVE breve.
--   * CREATE UNIQUE INDEX em commissions: SHARE (bloqueia escritas durante a construção); tabela vazia/pequena.
-- Compatibilidade com a versão anterior da aplicação: total — o entity Commission (finance) não mapeia as colunas
--   novas e só lê; os leitores (dashboard, sales, reports) continuam a somar commission_amount.
-- Rollback / forward-fix (nova migração; nunca editar esta):
--   DROP INDEX IF EXISTS uq_commissions_tenant_order;
--   ALTER TABLE commissions DROP COLUMN IF EXISTS ledger_transaction_id, DROP COLUMN IF EXISTS source,
--     DROP COLUMN IF EXISTS currency, DROP COLUMN IF EXISTS plan_id;
--   DROP TABLE IF EXISTS vendor_commission_overrides; DROP TABLE IF EXISTS commission_plans;

CREATE TABLE commission_plans (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name VARCHAR(120) NOT NULL,
    rate NUMERIC(6,4) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_commission_plans_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX uq_commission_plans_default_start ON commission_plans (tenant_id, effective_from) WHERE is_default;
CREATE INDEX idx_commission_plans_tenant ON commission_plans (tenant_id, effective_from DESC);

CREATE TABLE vendor_commission_overrides (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    vendor_id UUID NOT NULL REFERENCES vendors (id),
    plan_id UUID REFERENCES commission_plans (id),
    rate NUMERIC(6,4) CHECK (rate >= 0 AND rate <= 1),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_vendor_commission_overrides_one_source CHECK ((plan_id IS NULL) <> (rate IS NULL)),
    CONSTRAINT ck_vendor_commission_overrides_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX uq_vendor_commission_overrides_start ON vendor_commission_overrides (tenant_id, vendor_id, effective_from);

INSERT INTO commission_plans (id, tenant_id, name, rate, is_default, effective_from)
SELECT gen_random_uuid(), t.id, 'Comissão padrão 15%', 0.1500, TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+00'
  FROM tenants t;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM commissions GROUP BY tenant_id, order_id HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'V066: duplicate (tenant_id, order_id) rows in commissions; resolve them before applying uq_commissions_tenant_order';
    END IF;
END;
$$;

ALTER TABLE commissions
    ADD COLUMN plan_id UUID,
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'MZN',
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'ORDER_DELIVERED',
    ADD COLUMN ledger_transaction_id UUID;

CREATE UNIQUE INDEX uq_commissions_tenant_order ON commissions (tenant_id, order_id);
