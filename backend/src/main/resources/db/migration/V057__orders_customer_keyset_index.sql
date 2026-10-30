-- W3-C / LAC-21: índice da lista de encomendas do próprio cliente (GET /api/v1/me/orders).
--
-- Query servida (CustomerOrderQueryRepository.findCustomerKeysetPage, convenção keyset LAC-14):
--   WHERE tenant_id = ? AND customer_id = ?
--     AND (created_at < ? OR (created_at = ? AND id < ?)) ORDER BY created_at DESC, id DESC LIMIT n+1;
-- O índice cobre o filtro de igualdade + a ordenação, para o plano ser um index scan sem sort.
--
-- Índice substituído (prefixo estrito do novo, logo redundante):
--   idx_orders_tenant_customer (V003) -> idx_orders_tenant_customer_created_id
--   Nenhum código, teste ou script referencia idx_orders_tenant_customer pelo nome.
--
-- Numeração: V056 está reservada para o pacote paralelo W3-D; o Flyway aceita a lacuna.
-- Validação prévia: não há UNIQUE nem índice parcial novo, por isso não existe risco de duplicados.
-- Locking: CREATE INDEX (sem CONCURRENTLY, porque o Flyway corre cada migração numa transação — mesmo critério
--   de V046/V053/V054) toma SHARE em orders: leituras continuam, escritas (checkout, transições) esperam até ao
--   fim da construção. Com os volumes atuais (MVP) é sub-segundo. Em produção com a tabela grande: criar antes,
--   fora do Flyway, com
--     CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_tenant_customer_created_id
--         ON orders (tenant_id, customer_id, created_at DESC, id DESC);
--   e o IF NOT EXISTS desta migração passa a no-op. DROP INDEX toma ACCESS EXCLUSIVE breve (só catálogo).
-- Compatibilidade com a versão anterior da aplicação: total (só índices; as queries por (tenant_id, customer_id)
--   continuam cobertas pelo prefixo do novo índice).
-- Rollback / forward-fix (nova migração): DROP INDEX IF EXISTS idx_orders_tenant_customer_created_id; e recriar
--   CREATE INDEX IF NOT EXISTS idx_orders_tenant_customer ON orders (tenant_id, customer_id);

CREATE INDEX IF NOT EXISTS idx_orders_tenant_customer_created_id
    ON orders (tenant_id, customer_id, created_at DESC, id DESC);
DROP INDEX IF EXISTS idx_orders_tenant_customer;
