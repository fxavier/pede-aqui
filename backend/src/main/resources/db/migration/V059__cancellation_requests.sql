-- LAC-26 (W5-A): pedidos de cancelamento do cliente sujeitos a aprovação de OPS.
--
-- Política (CustomerCancellationPolicy): PAYMENT_PENDING/PAYMENT_CONFIRMED → cancelamento imediato (sem linha aqui);
-- ACCEPTED_BY_VENDOR/PREPARING → o cliente PEDE o cancelamento (uma linha REQUESTED), OPS/ADMIN aprova (encomenda
-- CANCELLED com cancellation_reason CUSTOMER_CANCELLED_WITH_FEE, reembolso = pago − taxa) ou rejeita (a encomenda
-- continua); READY_FOR_PICKUP em diante → 409 order_not_cancellable. Um pedido aberto cuja encomenda avançou
-- (READY_FOR_PICKUP, ou terminou por outra via) é fechado como REJECTED com closure_reason ORDER_ADVANCED /
-- ORDER_CLOSED.
--
-- Colunas: reason = texto livre do cliente (limitado a 500; nunca copiado para o histórico/auditoria);
--   requested_by = perfil do cliente (app_user_profiles.id); decided_by = sub Keycloak de OPS/ADMIN ou o nome do
--   caminho de sistema; fee_rate = taxa em vigor no pedido (app.orders.cancellation.fee-rate);
--   fee_amount = taxa cotada no pedido (subtotal × fee_rate, HALF_UP 2 casas) e, na aprovação, a taxa aplicada
--   (limitada ao que ainda é reembolsável); refund_amount = reembolso pedido na aprovação (NULL até lá).
--
-- Unicidade: um único pedido aberto por encomenda (índice único parcial WHERE status = 'REQUESTED'). Validação de
--   duplicados: não aplicável — a tabela é criada vazia nesta migração.
--
-- Locking: CREATE TABLE nova + índices sobre a tabela vazia. As FKs tomam SHARE ROW EXCLUSIVE sobre tenants, orders
--   e app_user_profiles só durante o CREATE (instantâneo; nada a validar). Não altera tabelas existentes.
-- Compatibilidade: aditiva. A versão anterior da aplicação ignora a tabela. ATENÇÃO: a nova versão escreve dois
--   valores novos em orders.cancellation_reason (CUSTOMER_CANCELLED, CUSTOMER_CANCELLED_WITH_FEE, VARCHAR(40) sem
--   CHECK); a versão anterior não os conhece (EnumType.STRING) e falharia a carregar essas encomendas.
-- Rollback: forward-fix preferido. Se for mesmo preciso voltar à versão anterior da aplicação:
--   UPDATE orders SET cancellation_reason = NULL
--    WHERE cancellation_reason IN ('CUSTOMER_CANCELLED', 'CUSTOMER_CANCELLED_WITH_FEE');
--   (perde-se a distinção do motivo; a tabela pode ficar) e, só se for para remover a funcionalidade,
--   DROP TABLE cancellation_requests; + DELETE FROM flyway_schema_history WHERE version = '059';

CREATE TABLE cancellation_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    order_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    closure_reason VARCHAR(40),
    reason VARCHAR(500),
    requested_by UUID NOT NULL,
    decided_by VARCHAR(255),
    fee_rate NUMERIC(5,4) NOT NULL,
    fee_amount NUMERIC(12,2) NOT NULL,
    refund_amount NUMERIC(12,2),
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    -- A request belongs to its order: deleting the order (retention, test clean-up) removes its requests.
    CONSTRAINT fk_cancellation_requests_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_cancellation_requests_requested_by FOREIGN KEY (requested_by) REFERENCES app_user_profiles(id),
    CONSTRAINT ck_cancellation_requests_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_cancellation_requests_decision CHECK ((status = 'REQUESTED') = (decided_at IS NULL)),
    CONSTRAINT ck_cancellation_requests_fee_rate CHECK (fee_rate >= 0 AND fee_rate <= 1),
    CONSTRAINT ck_cancellation_requests_fee_amount CHECK (fee_amount >= 0),
    CONSTRAINT ck_cancellation_requests_refund_amount CHECK (refund_amount IS NULL OR refund_amount >= 0)
);

-- One open request per order (concurrent duplicates also serialize on the payment row lock; this is the backstop).
CREATE UNIQUE INDEX uq_cancellation_requests_open_per_order ON cancellation_requests (order_id) WHERE status = 'REQUESTED';
-- Latest request of an order (customer order detail, idempotent repeat).
CREATE INDEX idx_cancellation_requests_order ON cancellation_requests (order_id, requested_at DESC);
-- OPS queue: GET /ops/cancellation-requests?status=&page=&size= (newest first).
CREATE INDEX idx_cancellation_requests_tenant_status ON cancellation_requests (tenant_id, status, requested_at DESC, id DESC);
