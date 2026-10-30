-- W6-A (LAC-22 + restos de LAC-20) — checkout v2: IVA incluído nos preços, categoria fiscal por produto,
-- decomposição do IVA na quote da encomenda, quotes de checkout com validade e distância máxima de entrega.
--
-- PRESSUPOSTOS FISCAIS — "a validar com contabilista" (LAC-38):
--   * IVA normal em Moçambique = 16 % (Lei n.º 22/2022, desde 2023-01-01).
--   * Os preços de catálogo são publicados COM IVA incluído (B2C); o IVA é EXTRAÍDO (preço × t / (1 + t)), nunca somado.
--   * A plataforma atua como agente de cobrança: o IVA dos bens pertence ao comerciante (vendedor).
--   * As taxas da plataforma (entrega, serviço) são tratadas também como IVA incluído à taxa normal — o enquadramento
--     fiscal destas taxas TEM de ser validado pelo contabilista.
--   * ZERO/EXEMPT existem para uso futuro; a escolha de que produtos são isentos/taxa zero é fiscal.
--
-- 1. products.tax_category  VARCHAR(20) NOT NULL DEFAULT 'STANDARD'  (STANDARD | ZERO | EXEMPT).
-- 2. fee_policies
--    - tax_inclusive BOOLEAN NOT NULL DEFAULT TRUE: todas as políticas passam a ser "IVA incluído".
--    - max_delivery_distance_km NUMERIC(8,2) NULL (NULL = sem limite).
--    - ALTERAÇÃO DE DADOS: tax_rate = 0.0800 → 0.1600 (e version + 1, para que as quotes novas registem a nova versão
--      da regra). 0.08 era a constante errada do MVP (seed V048 e default de código); não há forma de distinguir uma
--      linha semeada de uma que um admin tenha configurado explicitamente a 0.08, por isso TODAS as linhas a 0.08 são
--      corrigidas. Linhas com outro valor (configuradas por um admin) mantêm a taxa, mas passam a ser interpretadas
--      como IVA incluído (tax_inclusive = TRUE). Para manter o comportamento antigo num tenant concreto:
--        UPDATE fee_policies SET tax_inclusive = FALSE, tax_rate = 0.08 WHERE tenant_id = '<tenant>';
--    Impacto nos totais (cesto 18.00, política semeada): antes 18.00 + 5.00 + 0.90 + 1.44 (8 % somado) = 25.34;
--    agora 18.00 + 5.00 + 0.90 = 23.90 com IVA incluído de 3.29 (2.48 bens + 0.69 entrega + 0.12 serviço). Ver relatório W6-A (tabela antigo → novo).
-- 3. vendors.max_delivery_distance_km NUMERIC(8,2) NULL — sobrepõe-se ao limite da política do tenant.
-- 4. orders: decomposição do IVA da quote aplicada (imutável, junta-se ao trigger de V048) + quote_id usado.
-- 5. order_items: tax_category, vat_rate e vat_amount (IVA da linha antes de desconto) — NULL nas linhas antigas.
-- 6. checkout_quotes: quote com validade (GET /checkout/quote → quoteId + expiresAt; POST /checkout { quoteId }).
--
-- Locking:
--   * ADD COLUMN com DEFAULT constante / nullable: só catálogo em PG >= 11 (ACCESS EXCLUSIVE breve, sem rewrite).
--   * CHECK em products/fee_policies/vendors: ADD ... NOT VALID (sem varrimento) + VALIDATE CONSTRAINT
--     (SHARE UPDATE EXCLUSIVE: não bloqueia leituras nem escritas); tabelas pequenas.
--   * UPDATE fee_policies: uma linha por tenant.
--   * CREATE TABLE checkout_quotes + índices: tabela nova, sem contenção.
--   * CREATE OR REPLACE FUNCTION do trigger de imutabilidade: sem lock em orders (o trigger já existe).
-- Compatibilidade com a versão anterior da aplicação (rolling deploy):
--   * Colunas novas são nullable/com default e não mapeadas pela versão anterior (ddl-auto=validate ignora-as).
--   * ATENÇÃO: a versão anterior lê fee_policies.tax_rate e SOMA o imposto. Depois desta migração somaria 16 % em vez
--     de 8 % enquanto coexistir com a nova. Fazer o deploy SEM sobreposição (parar as instâncias antigas antes de
--     migrar) ou aceitar a janela; ver Riscos no relatório W6-A.
--   * O trigger estendido só atua quando a quote já existe e compara colunas que a versão anterior nunca escreve.
-- Validação antes/depois:
--   SELECT tenant_id, tax_rate, tax_inclusive, version, cancellation_policy FROM fee_policies ORDER BY tenant_id;
--   SELECT tax_category, count(*) FROM products GROUP BY 1;   -- tudo STANDARD
-- Rollback (preferir forward-fix; perde as quotes e a decomposição do IVA):
--   aplicação anterior primeiro; depois
--   UPDATE fee_policies SET tax_rate = 0.0800 WHERE tax_rate = 0.1600 AND tax_inclusive;   -- (só as linhas corrigidas aqui)
--   DROP TABLE checkout_quotes;
--   ALTER TABLE order_items DROP COLUMN tax_category, DROP COLUMN vat_rate, DROP COLUMN vat_amount;
--   ALTER TABLE orders DROP COLUMN quote_id, DROP COLUMN quote_tax_inclusive, ... (todas as quote_* novas);
--   recriar orders_pricing_quote_immutable() de V048;
--   ALTER TABLE vendors DROP COLUMN max_delivery_distance_km;
--   ALTER TABLE fee_policies DROP COLUMN tax_inclusive, DROP COLUMN max_delivery_distance_km;
--   ALTER TABLE products DROP COLUMN tax_category;
--   DELETE FROM flyway_schema_history WHERE version = '64';

-- 1. products --------------------------------------------------------------------------------------------------
ALTER TABLE products ADD COLUMN tax_category VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE products ADD CONSTRAINT ck_products_tax_category CHECK (tax_category IN ('STANDARD', 'ZERO', 'EXEMPT')) NOT VALID;
ALTER TABLE products VALIDATE CONSTRAINT ck_products_tax_category;

-- 2. fee_policies ----------------------------------------------------------------------------------------------
ALTER TABLE fee_policies
    ADD COLUMN tax_inclusive BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN max_delivery_distance_km NUMERIC(8,2);
ALTER TABLE fee_policies ADD CONSTRAINT ck_fee_policies_max_delivery_distance
    CHECK (max_delivery_distance_km IS NULL OR max_delivery_distance_km > 0) NOT VALID;
ALTER TABLE fee_policies VALIDATE CONSTRAINT ck_fee_policies_max_delivery_distance;

UPDATE fee_policies
   SET tax_rate = 0.1600, version = version + 1, updated_at = now()
 WHERE tax_rate = 0.0800;

-- 3. vendors ---------------------------------------------------------------------------------------------------
ALTER TABLE vendors ADD COLUMN max_delivery_distance_km NUMERIC(8,2);
ALTER TABLE vendors ADD CONSTRAINT ck_vendors_max_delivery_distance
    CHECK (max_delivery_distance_km IS NULL OR max_delivery_distance_km > 0) NOT VALID;
ALTER TABLE vendors VALIDATE CONSTRAINT ck_vendors_max_delivery_distance;

-- 4. orders: VAT breakdown of the applied quote ----------------------------------------------------------------
ALTER TABLE orders
    ADD COLUMN quote_id UUID,
    ADD COLUMN quote_tax_inclusive BOOLEAN,
    ADD COLUMN quote_goods_standard_gross NUMERIC(12,2),
    ADD COLUMN quote_goods_zero_gross NUMERIC(12,2),
    ADD COLUMN quote_goods_exempt_gross NUMERIC(12,2),
    ADD COLUMN quote_goods_vat NUMERIC(12,2),
    ADD COLUMN quote_fees_vat NUMERIC(12,2),
    ADD COLUMN quote_vat_total NUMERIC(12,2),
    ADD COLUMN quote_net_total NUMERIC(12,2);

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
        OR NEW.quoted_at IS DISTINCT FROM OLD.quoted_at
        OR NEW.quote_id IS DISTINCT FROM OLD.quote_id
        OR NEW.quote_tax_inclusive IS DISTINCT FROM OLD.quote_tax_inclusive
        OR NEW.quote_goods_standard_gross IS DISTINCT FROM OLD.quote_goods_standard_gross
        OR NEW.quote_goods_zero_gross IS DISTINCT FROM OLD.quote_goods_zero_gross
        OR NEW.quote_goods_exempt_gross IS DISTINCT FROM OLD.quote_goods_exempt_gross
        OR NEW.quote_goods_vat IS DISTINCT FROM OLD.quote_goods_vat
        OR NEW.quote_fees_vat IS DISTINCT FROM OLD.quote_fees_vat
        OR NEW.quote_vat_total IS DISTINCT FROM OLD.quote_vat_total
        OR NEW.quote_net_total IS DISTINCT FROM OLD.quote_net_total) THEN
        RAISE EXCEPTION 'order pricing quote is immutable (order %)', OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 5. order_items -----------------------------------------------------------------------------------------------
ALTER TABLE order_items
    ADD COLUMN tax_category VARCHAR(20),
    ADD COLUMN vat_rate NUMERIC(8,4),
    ADD COLUMN vat_amount NUMERIC(12,2);

-- 6. checkout_quotes -------------------------------------------------------------------------------------------
CREATE TABLE checkout_quotes (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    customer_id     UUID NOT NULL,
    cart_id         UUID NOT NULL,
    address_id      UUID,
    vendor_id       UUID,
    subtotal        NUMERIC(12,2) NOT NULL,
    delivery_fee    NUMERIC(12,2) NOT NULL,
    service_fee     NUMERIC(12,2) NOT NULL,
    taxes           NUMERIC(12,2) NOT NULL,
    discount_total  NUMERIC(12,2) NOT NULL,
    total           NUMERIC(12,2) NOT NULL,
    vat_total       NUMERIC(12,2) NOT NULL,
    tax_inclusive   BOOLEAN NOT NULL,
    engine_version  VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_checkout_quotes_customer_id FOREIGN KEY (customer_id) REFERENCES app_user_profiles (id),
    -- A quote is meaningless without its cart; carts are not deleted today, CASCADE keeps a future purge simple.
    CONSTRAINT fk_checkout_quotes_cart_id FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT ck_checkout_quotes_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_checkout_quotes_cart ON checkout_quotes (tenant_id, cart_id);
CREATE INDEX idx_checkout_quotes_customer ON checkout_quotes (customer_id);
CREATE INDEX idx_checkout_quotes_expires_at ON checkout_quotes (expires_at);

COMMENT ON COLUMN products.tax_category IS 'VAT category: STANDARD (fee_policies.tax_rate) | ZERO | EXEMPT (W6-A; a validar com contabilista)';
COMMENT ON COLUMN fee_policies.tax_inclusive IS 'TRUE: catalogue prices and fees include VAT (extracted); FALSE: tax_rate added on top (pre-W6-A)';
COMMENT ON COLUMN fee_policies.max_delivery_distance_km IS 'Maximum straight-line delivery distance (km); NULL = no limit; vendors.max_delivery_distance_km overrides';
COMMENT ON COLUMN orders.quote_vat_total IS 'VAT contained in the order total (W6-A); NULL for pre-W6-A orders, whose VAT is orders.taxes';
COMMENT ON TABLE checkout_quotes IS 'Checkout quotes with TTL (W6-A): POST /checkout with quoteId fails 409 quote_changed when expired or amounts moved';
