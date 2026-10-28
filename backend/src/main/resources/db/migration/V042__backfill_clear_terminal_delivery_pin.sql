-- INC-02 follow-up (AS-08): data fix. Clears the plain delivery PIN (orders.delivery_confirmation_code_display)
-- on legacy orders that are already in a terminal state. Since INC-02 the application clears it on
-- Order.markDelivered() / Order.markCancelled() (failed delivery cancels the order too), but V037
-- deliberately left existing rows untouched, so pre-INC-02 terminal orders still store the PIN.
--
-- Exact set (derived from the code, not guessed):
--   * DELIVERED, CANCELLED  -> always cleared, exactly what markDelivered()/markCancelled() do today.
--   * REJECTED_BY_VENDOR, REFUNDED -> terminal: no code path transitions an order out of them, and
--     dispatch only offers READY_FOR_PICKUP orders. The PIN is cleared only when the order's delivery
--     cannot be completed any more, i.e. the delivery row (one per order, uq_deliveries_tenant_order)
--     is absent or NOT in a courier-engaged status. Reason: DeliveryService.complete() checks only
--     the delivery status, so a full refund approved while a courier is already engaged/en route could
--     still be completed with the PIN; such rows are left alone.
--   * Everything else (incl. REFUND_PENDING, which no code path sets, and all active states) is NOT touched.
--
-- Validation query (run before; the count is what this migration will clear):
--   SELECT o.status, count(*) FROM orders o
--     LEFT JOIN deliveries d ON d.tenant_id = o.tenant_id AND d.order_id = o.id
--    WHERE o.delivery_confirmation_code_display IS NOT NULL
--      AND (o.status IN ('DELIVERED','CANCELLED')
--           OR (o.status IN ('REJECTED_BY_VENDOR','REFUNDED')
--               AND (d.status IS NULL OR d.status NOT IN ('ASSIGNED','ACCEPTED','ARRIVED_AT_VENDOR','REASSIGNED',
--                                                         'PICKED_UP','ON_ROUTE_TO_CUSTOMER','ARRIVED_AT_CUSTOMER'))))
--    GROUP BY o.status;
--
-- Locking: one UPDATE takes row locks on the matched orders rows only (no table rewrite, no DDL);
--   the orders table is small, so the lock window is short. Reads are never blocked.
-- Compatibility: the previous application version (post-INC-02) already treats a NULL display as
--   "no PIN to show" (V037 made the column nullable). version is bumped so a stale in-flight entity
--   from an instance still running fails optimistically instead of writing the PIN back.
--   updated_at is intentionally NOT touched (historic order timelines/reports stay unchanged).
-- Rollback / forward-fix: irreversible by design (the plain PIN must not be kept; the hash in
--   delivery_confirmation_code_hash, which is what verification uses, is untouched). No rollback is
--   needed: no behaviour depends on the plain PIN of a terminal order. Re-running is a no-op.
UPDATE orders o
   SET delivery_confirmation_code_display = NULL,
       version = o.version + 1
 WHERE o.delivery_confirmation_code_display IS NOT NULL
   AND (
        o.status IN ('DELIVERED', 'CANCELLED')
        OR (o.status IN ('REJECTED_BY_VENDOR', 'REFUNDED')
            AND NOT EXISTS (
                SELECT 1 FROM deliveries d
                 WHERE d.tenant_id = o.tenant_id
                   AND d.order_id = o.id
                   AND d.status IN ('ASSIGNED', 'ACCEPTED', 'ARRIVED_AT_VENDOR', 'REASSIGNED',
                                    'PICKED_UP', 'ON_ROUTE_TO_CUSTOMER', 'ARRIVED_AT_CUSTOMER')))
       );
