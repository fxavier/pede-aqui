-- V035: Dispatch exclusivity (INC-03, COR-02).
-- 1) At most one active dispatch job per delivery. "Active" = ASSIGNED | ACCEPTED | REASSIGNABLE
--    (a REASSIGNABLE job is still the open job of its delivery: reassign() reuses the same row).
--    Must match DispatchService.ACTIVE_JOB_STATUSES.
-- 2) MVP rule: at most one active delivery per courier. "Active" = the courier-held statuses
--    ASSIGNED .. ARRIVED_AT_CUSTOMER. Must match DispatchService.COURIER_BUSY_DELIVERY_STATUSES.
--
-- Plain (non-CONCURRENT) index builds: Flyway runs each migration in a transaction and both tables
-- are small; the build holds a SHARE lock that blocks writes to the table for its duration only.
-- Fails (and rolls back) if existing rows already violate a rule; see the INC-03 report for the
-- duplicate-detection queries to run before deploying.

CREATE UNIQUE INDEX uq_dispatch_jobs_active_delivery
    ON dispatch_jobs (delivery_id)
    WHERE status IN ('ASSIGNED', 'ACCEPTED', 'REASSIGNABLE');

CREATE UNIQUE INDEX uq_deliveries_active_courier
    ON deliveries (courier_id)
    WHERE courier_id IS NOT NULL
      AND status IN ('ASSIGNED', 'ACCEPTED', 'ARRIVED_AT_VENDOR', 'PICKED_UP', 'ON_ROUTE_TO_CUSTOMER', 'ARRIVED_AT_CUSTOMER');
