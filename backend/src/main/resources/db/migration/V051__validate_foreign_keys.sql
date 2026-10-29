-- LAC-58 (COR-15), passo 2 de 2: valida as 20 FKs criadas NOT VALID em V050.
--
-- Pré-condição: o relatório de órfãos do cabeçalho de V050 devolve 0 linhas. Se houver órfãos, este
--   VALIDATE falha (23503) e a migração inteira é revertida (transação do Flyway): V050 fica aplicada,
--   as FKs continuam a proteger as escritas novas, e corrige-se o dado antes de repetir o migrate.
--   Nunca apagar dados para "passar" o VALIDATE sem aprovação explícita.
-- Locking: VALIDATE CONSTRAINT percorre a tabela filha com SHARE UPDATE EXCLUSIVE (não bloqueia leituras
--   nem escritas; só DDL/VACUUM concorrentes) e ROW SHARE na tabela referida. Os locks somam-se até ao
--   commit da migração, mas nenhum bloqueia o tráfego da aplicação. Custo: um scan por FK.
-- Compatibilidade com a versão anterior da aplicação: total (não muda o que é aceite: V050 já impõe as
--   FKs às escritas novas).
-- Rollback: nada a reverter isoladamente — uma FK validada pode voltar a NOT VALID só por DROP + ADD ...
--   NOT VALID; para remover, ALTER TABLE <t> DROP CONSTRAINT IF EXISTS <fk> (forward-fix numa nova migração).

ALTER TABLE orders VALIDATE CONSTRAINT fk_orders_customer_id;
ALTER TABLE carts VALIDATE CONSTRAINT fk_carts_customer_id;
ALTER TABLE customer_addresses VALIDATE CONSTRAINT fk_customer_addresses_customer_id;
ALTER TABLE promotion_redemption VALIDATE CONSTRAINT fk_promotion_redemption_customer_id;

ALTER TABLE orders VALIDATE CONSTRAINT fk_orders_vendor_id;
ALTER TABLE carts VALIDATE CONSTRAINT fk_carts_vendor_id;
ALTER TABLE products VALIDATE CONSTRAINT fk_products_vendor_id;
ALTER TABLE inventory_items VALIDATE CONSTRAINT fk_inventory_items_vendor_id;
ALTER TABLE commissions VALIDATE CONSTRAINT fk_commissions_vendor_id;
ALTER TABLE coupons VALIDATE CONSTRAINT fk_coupons_vendor_id;
ALTER TABLE promotions VALIDATE CONSTRAINT fk_promotions_vendor_id;
ALTER TABLE promotion VALIDATE CONSTRAINT fk_promotion_vendor_id;

ALTER TABLE orders VALIDATE CONSTRAINT fk_orders_cart_id;
ALTER TABLE promotion VALIDATE CONSTRAINT fk_promotion_target_product_id;
ALTER TABLE promotion VALIDATE CONSTRAINT fk_promotion_target_category_id;
ALTER TABLE vendors VALIDATE CONSTRAINT fk_vendors_category_id;
ALTER TABLE deliveries VALIDATE CONSTRAINT fk_deliveries_courier_id;
ALTER TABLE cash_reconciliations VALIDATE CONSTRAINT fk_cash_reconciliations_courier_id;
ALTER TABLE couriers VALIDATE CONSTRAINT fk_couriers_operating_zone_id;
ALTER TABLE verticals VALIDATE CONSTRAINT fk_verticals_tenant_id;
