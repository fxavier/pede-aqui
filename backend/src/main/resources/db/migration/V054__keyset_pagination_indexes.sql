-- LAC-14 (SEC-11, COR-09): índices para as ordenações de paginação.
--
-- Todas as listas passam a ordenar por (created_at DESC, id DESC) dentro do tenant (common/pagination/PageLimits):
--   * keyset (cursor) em audit_logs, notifications e orders: WHERE tenant_id = ? [AND ...]
--       AND (created_at < ? OR (created_at = ? AND id < ?)) ORDER BY created_at DESC, id DESC LIMIT n+1;
--   * offset (Pageable) e listas legadas limitadas a 200 em couriers, vendors, app_user_profiles e
--     support_tickets: WHERE tenant_id = ? ORDER BY created_at DESC, id DESC LIMIT/OFFSET.
-- Cada índice cobre exatamente o filtro de igualdade + a ordenação, para o plano ser um index scan sem sort.
--
-- Índices substituídos (prefixo estrito do novo índice, logo redundantes):
--   idx_audit_logs_tenant_created (V010)               -> idx_audit_logs_tenant_created_id
--   idx_notifications_tenant_recipient_created (V009)  -> idx_notifications_tenant_recipient_created_id
-- Os índices de orders de V032 (ix_orders_tenant_created, ix_orders_tenant_vendor_created) ficam: são usados
-- pelos relatórios por intervalo de datas e não pertencem a este pacote.
--
-- Validação prévia: não há UNIQUE nem índices parciais novos, por isso não existe risco de duplicados.
-- Locking: CREATE INDEX (sem CONCURRENTLY, porque o Flyway corre cada migração numa transação — mesmo critério
--   de V046/V053) toma SHARE na tabela: leituras continuam, escritas na tabela esperam até ao fim da construção.
--   Com os volumes atuais (MVP) é sub-segundo. Em produção com tabelas grandes: criar antes, fora do Flyway,
--   com CREATE INDEX CONCURRENTLY IF NOT EXISTS <mesmo nome>; o IF NOT EXISTS desta migração passa a no-op.
--   DROP INDEX toma ACCESS EXCLUSIVE breve (só catálogo).
-- Compatibilidade com a versão anterior da aplicação: total (só índices; nenhuma query perde cobertura — os
--   índices removidos são prefixos dos novos).
-- Rollback / forward-fix (nova migração): DROP INDEX IF EXISTS de cada índice criado abaixo e, se necessário,
--   recriar os dois índices removidos com a definição original de V009/V010.

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_created_id
    ON audit_logs (tenant_id, created_at DESC, id DESC);
DROP INDEX IF EXISTS idx_audit_logs_tenant_created;

CREATE INDEX IF NOT EXISTS idx_notifications_tenant_recipient_created_id
    ON notifications (tenant_id, recipient_user_id, created_at DESC, id DESC);
DROP INDEX IF EXISTS idx_notifications_tenant_recipient_created;

CREATE INDEX IF NOT EXISTS idx_orders_tenant_created_id
    ON orders (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_vendor_created_id
    ON orders (tenant_id, vendor_id, created_at DESC, id DESC);
-- Vista transversal do super-admin de plataforma (GET /orders e /orders/cursor sem tenant).
CREATE INDEX IF NOT EXISTS idx_orders_created_id
    ON orders (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_couriers_tenant_created_id
    ON couriers (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_vendors_tenant_created_id
    ON vendors (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_app_user_profiles_tenant_created_id
    ON app_user_profiles (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_tenant_created_id
    ON support_tickets (tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_tenant_creator_created_id
    ON support_tickets (tenant_id, creator_user_id, created_at DESC, id DESC);
