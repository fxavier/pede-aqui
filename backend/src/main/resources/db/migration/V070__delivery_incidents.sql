-- V070: entregas falhadas e incidentes com fluxo de compensação (LAC-44, W7-B).
--
-- 1) delivery_incidents: um incidente por entrega que terminou em FAILED_DELIVERY (UNIQUE delivery_id). Aberto (OPEN)
--    pelo DeliveryService/OrderDeliveryHooks na mesma transação da falha, com o motivo codificado, quem reportou
--    (sub do JWT + papel), o estado de onde falhou, a foto de prova opcional (storage key já validada) e a evidência
--    de chegada (última posição do estafeta + distância em linha reta à morada da encomenda). O resultado por defeito
--    (default_cost_bearer/default_outcome/default_goods_disposition) vem da política configurável
--    (app.delivery-incidents.policy). OPS/ADMIN resolvem uma vez (RESOLVED): decisão final (cost_bearer,
--    final_outcome, goods_disposition), override com motivo e os montantes lançados no razão. `note` e
--    `override_reason` são texto livre: ficam só aqui (nunca em logs nem em audit_logs).
--    FKs para orders/deliveries com ON DELETE CASCADE (como cancellation_requests): o incidente segue o ciclo de vida
--    da encomenda; o registo financeiro imutável é o razão (sem FKs) e o audit_logs.
-- 2) Razão (V065): nova conta PLATFORM_INCIDENT_EXPENSE e novo tipo de lançamento INCIDENT_SETTLED nos CHECKs
--    (modelo "a validar" com o contabilista, ADR-0005 / LAC-38).
-- 3) Modelos de notificação por defeito (pt-PT, só {orderReference}): delivery_failed (incidente aberto),
--    delivery_incident_refund_full / delivery_incident_refund_partial (resolução com reembolso).
--
-- Validação prévia (nenhum UNIQUE sobre dados existentes: a tabela é nova). Os CHECKs do razão só alargam o conjunto
-- de valores permitidos, por isso nenhuma linha existente os viola:
--   SELECT account_type, COUNT(*) FROM ledger_accounts GROUP BY 1;
--   SELECT transaction_type, COUNT(*) FROM ledger_transactions GROUP BY 1;
--
-- Locking: CREATE TABLE/INDEX numa tabela nova; as FKs tomam SHARE ROW EXCLUSIVE em orders/deliveries só durante o
--   CREATE (tabela vazia, nada a validar). Os ALTER TABLE dos CHECKs do razão tomam ACCESS EXCLUSIVE em
--   ledger_accounts/ledger_transactions durante a revalidação (varrimento completo; tabelas pequenas — append-only
--   mas de volume baixo). Os triggers append-only de V065 só atuam em DML e não bloqueiam o ALTER.
-- Compatibilidade com a versão anterior da aplicação: total. A versão anterior ignora a tabela nova, nunca escreve
--   os novos valores de conta/lançamento e os modelos novos não são usados por ela. Uma encomenda cancelada com
--   cancellation_reason = 'DELIVERY_FAILED' (coluna sem CHECK, V044) falharia a carregar na versão anterior
--   (EnumType.STRING) — fazer rollback da aplicação só antes de existirem tais linhas, ou corrigi-las primeiro:
--   UPDATE orders SET cancellation_reason = NULL WHERE cancellation_reason = 'DELIVERY_FAILED';
-- Rollback / forward-fix (nova migração; nunca editar esta):
--   DROP TABLE IF EXISTS delivery_incidents;
--   DELETE FROM notification_templates WHERE tenant_id IS NULL
--     AND template_key IN ('delivery_failed', 'delivery_incident_refund_full', 'delivery_incident_refund_partial');
--   Os CHECKs do razão só podem voltar ao conjunto de V065 se não houver linhas INCIDENT_SETTLED /
--   PLATFORM_INCIDENT_EXPENSE (o razão é append-only — com dinheiro real, deixar os CHECKs alargados).

CREATE TABLE delivery_incidents (
    id                           UUID PRIMARY KEY,
    tenant_id                    UUID NOT NULL,
    order_id                     UUID NOT NULL,
    delivery_id                  UUID NOT NULL,
    courier_id                   UUID,
    reason                       VARCHAR(30) NOT NULL,
    note                         VARCHAR(500),
    proof_photo_storage_key      VARCHAR(512),
    reported_by                  VARCHAR(255) NOT NULL,
    reported_by_role             VARCHAR(20) NOT NULL,
    failed_from_status           VARCHAR(40) NOT NULL,
    evidence_latitude            DOUBLE PRECISION,
    evidence_longitude           DOUBLE PRECISION,
    evidence_accuracy_m          DOUBLE PRECISION,
    evidence_recorded_at         TIMESTAMPTZ,
    evidence_distance_m          DOUBLE PRECISION,
    status                       VARCHAR(20) NOT NULL,
    default_cost_bearer          VARCHAR(20) NOT NULL,
    default_outcome              VARCHAR(30) NOT NULL,
    default_goods_disposition    VARCHAR(20) NOT NULL,
    cost_bearer                  VARCHAR(20),
    final_outcome                VARCHAR(30),
    goods_disposition            VARCHAR(20),
    overridden                   BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason              VARCHAR(500),
    refund_amount                NUMERIC(14, 2),
    retained_fee_amount          NUMERIC(14, 2),
    merchant_charge_amount       NUMERIC(14, 2),
    platform_compensation_amount NUMERIC(14, 2),
    decided_by                   VARCHAR(255),
    created_at                   TIMESTAMPTZ NOT NULL,
    resolved_at                  TIMESTAMPTZ,
    updated_at                   TIMESTAMPTZ NOT NULL,
    version                      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_delivery_incidents_delivery UNIQUE (delivery_id),
    CONSTRAINT fk_delivery_incidents_order_id FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_incidents_delivery_id FOREIGN KEY (delivery_id) REFERENCES deliveries (id) ON DELETE CASCADE,
    CONSTRAINT ck_delivery_incidents_reason CHECK (reason IN ('CUSTOMER_ABSENT', 'WRONG_ADDRESS', 'CUSTOMER_REFUSED',
        'STORE_FAULT', 'DAMAGED_IN_TRANSIT', 'ACCIDENT', 'OTHER')),
    CONSTRAINT ck_delivery_incidents_reported_by_role CHECK (reported_by_role IN ('COURIER', 'OPS', 'ADMIN')),
    CONSTRAINT ck_delivery_incidents_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_delivery_incidents_cost_bearer CHECK (default_cost_bearer IN ('CUSTOMER', 'MERCHANT', 'PLATFORM')
        AND (cost_bearer IS NULL OR cost_bearer IN ('CUSTOMER', 'MERCHANT', 'PLATFORM'))),
    CONSTRAINT ck_delivery_incidents_outcome CHECK (default_outcome IN ('REFUND_FULL', 'REFUND_MINUS_DELIVERY_FEE')
        AND (final_outcome IS NULL OR final_outcome IN ('REFUND_FULL', 'REFUND_MINUS_DELIVERY_FEE'))),
    CONSTRAINT ck_delivery_incidents_goods CHECK (default_goods_disposition IN ('RETURN_TO_STORE', 'DISCARDED')
        AND (goods_disposition IS NULL OR goods_disposition IN ('RETURN_TO_STORE', 'DISCARDED'))),
    -- OPEN ⇔ no decision yet; RESOLVED ⇔ decision, amounts and decider recorded.
    CONSTRAINT ck_delivery_incidents_resolution CHECK (
        (status = 'OPEN' AND resolved_at IS NULL AND cost_bearer IS NULL AND final_outcome IS NULL AND goods_disposition IS NULL)
        OR (status = 'RESOLVED' AND resolved_at IS NOT NULL AND cost_bearer IS NOT NULL AND final_outcome IS NOT NULL
            AND goods_disposition IS NOT NULL AND refund_amount IS NOT NULL AND retained_fee_amount IS NOT NULL
            AND merchant_charge_amount IS NOT NULL AND platform_compensation_amount IS NOT NULL AND decided_by IS NOT NULL)),
    CONSTRAINT ck_delivery_incidents_override CHECK (NOT overridden OR override_reason IS NOT NULL),
    CONSTRAINT ck_delivery_incidents_amounts CHECK (
        (refund_amount IS NULL OR refund_amount >= 0) AND (retained_fee_amount IS NULL OR retained_fee_amount >= 0)
        AND (merchant_charge_amount IS NULL OR merchant_charge_amount >= 0)
        AND (platform_compensation_amount IS NULL OR platform_compensation_amount >= 0))
);

-- OPS queue (GET /ops/delivery-incidents?status=): newest first per tenant and status.
CREATE INDEX idx_delivery_incidents_tenant_status_created ON delivery_incidents (tenant_id, status, created_at DESC, id DESC);
CREATE INDEX idx_delivery_incidents_order ON delivery_incidents (order_id);

ALTER TABLE ledger_accounts DROP CONSTRAINT ledger_accounts_account_type_check;
ALTER TABLE ledger_accounts ADD CONSTRAINT ledger_accounts_account_type_check CHECK (account_type IN (
    'PLATFORM_CASH_CLEARING', 'CUSTOMER_FUNDS_HELD', 'CUSTOMER_REFUNDS_PAYABLE', 'MERCHANT_PAYABLE',
    'COURIER_PAYABLE', 'PLATFORM_REVENUE_COMMISSION', 'PLATFORM_REVENUE_FEES', 'PLATFORM_PROMOTION_EXPENSE',
    'PLATFORM_VAT_PAYABLE', 'PLATFORM_INCIDENT_EXPENSE'));

ALTER TABLE ledger_transactions DROP CONSTRAINT ledger_transactions_transaction_type_check;
ALTER TABLE ledger_transactions ADD CONSTRAINT ledger_transactions_transaction_type_check CHECK (transaction_type IN (
    'PAYMENT_CAPTURED', 'ORDER_DELIVERED', 'REFUND_BEFORE_DELIVERY', 'REFUND_AFTER_DELIVERY',
    'CANCELLATION_FEE_RETAINED', 'INCIDENT_SETTLED'));

-- Platform default templates (pt-PT). Only {orderReference}: no PII, no amounts, no free text. These keys have no
-- other inbox message, so IN_APP is active (unlike the W6-C status keys).
INSERT INTO notification_templates (id, tenant_id, template_key, channel, locale, title, body, active) VALUES
    (gen_random_uuid(), NULL, 'delivery_failed', 'PUSH', 'pt-PT', 'Entrega não concluída',
     'Não foi possível entregar o seu pedido {orderReference}. A nossa equipa vai analisar a ocorrência e informá-lo da solução.', TRUE),
    (gen_random_uuid(), NULL, 'delivery_failed', 'IN_APP', 'pt-PT', 'Entrega não concluída',
     'Não foi possível entregar o seu pedido {orderReference}. A nossa equipa vai analisar a ocorrência e informá-lo da solução.', TRUE),
    (gen_random_uuid(), NULL, 'delivery_incident_refund_full', 'PUSH', 'pt-PT', 'Reembolso aprovado',
     'Analisámos a entrega do pedido {orderReference}: o valor pago será devolvido na totalidade.', TRUE),
    (gen_random_uuid(), NULL, 'delivery_incident_refund_full', 'IN_APP', 'pt-PT', 'Reembolso aprovado',
     'Analisámos a entrega do pedido {orderReference}: o valor pago será devolvido na totalidade.', TRUE),
    (gen_random_uuid(), NULL, 'delivery_incident_refund_partial', 'PUSH', 'pt-PT', 'Reembolso aprovado',
     'Analisámos a entrega do pedido {orderReference}: os artigos serão reembolsados; a taxa de entrega não é devolvida.', TRUE),
    (gen_random_uuid(), NULL, 'delivery_incident_refund_partial', 'IN_APP', 'pt-PT', 'Reembolso aprovado',
     'Analisámos a entrega do pedido {orderReference}: os artigos serão reembolsados; a taxa de entrega não é devolvida.', TRUE);
