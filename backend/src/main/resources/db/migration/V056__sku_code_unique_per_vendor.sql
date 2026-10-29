-- LAC-16 / ADR-0001 (unicidades a rever): o código de SKU passa a ser único por (tenant_id, vendor_id, sku_code)
-- em vez de (tenant_id, sku_code) (uq_skus_tenant_code, V003). Com tenant = operador, duas lojas do mesmo
-- operador têm de poder usar o mesmo código ("ARROZ-1KG") sem colidir.
--
-- skus não tinha vendor_id (o vendor vem de products.vendor_id). Uma UNIQUE não atravessa tabelas, por isso:
--   * skus.vendor_id é uma coluna DERIVADA: o trigger trg_skus_derive_vendor_id copia-a SEMPRE de
--     products.vendor_id em cada INSERT/UPDATE de product_id/vendor_id (o valor enviado pelo cliente é ignorado);
--   * trg_products_propagate_vendor_id mantém-na coerente se products.vendor_id mudar;
--   * a entidade Sku mapeia-a como só-leitura (insertable/updatable = false).
--
-- Validação de duplicados (deve devolver 0 linhas; a nova chave é MAIS FRACA do que a antiga, logo os dados que
-- respeitavam uq_skus_tenant_code respeitam forçosamente a nova — não há limpeza a fazer):
--   SELECT s.tenant_id, p.vendor_id, s.sku_code, COUNT(*)
--     FROM skus s JOIN products p ON p.id = s.product_id
--    GROUP BY s.tenant_id, p.vendor_id, s.sku_code HAVING COUNT(*) > 1;
-- Linhas sem produto (impossível: FK skus.product_id validada desde V003):
--   SELECT COUNT(*) FROM skus s LEFT JOIN products p ON p.id = s.product_id WHERE p.id IS NULL;
--
-- Locking (Flyway corre a migração numa única transação):
--   * ADD COLUMN sem default: ACCESS EXCLUSIVE só de catálogo (instantâneo);
--   * UPDATE de backfill: reescreve todas as linhas de skus (ROW EXCLUSIVE; escritas concorrentes em skus esperam
--     pelo commit). Volume MVP: sub-segundo;
--   * SET NOT NULL: ACCESS EXCLUSIVE + scan completo de skus;
--   * ADD CONSTRAINT FK (validada na hora, porque as linhas acabaram de ser preenchidas a partir de products, cuja
--     FK para vendors já está validada — V051): SHARE ROW EXCLUSIVE em skus e vendors durante o scan;
--   * ADD CONSTRAINT UNIQUE: constrói o índice com SHARE em skus (escritas esperam); DROP CONSTRAINT: ACCESS
--     EXCLUSIVE breve;
--   * CREATE TRIGGER: SHARE ROW EXCLUSIVE breve em skus e products.
--   Em produção com tabelas grandes: CREATE UNIQUE INDEX CONCURRENTLY uq_skus_tenant_vendor_code ... fora do Flyway
--   e ADD CONSTRAINT ... UNIQUE USING INDEX aqui.
-- Compatibilidade com a versão anterior da aplicação: total. A versão anterior não conhece skus.vendor_id; os seus
--   INSERT omitem a coluna e o trigger preenche-a. A unicidade fica mais permissiva: nenhum INSERT antes aceite é
--   agora recusado. O único efeito visível é que a mesma sku_code pode existir em duas lojas do mesmo tenant.
-- Rollback / forward-fix (nova migração; só possível enquanto não existirem códigos repetidos entre lojas do mesmo
--   tenant — validar com SELECT tenant_id, sku_code, COUNT(*) FROM skus GROUP BY 1,2 HAVING COUNT(*) > 1):
--   ALTER TABLE skus ADD CONSTRAINT uq_skus_tenant_code UNIQUE (tenant_id, sku_code);
--   ALTER TABLE skus DROP CONSTRAINT uq_skus_tenant_vendor_code;
--   DROP TRIGGER trg_products_propagate_vendor_id ON products; DROP TRIGGER trg_skus_derive_vendor_id ON skus;
--   DROP FUNCTION skus_propagate_vendor_id(); DROP FUNCTION skus_derive_vendor_id();
--   ALTER TABLE skus DROP COLUMN vendor_id;  (exige antes reverter a entidade Sku)

ALTER TABLE skus ADD COLUMN vendor_id UUID;

UPDATE skus s SET vendor_id = p.vendor_id FROM products p WHERE p.id = s.product_id;

ALTER TABLE skus ALTER COLUMN vendor_id SET NOT NULL;

ALTER TABLE skus ADD CONSTRAINT fk_skus_vendor_id FOREIGN KEY (vendor_id) REFERENCES vendors (id);

CREATE FUNCTION skus_derive_vendor_id() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    SELECT p.vendor_id INTO NEW.vendor_id FROM products p WHERE p.id = NEW.product_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_skus_derive_vendor_id
    BEFORE INSERT OR UPDATE OF product_id, vendor_id ON skus
    FOR EACH ROW EXECUTE FUNCTION skus_derive_vendor_id();

CREATE FUNCTION skus_propagate_vendor_id() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    UPDATE skus SET vendor_id = NEW.vendor_id WHERE product_id = NEW.id;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_products_propagate_vendor_id
    AFTER UPDATE OF vendor_id ON products
    FOR EACH ROW WHEN (OLD.vendor_id IS DISTINCT FROM NEW.vendor_id)
    EXECUTE FUNCTION skus_propagate_vendor_id();

ALTER TABLE skus ADD CONSTRAINT uq_skus_tenant_vendor_code UNIQUE (tenant_id, vendor_id, sku_code);

ALTER TABLE skus DROP CONSTRAINT uq_skus_tenant_code;
