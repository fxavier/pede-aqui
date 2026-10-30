-- LAC-58 (COR-15), passo 1 de 2: integridade referencial em falta. Acrescenta 20 FOREIGN KEYs como
-- NOT VALID (as linhas existentes não são verificadas; toda a escrita nova passa a ser) e os índices de
-- suporte em falta. V051 valida-as (VALIDATE CONSTRAINT).
--
-- Inventário (colunas UUID de V001–V049 que referem outra tabela e não tinham FK):
--   orders.customer_id, carts.customer_id, customer_addresses.customer_id,
--   promotion_redemption.customer_id                       → app_user_profiles(id)  (id do perfil, não o sub Keycloak)
--   orders.vendor_id, carts.vendor_id, products.vendor_id, inventory_items.vendor_id, commissions.vendor_id,
--   coupons.vendor_id, promotions.vendor_id, promotion.vendor_id → vendors(id)
--   orders.cart_id → carts(id); promotion.target_product_id → products(id)
--   promotion.target_category_id, vendors.category_id → categories(id)
--   deliveries.courier_id, cash_reconciliations.courier_id → couriers(id); couriers.operating_zone_id → zones(id)
--   verticals.tenant_id → tenants(id)
-- Já tinham FK (nada a fazer): deliveries.order_id, payments.order_id, refunds.payment_id/order_id,
--   order_items.order_id/sku_id, inventory_items.sku_id, cart_items.*, products.category_id, ...
-- Deliberadamente SEM FK (documentado):
--   orders.delivery_address_id   — a morada é um snapshot (V047); o cliente pode apagar a morada original.
--   orders.quote_fee_policy_id / quote_delivery_fee_rule_id — registo histórico da quote (id + versão, V048);
--                                  as regras de taxa são substituídas/apagadas (deleteByTenantIdAndVendorId).
--   customer_favorite_vendors.customer_id — a aplicação grava o sub Keycloak do JWT, não o id do perfil.
--   ratings.rater_user_id        — sem escritor na aplicação; semântica (perfil vs sub Keycloak) por decidir.
--   audit_logs.actor_user_id/target_id, support_tickets.*_user_id — VARCHAR (sub Keycloak / alvo polimórfico).
-- Todas as FKs ficam ON DELETE NO ACTION: nenhum destes pais é apagado pela aplicação.
--
-- Locking: ADD CONSTRAINT ... FOREIGN KEY ... NOT VALID não percorre a tabela; pede SHARE ROW EXCLUSIVE
--   (breve, bloqueia escritas, não leituras) na tabela filha e na referida, mantido até ao commit da
--   migração. CREATE INDEX (sem CONCURRENTLY: o Flyway corre a migração numa transação, cf. V046) pede
--   SHARE na tabela durante a construção; as tabelas afetadas são pequenas. Em produção com tabelas
--   grandes, criar os índices antes, fora do Flyway, com CREATE INDEX CONCURRENTLY IF NOT EXISTS
--   (os nomes coincidem, os IF NOT EXISTS daqui tornam-se no-ops).
-- Compatibilidade com a versão anterior da aplicação: total — ela já só grava referências existentes
--   (ids resolvidos de linhas lidas na mesma transação). Um pedido com id inexistente (ex.: vendorId
--   arbitrário em POST /cart/items ou categoryId em PUT /vendors) passa a falhar com 409 (handler de
--   DataIntegrityViolationException) em vez de gravar uma referência pendurada.
-- Rollback: ALTER TABLE <t> DROP CONSTRAINT IF EXISTS <fk>; DROP INDEX IF EXISTS <idx>; (forward-fix
--   preferido: uma nova migração que remova só a FK problemática).
--
-- Relatório de órfãos (correr ANTES de V051; esperado: 0 linhas). Cada linha = uma FK com órfãos.
-- Um órfão impede o VALIDATE de V051: corrigir o dado (repor o pai ou anular a referência opcional),
-- nunca apagar dados sem aprovação. ForeignKeyIntegrityMigrationTest executa exatamente este bloco.
-- ORPHAN-REPORT-BEGIN
-- SELECT fk, orphans FROM (
--   SELECT 'fk_orders_customer_id' AS fk, COUNT(*) AS orphans FROM orders c WHERE c.customer_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM app_user_profiles p WHERE p.id = c.customer_id)
--   UNION ALL SELECT 'fk_carts_customer_id', COUNT(*) FROM carts c WHERE c.customer_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM app_user_profiles p WHERE p.id = c.customer_id)
--   UNION ALL SELECT 'fk_customer_addresses_customer_id', COUNT(*) FROM customer_addresses c WHERE c.customer_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM app_user_profiles p WHERE p.id = c.customer_id)
--   UNION ALL SELECT 'fk_promotion_redemption_customer_id', COUNT(*) FROM promotion_redemption c WHERE c.customer_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM app_user_profiles p WHERE p.id = c.customer_id)
--   UNION ALL SELECT 'fk_orders_vendor_id', COUNT(*) FROM orders c WHERE c.vendor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM vendors p WHERE p.id = c.vendor_id)
--   UNION ALL SELECT 'fk_carts_vendor_id', COUNT(*) FROM carts c WHERE c.vendor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM vendors p WHERE p.id = c.vendor_id)
--   UNION ALL SELECT 'fk_products_vendor_id', COUNT(*) FROM products c WHERE c.vendor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM vendors p WHERE p.id = c.vendor_id)
--   UNION ALL SELECT 'fk_inventory_items_vendor_id', COUNT(*) FROM inventory_items c WHERE c.vendor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM vendors p WHERE p.id = c.vendor_id)
--   UNION ALL SELECT 'fk_commissions_vendor_id', COUNT(*) FROM commissions c WHERE c.vendor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM vendors p WHERE p.id = c.vendor_id)
--   UNION ALL SELECT 'fk_coupons_vendor_id', COUNT(*) FROM coupons c WHERE c.vendor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM vendors p WHERE p.id = c.vendor_id)
--   UNION ALL SELECT 'fk_promotions_vendor_id', COUNT(*) FROM promotions c WHERE c.vendor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM vendors p WHERE p.id = c.vendor_id)
--   UNION ALL SELECT 'fk_promotion_vendor_id', COUNT(*) FROM promotion c WHERE c.vendor_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM vendors p WHERE p.id = c.vendor_id)
--   UNION ALL SELECT 'fk_orders_cart_id', COUNT(*) FROM orders c WHERE c.cart_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM carts p WHERE p.id = c.cart_id)
--   UNION ALL SELECT 'fk_promotion_target_product_id', COUNT(*) FROM promotion c WHERE c.target_product_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM products p WHERE p.id = c.target_product_id)
--   UNION ALL SELECT 'fk_promotion_target_category_id', COUNT(*) FROM promotion c WHERE c.target_category_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM categories p WHERE p.id = c.target_category_id)
--   UNION ALL SELECT 'fk_vendors_category_id', COUNT(*) FROM vendors c WHERE c.category_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM categories p WHERE p.id = c.category_id)
--   UNION ALL SELECT 'fk_deliveries_courier_id', COUNT(*) FROM deliveries c WHERE c.courier_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM couriers p WHERE p.id = c.courier_id)
--   UNION ALL SELECT 'fk_cash_reconciliations_courier_id', COUNT(*) FROM cash_reconciliations c WHERE c.courier_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM couriers p WHERE p.id = c.courier_id)
--   UNION ALL SELECT 'fk_couriers_operating_zone_id', COUNT(*) FROM couriers c WHERE c.operating_zone_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM zones p WHERE p.id = c.operating_zone_id)
--   UNION ALL SELECT 'fk_verticals_tenant_id', COUNT(*) FROM verticals c WHERE c.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants p WHERE p.id = c.tenant_id)
-- ) r WHERE orphans > 0 ORDER BY fk
-- ORPHAN-REPORT-END
-- Para listar os ids órfãos de uma FK: substituir COUNT(*) por c.id no SELECT correspondente.

-- Perfis de cliente (app_user_profiles.id).
ALTER TABLE orders ADD CONSTRAINT fk_orders_customer_id
    FOREIGN KEY (customer_id) REFERENCES app_user_profiles (id) NOT VALID;
ALTER TABLE carts ADD CONSTRAINT fk_carts_customer_id
    FOREIGN KEY (customer_id) REFERENCES app_user_profiles (id) NOT VALID;
ALTER TABLE customer_addresses ADD CONSTRAINT fk_customer_addresses_customer_id
    FOREIGN KEY (customer_id) REFERENCES app_user_profiles (id) NOT VALID;
ALTER TABLE promotion_redemption ADD CONSTRAINT fk_promotion_redemption_customer_id
    FOREIGN KEY (customer_id) REFERENCES app_user_profiles (id) NOT VALID;

-- Lojas.
ALTER TABLE orders ADD CONSTRAINT fk_orders_vendor_id
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) NOT VALID;
ALTER TABLE carts ADD CONSTRAINT fk_carts_vendor_id
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) NOT VALID;
ALTER TABLE products ADD CONSTRAINT fk_products_vendor_id
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) NOT VALID;
ALTER TABLE inventory_items ADD CONSTRAINT fk_inventory_items_vendor_id
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) NOT VALID;
ALTER TABLE commissions ADD CONSTRAINT fk_commissions_vendor_id
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) NOT VALID;
ALTER TABLE coupons ADD CONSTRAINT fk_coupons_vendor_id
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) NOT VALID;
ALTER TABLE promotions ADD CONSTRAINT fk_promotions_vendor_id
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) NOT VALID;
ALTER TABLE promotion ADD CONSTRAINT fk_promotion_vendor_id
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) NOT VALID;

-- Catálogo, carrinho, entregas, zonas, verticais.
ALTER TABLE orders ADD CONSTRAINT fk_orders_cart_id
    FOREIGN KEY (cart_id) REFERENCES carts (id) NOT VALID;
ALTER TABLE promotion ADD CONSTRAINT fk_promotion_target_product_id
    FOREIGN KEY (target_product_id) REFERENCES products (id) NOT VALID;
ALTER TABLE promotion ADD CONSTRAINT fk_promotion_target_category_id
    FOREIGN KEY (target_category_id) REFERENCES categories (id) NOT VALID;
ALTER TABLE vendors ADD CONSTRAINT fk_vendors_category_id
    FOREIGN KEY (category_id) REFERENCES categories (id) NOT VALID;
ALTER TABLE deliveries ADD CONSTRAINT fk_deliveries_courier_id
    FOREIGN KEY (courier_id) REFERENCES couriers (id) NOT VALID;
ALTER TABLE cash_reconciliations ADD CONSTRAINT fk_cash_reconciliations_courier_id
    FOREIGN KEY (courier_id) REFERENCES couriers (id) NOT VALID;
ALTER TABLE couriers ADD CONSTRAINT fk_couriers_operating_zone_id
    FOREIGN KEY (operating_zone_id) REFERENCES zones (id) NOT VALID;
ALTER TABLE verticals ADD CONSTRAINT fk_verticals_tenant_id
    FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;

-- Índices de suporte: só onde a coluna não é a primeira de nenhum índice nem está coberta por um índice
-- (tenant_id, coluna) — a aplicação filtra sempre por tenant e nenhum destes pais é apagado, por isso
-- orders.customer_id/vendor_id, carts.customer_id, customer_addresses.customer_id, products.vendor_id,
-- inventory_items.vendor_id, promotion.vendor_id e vendors.category_id não ganham índice novo.
-- Colunas opcionais: índice parcial (WHERE ... IS NOT NULL).
CREATE INDEX IF NOT EXISTS idx_orders_cart_id ON orders (cart_id) WHERE cart_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_carts_vendor_id ON carts (vendor_id);
CREATE INDEX IF NOT EXISTS idx_commissions_vendor_id ON commissions (vendor_id);
CREATE INDEX IF NOT EXISTS idx_coupons_vendor_id ON coupons (vendor_id) WHERE vendor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_promotions_vendor_id ON promotions (vendor_id) WHERE vendor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_promotion_target_product_id ON promotion (target_product_id) WHERE target_product_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_promotion_target_category_id ON promotion (target_category_id) WHERE target_category_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_promotion_redemption_customer_id ON promotion_redemption (customer_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_courier_id ON deliveries (courier_id) WHERE courier_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_cash_reconciliations_courier_id ON cash_reconciliations (courier_id) WHERE courier_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_couriers_operating_zone_id ON couriers (operating_zone_id) WHERE operating_zone_id IS NOT NULL;
