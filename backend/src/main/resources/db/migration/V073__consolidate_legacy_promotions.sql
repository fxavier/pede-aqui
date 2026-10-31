-- LAC-29 (W7-D), passo "expand + migrate" da consolidação das promoções no modelo spec-002 `promotion` (V030).
--
-- Inventário (2026-10-31): `coupons` (V013) tinha um escritor — MarketingService (POST /marketing/coupons) — e NENHUM
-- leitor no checkout (o cupão legado nunca dava desconto; o checkout só resolve `promotion`). `promotions` (V013) não
-- tinha escritor nem leitor na aplicação (só SQL manual/seeds). Desde W7-D, /marketing/coupons é uma fachada sobre
-- `promotion` e os escritores legados foram removidos do código.
--
-- Esta migração:
--   1. cria `legacy_promotion_migration` (tabela de mapeamento/relatório: uma linha por linha legada, MIGRATED ou
--      SKIPPED com motivo) e marca as linhas legadas migradas (`migrated_to_promotion_id`, `migrated_at`);
--   2. cria a função idempotente `marketing_migrate_legacy_promotions()` e corre-a uma vez. Só trata linhas legadas
--      ainda sem linha no mapeamento, por isso pode ser corrida de novo à mão (ex.: depois de um deploy em rolling
--      em que uma instância antiga ainda gravou em `coupons`) sem duplicar nada:
--        SELECT * FROM marketing_migrate_legacy_promotions();
--   3. NUNCA apaga nem altera dados legados além das duas colunas de marcação. O DROP de `coupons`/`promotions`, da
--      função e do mapeamento é o passo "contract", numa release posterior (ver docs/api/API-MIGRATION.md, W7-D).
--
-- Mapeamento `coupons` → `promotion` (cupão: com código):
--   id = id do cupão (mantém os ids que a fachada devolve; se já existir em `promotion`, gera-se um novo);
--   code = upper(trim(code)) — > 40 caracteres → SKIPPED code_too_long; código já usado por outra promoção do tenant →
--   SKIPPED code_conflict; name = 'Cupão ' || code; type: PERCENTAGE|PERCENT|PCT → PERCENTAGE, FIXED_AMOUNT|FIXED|
--   AMOUNT|VALUE → FIXED_AMOUNT, outro → SKIPPED unknown_discount_type; value = discount_value (percentagem fora de
--   ]0,100] ou fixo ≤ 0 → SKIPPED invalid_value); scope ORDER; min_order_total = min_order_amount (negativo →
--   SKIPPED invalid_min_order); usage_limit = max_uses (≤ 0 → SKIPPED invalid_usage_limit); used_count = uses_count;
--   vendor_id igual; starts_at = valid_from; ends_at = valid_until ou 2099-12-31T23:59:59Z (sem fim; a fachada devolve
--   null para este valor) — ends_at ≤ starts_at → SKIPPED invalid_window; status = ACTIVE se active, senão PAUSED.
--   Consequência assumida: um cupão legado ativo passa a dar desconto quando o cliente o introduz (antes não dava).
-- Mapeamento `promotions` → `promotion` (automática: sem código):
--   applies_to tem de ser ALL_ORDERS (→ scope ORDER), senão SKIPPED unsupported_applies_to; name = left(name, 140);
--   type/value/window como acima; description perde-se (sem coluna); status = PAUSED SEMPRE (motivo
--   paused_for_review nas que estavam ativas): uma promoção automática aplica-se a todos os carrinhos elegíveis, por
--   isso a ativação fica para OPS/ADMIN em /marketing.
--
-- Relatório (correr antes e depois; esperado em dev: 0 linhas legadas):
--   SELECT legacy_table, outcome, reason, count(*) FROM legacy_promotion_migration GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;
--   SELECT 'coupons' t, count(*) FROM coupons WHERE migrated_at IS NULL
--   UNION ALL SELECT 'promotions', count(*) FROM promotions WHERE migrated_at IS NULL;  -- SKIPPED ficam aqui
--
-- Locking: CREATE TABLE/FUNCTION sem locks em tabelas existentes; ADD COLUMN nullable sem default = catálogo, ACCESS
--   EXCLUSIVE breve em coupons/promotions. A migração de dados faz INSERT em `promotion` (RowExclusive; o índice
--   único parcial ux_promotion_tenant_code serializa códigos iguais) e UPDATE das linhas legadas migradas; volumes
--   pequenos (tabelas legadas sem escritor relevante). Corre numa transação (Flyway): ou migra tudo ou nada.
-- Compatibilidade com a versão anterior da aplicação: total para leituras; uma instância antiga ainda escreve em
--   `coupons` (POST /marketing/coupons) e essas linhas não são vistas pela fachada nova até se voltar a correr a
--   função (idempotente). As colunas novas são ignoradas pelo JPA antigo.
-- Rollback (forward-fix numa migração nova, nunca editar esta): DELETE FROM promotion WHERE id IN (SELECT
--   promotion_id FROM legacy_promotion_migration WHERE outcome = 'MIGRATED') AND NOT EXISTS (redenções/encomendas que
--   as referenciem); UPDATE coupons/promotions SET migrated_to_promotion_id = NULL, migrated_at = NULL; DROP FUNCTION
--   marketing_migrate_legacy_promotions(); DROP TABLE legacy_promotion_migration; ALTER TABLE coupons/promotions DROP
--   COLUMN migrated_to_promotion_id, DROP COLUMN migrated_at.

CREATE TABLE legacy_promotion_migration (
    legacy_table VARCHAR(20) NOT NULL,
    legacy_id UUID NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    promotion_id UUID REFERENCES promotion (id) ON DELETE SET NULL,
    outcome VARCHAR(10) NOT NULL,
    reason VARCHAR(40),
    migrated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_legacy_promotion_migration PRIMARY KEY (legacy_table, legacy_id),
    CONSTRAINT ck_legacy_promotion_migration_table CHECK (legacy_table IN ('coupons', 'promotions')),
    CONSTRAINT ck_legacy_promotion_migration_outcome CHECK (outcome IN ('MIGRATED', 'SKIPPED')),
    -- A SKIPPED row always says why; a MIGRATED row loses promotion_id only if that promotion is later deleted.
    CONSTRAINT ck_legacy_promotion_migration_reason CHECK (outcome <> 'SKIPPED' OR reason IS NOT NULL)
);

ALTER TABLE coupons
    ADD COLUMN migrated_to_promotion_id UUID,
    ADD COLUMN migrated_at TIMESTAMPTZ;
ALTER TABLE promotions
    ADD COLUMN migrated_to_promotion_id UUID,
    ADD COLUMN migrated_at TIMESTAMPTZ;

COMMENT ON TABLE legacy_promotion_migration IS 'LAC-29: mapping/report of legacy coupons/promotions rows into promotion (drop in the contract step)';
COMMENT ON COLUMN coupons.migrated_to_promotion_id IS 'LAC-29: promotion row this legacy coupon was migrated to (legacy row kept until the contract step)';
COMMENT ON COLUMN promotions.migrated_to_promotion_id IS 'LAC-29: promotion row this legacy promotion was migrated to (legacy row kept until the contract step)';

CREATE OR REPLACE FUNCTION marketing_migrate_legacy_promotions()
RETURNS TABLE (legacy_table TEXT, outcome TEXT, reason TEXT, row_count BIGINT)
LANGUAGE plpgsql
AS $$
#variable_conflict use_column
DECLARE
    open_end CONSTANT TIMESTAMPTZ := TIMESTAMPTZ '2099-12-31 23:59:59+00';
    r RECORD;
    v_type TEXT;
    v_code TEXT;
    v_ends TIMESTAMPTZ;
    v_skip TEXT;
    v_id UUID;
    v_now TIMESTAMPTZ := now();
BEGIN
    -- Legacy coupons → coupon promotions.
    FOR r IN
        SELECT c.* FROM coupons c
         WHERE NOT EXISTS (SELECT 1 FROM legacy_promotion_migration m WHERE m.legacy_table = 'coupons' AND m.legacy_id = c.id)
         ORDER BY c.created_at, c.id
    LOOP
        v_skip := NULL;
        v_code := upper(btrim(r.code));
        v_type := CASE upper(btrim(r.discount_type))
                      WHEN 'PERCENTAGE' THEN 'PERCENTAGE' WHEN 'PERCENT' THEN 'PERCENTAGE' WHEN 'PCT' THEN 'PERCENTAGE'
                      WHEN 'FIXED_AMOUNT' THEN 'FIXED_AMOUNT' WHEN 'FIXED' THEN 'FIXED_AMOUNT'
                      WHEN 'AMOUNT' THEN 'FIXED_AMOUNT' WHEN 'VALUE' THEN 'FIXED_AMOUNT'
                  END;
        v_ends := COALESCE(r.valid_until, open_end);
        IF v_code = '' OR length(v_code) > 40 THEN
            v_skip := 'code_too_long';
        ELSIF v_type IS NULL THEN
            v_skip := 'unknown_discount_type';
        ELSIF (v_type = 'PERCENTAGE' AND (r.discount_value <= 0 OR r.discount_value > 100))
           OR (v_type = 'FIXED_AMOUNT' AND r.discount_value <= 0) THEN
            v_skip := 'invalid_value';
        ELSIF r.min_order_amount IS NOT NULL AND r.min_order_amount < 0 THEN
            v_skip := 'invalid_min_order';
        ELSIF r.max_uses IS NOT NULL AND r.max_uses <= 0 THEN
            v_skip := 'invalid_usage_limit';
        ELSIF v_ends <= r.valid_from THEN
            v_skip := 'invalid_window';
        ELSIF EXISTS (SELECT 1 FROM promotion p WHERE p.tenant_id = r.tenant_id AND p.code = v_code) THEN
            v_skip := 'code_conflict';
        END IF;

        IF v_skip IS NOT NULL THEN
            INSERT INTO legacy_promotion_migration (legacy_table, legacy_id, tenant_id, promotion_id, outcome, reason, migrated_at)
            VALUES ('coupons', r.id, r.tenant_id, NULL, 'SKIPPED', v_skip, v_now);
            CONTINUE;
        END IF;

        v_id := CASE WHEN EXISTS (SELECT 1 FROM promotion p WHERE p.id = r.id) THEN gen_random_uuid() ELSE r.id END;
        INSERT INTO promotion (id, tenant_id, vendor_id, name, code, type, value, scope, min_order_total, max_discount_amount,
                               starts_at, ends_at, usage_limit, per_customer_limit, used_count, status, created_at, updated_at)
        VALUES (v_id, r.tenant_id, r.vendor_id, 'Cupão ' || v_code, v_code, v_type, r.discount_value, 'ORDER',
                r.min_order_amount, NULL, r.valid_from, v_ends, r.max_uses, NULL, GREATEST(r.uses_count, 0),
                CASE WHEN r.active THEN 'ACTIVE' ELSE 'PAUSED' END, r.created_at, r.updated_at);
        INSERT INTO legacy_promotion_migration (legacy_table, legacy_id, tenant_id, promotion_id, outcome, reason, migrated_at)
        VALUES ('coupons', r.id, r.tenant_id, v_id, 'MIGRATED', NULL, v_now);
        UPDATE coupons SET migrated_to_promotion_id = v_id, migrated_at = v_now WHERE id = r.id;
    END LOOP;

    -- Legacy promotions → automatic promotions, always PAUSED for review.
    FOR r IN
        SELECT p.* FROM promotions p
         WHERE NOT EXISTS (SELECT 1 FROM legacy_promotion_migration m WHERE m.legacy_table = 'promotions' AND m.legacy_id = p.id)
         ORDER BY p.created_at, p.id
    LOOP
        v_skip := NULL;
        v_type := CASE upper(btrim(r.discount_type))
                      WHEN 'PERCENTAGE' THEN 'PERCENTAGE' WHEN 'PERCENT' THEN 'PERCENTAGE' WHEN 'PCT' THEN 'PERCENTAGE'
                      WHEN 'FIXED_AMOUNT' THEN 'FIXED_AMOUNT' WHEN 'FIXED' THEN 'FIXED_AMOUNT'
                      WHEN 'AMOUNT' THEN 'FIXED_AMOUNT' WHEN 'VALUE' THEN 'FIXED_AMOUNT'
                  END;
        v_ends := COALESCE(r.ends_at, open_end);
        IF upper(btrim(r.applies_to)) <> 'ALL_ORDERS' THEN
            v_skip := 'unsupported_applies_to';
        ELSIF v_type IS NULL THEN
            v_skip := 'unknown_discount_type';
        ELSIF (v_type = 'PERCENTAGE' AND (r.discount_value <= 0 OR r.discount_value > 100))
           OR (v_type = 'FIXED_AMOUNT' AND r.discount_value <= 0) THEN
            v_skip := 'invalid_value';
        ELSIF v_ends <= r.starts_at THEN
            v_skip := 'invalid_window';
        END IF;

        IF v_skip IS NOT NULL THEN
            INSERT INTO legacy_promotion_migration (legacy_table, legacy_id, tenant_id, promotion_id, outcome, reason, migrated_at)
            VALUES ('promotions', r.id, r.tenant_id, NULL, 'SKIPPED', v_skip, v_now);
            CONTINUE;
        END IF;

        v_id := CASE WHEN EXISTS (SELECT 1 FROM promotion p WHERE p.id = r.id) THEN gen_random_uuid() ELSE r.id END;
        INSERT INTO promotion (id, tenant_id, vendor_id, name, code, type, value, scope, min_order_total, max_discount_amount,
                               starts_at, ends_at, usage_limit, per_customer_limit, used_count, status, created_at, updated_at)
        VALUES (v_id, r.tenant_id, r.vendor_id, left(r.name, 140), NULL, v_type, r.discount_value, 'ORDER',
                NULL, NULL, r.starts_at, v_ends, NULL, NULL, 0, 'PAUSED', r.created_at, r.updated_at);
        INSERT INTO legacy_promotion_migration (legacy_table, legacy_id, tenant_id, promotion_id, outcome, reason, migrated_at)
        VALUES ('promotions', r.id, r.tenant_id, v_id, 'MIGRATED', CASE WHEN r.active THEN 'paused_for_review' END, v_now);
        UPDATE promotions SET migrated_to_promotion_id = v_id, migrated_at = v_now WHERE id = r.id;
    END LOOP;

    RETURN QUERY
        SELECT m.legacy_table::TEXT, m.outcome::TEXT, m.reason::TEXT, count(*)
          FROM legacy_promotion_migration m
         WHERE m.migrated_at = v_now
         GROUP BY m.legacy_table, m.outcome, m.reason
         ORDER BY 1, 2, 3;
END;
$$;

COMMENT ON FUNCTION marketing_migrate_legacy_promotions() IS
    'LAC-29: idempotent migration of unmigrated legacy coupons/promotions rows into promotion; returns this run''s report';

SELECT * FROM marketing_migrate_legacy_promotions();
