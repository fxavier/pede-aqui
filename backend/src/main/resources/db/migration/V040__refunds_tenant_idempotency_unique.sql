-- INC-06 (COR-04): one refund per (tenant_id, idempotency_key).
-- Pre-check (run manually before deploying):
--   SELECT tenant_id, idempotency_key, COUNT(*) FROM refunds
--   GROUP BY tenant_id, idempotency_key HAVING COUNT(*) > 1;
-- Duplicates are never deleted automatically: the migration aborts with a clear message instead,
-- so an operator can decide which row to keep (see the INC-06 report for the cleanup strategy).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM refunds
        GROUP BY tenant_id, idempotency_key
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V040: duplicate (tenant_id, idempotency_key) rows in refunds; resolve them before applying uq_refunds_tenant_idempotency';
    END IF;
END $$;

ALTER TABLE refunds
    ADD CONSTRAINT uq_refunds_tenant_idempotency UNIQUE (tenant_id, idempotency_key);
