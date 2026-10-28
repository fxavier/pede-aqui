-- INC-13 (AS-23, FR-021, FR-063) — motor de pricing: fee_policies/delivery_fee_rules passam a ser lidas
-- pelo pricing e a quote aplicada fica gravada (imutável) na encomenda.
--
-- 1. fee_policies
--    - service_fee_rate: percentagem do subtotal (fração, 0.0500 = 5 %). service_fee continua a ser um
--      valor fixo (NUMERIC(12,2), como sempre foi). Taxa de serviço = service_fee + subtotal × service_fee_rate.
--      Linhas pré-existentes (configuradas via PUT /admin/policy) ficam com rate 0, ou seja, passam a cobrar
--      exatamente o que configuraram (antes eram ignoradas).
--    - version: bloqueio otimista (@Version) e versão da regra gravada na quote da encomenda.
--    - Seed: um registo por tenant SEM política, que reproduz o checkout atual (entrega 5.00, serviço 5 %,
--      IVA 8 %), para que os totais não mudem para tenants existentes. commission_rate = 0 (não é usada no
--      pricing; decisão financeira futura). Tenants criados depois desta migração sem política usam os
--      mesmos valores como default de plataforma no código (PricingService, basis PLATFORM_DEFAULT).
-- 2. delivery_fee_rules: version (@Version, rastreabilidade na quote) + CHECK de faixa coerente.
-- 3. orders: colunas quote_* (snapshot da quote aplicada no checkout) + trigger de imutabilidade.
--
-- Locking: ADD COLUMN com DEFAULT constante e ADD COLUMN nullable são só metadados em PG ≥ 11 (ACCESS
-- EXCLUSIVE breve, sem reescrita). ADD CONSTRAINT ... NOT VALID não percorre a tabela (as linhas antigas
-- não são validadas; novas escritas sim). INSERT do seed: uma linha por tenant sem política (poucas linhas).
-- CREATE TRIGGER: SHARE ROW EXCLUSIVE breve em orders.
-- Compatibilidade com a versão anterior da aplicação: total — colunas novas com default/nullable que ela
-- ignora; a versão anterior nunca lê fee_policies no pricing, por isso o seed não altera os seus totais; o
-- trigger só atua quando a quote já existe (a versão anterior nunca a escreve). Atenção: a versão anterior
-- atualiza fee_policies sem incrementar version (inofensivo).
-- Validação antes do deploy (linhas antigas que a nova versão passaria a aplicar; taxas > 1 são recusadas
-- pelo pricing com 409 pricing_policy_invalid em vez de cobrar 800 %):
--   SELECT tenant_id, delivery_fee, service_fee, tax_rate FROM fee_policies WHERE tax_rate > 1 OR delivery_fee < 0;
--   SELECT id, vendor_id, min_km, max_km, fee_amount FROM delivery_fee_rules WHERE min_km < 0 OR max_km < min_km OR fee_amount < 0;
-- Rollback (preferir forward-fix; perde as quotes gravadas):
--   DROP TRIGGER trg_orders_pricing_quote_immutable ON orders; DROP FUNCTION orders_pricing_quote_immutable();
--   ALTER TABLE orders DROP COLUMN quote_engine_version, ... (todas as quote_* e quoted_at);
--   ALTER TABLE delivery_fee_rules DROP CONSTRAINT ck_delivery_fee_rules_band, DROP COLUMN version;
--   DELETE FROM fee_policies WHERE cancellation_policy = 'Default policy seeded by V048 (INC-13)' AND version = 0;
--   ALTER TABLE fee_policies DROP CONSTRAINT ck_fee_policies_amounts, DROP COLUMN service_fee_rate, DROP COLUMN version;

ALTER TABLE fee_policies
    ADD COLUMN service_fee_rate NUMERIC(8,4) NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE fee_policies
    ADD CONSTRAINT ck_fee_policies_amounts CHECK (
        delivery_fee >= 0 AND service_fee >= 0
        AND service_fee_rate BETWEEN 0 AND 1
        AND tax_rate BETWEEN 0 AND 1
        AND commission_rate BETWEEN 0 AND 1) NOT VALID;

INSERT INTO fee_policies (id, tenant_id, delivery_fee, service_fee, service_fee_rate, tax_rate, commission_rate,
                          cancellation_policy, created_at, updated_at, version)
SELECT gen_random_uuid(), t.id, 5.00, 0.00, 0.0500, 0.0800, 0.0000,
       'Default policy seeded by V048 (INC-13)', now(), now(), 0
  FROM tenants t
 WHERE NOT EXISTS (SELECT 1 FROM fee_policies f WHERE f.tenant_id = t.id);

ALTER TABLE delivery_fee_rules
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE delivery_fee_rules
    ADD CONSTRAINT ck_delivery_fee_rules_band CHECK (min_km >= 0 AND max_km >= min_km AND fee_amount >= 0) NOT VALID;

ALTER TABLE orders
    ADD COLUMN quote_engine_version VARCHAR(20),
    ADD COLUMN quote_delivery_fee NUMERIC(12,2),
    ADD COLUMN quote_service_fee NUMERIC(12,2),
    ADD COLUMN quote_service_fee_rate NUMERIC(8,4),
    ADD COLUMN quote_tax_rate NUMERIC(8,4),
    ADD COLUMN quote_delivery_fee_basis VARCHAR(30),
    ADD COLUMN quote_distance_km NUMERIC(8,2),
    ADD COLUMN quote_fee_policy_id UUID,
    ADD COLUMN quote_fee_policy_version BIGINT,
    ADD COLUMN quote_delivery_fee_rule_id UUID,
    ADD COLUMN quote_delivery_fee_rule_version BIGINT,
    ADD COLUMN quoted_at TIMESTAMPTZ;

-- Uma vez gravada, a quote não muda (a aplicação também a mapeia com updatable = false).
CREATE OR REPLACE FUNCTION orders_pricing_quote_immutable() RETURNS trigger AS $$
BEGIN
    IF OLD.quote_engine_version IS NOT NULL AND (
           NEW.quote_engine_version IS DISTINCT FROM OLD.quote_engine_version
        OR NEW.quote_delivery_fee IS DISTINCT FROM OLD.quote_delivery_fee
        OR NEW.quote_service_fee IS DISTINCT FROM OLD.quote_service_fee
        OR NEW.quote_service_fee_rate IS DISTINCT FROM OLD.quote_service_fee_rate
        OR NEW.quote_tax_rate IS DISTINCT FROM OLD.quote_tax_rate
        OR NEW.quote_delivery_fee_basis IS DISTINCT FROM OLD.quote_delivery_fee_basis
        OR NEW.quote_distance_km IS DISTINCT FROM OLD.quote_distance_km
        OR NEW.quote_fee_policy_id IS DISTINCT FROM OLD.quote_fee_policy_id
        OR NEW.quote_fee_policy_version IS DISTINCT FROM OLD.quote_fee_policy_version
        OR NEW.quote_delivery_fee_rule_id IS DISTINCT FROM OLD.quote_delivery_fee_rule_id
        OR NEW.quote_delivery_fee_rule_version IS DISTINCT FROM OLD.quote_delivery_fee_rule_version
        OR NEW.quoted_at IS DISTINCT FROM OLD.quoted_at) THEN
        RAISE EXCEPTION 'order pricing quote is immutable (order %)', OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_pricing_quote_immutable
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION orders_pricing_quote_immutable();

COMMENT ON COLUMN fee_policies.service_fee_rate IS 'Service fee as a fraction of the subtotal; service fee = service_fee (flat) + subtotal * service_fee_rate';
COMMENT ON COLUMN orders.quote_engine_version IS 'Pricing engine version of the quote applied at checkout (INC-13); quote_* columns are immutable, NULL for pre-INC-13 orders';
COMMENT ON COLUMN orders.quote_delivery_fee_basis IS 'DISTANCE_RULE | POLICY_BASE | PLATFORM_DEFAULT | EMPTY_CART';
