-- V060: courier operational status, vehicle enum and capacity (LAC-42, W5-B).
--
-- 1) couriers.status (OFFLINE | AVAILABLE | BUSY | SUSPENDED), separate from verification_status.
--    Backfill: an approved courier with available = TRUE -> AVAILABLE; a courier holding an accepted delivery
--    (ACCEPTED .. ARRIVED_AT_CUSTOMER) -> BUSY; everyone else -> OFFLINE. Dispatch only offers to AVAILABLE.
--    couriers.available is KEPT (deprecated) and always equals status IN ('AVAILABLE','BUSY'): the trigger below
--    derives it, and also translates a write that only toggles `available` (pre-V060 application code during a
--    rolling deploy, legacy SQL seeds) into the matching status change.
-- 2) couriers.vehicle_type: free text -> MOTORBIKE | BICYCLE | CAR | ON_FOOT | OTHER (CHECK). Known spellings
--    (pt/en, case-insensitive, by prefix) are mapped; blank -> NULL; anything else -> OTHER.
--    Report BEFORE deploying (what will become OTHER):
--      SELECT vehicle_type, count(*) FROM couriers
--       WHERE vehicle_type IS NOT NULL AND btrim(vehicle_type) <> ''
--         AND vehicle_type !~* '^\s*(mot|scooter|bic|bike|cicl|car|auto|viat|a\s*p[eé]|on[\s_-]*foot|walk|pedestre|p[eé]\s*$)'
--       GROUP BY 1 ORDER BY 2 DESC;
--    After deploying, the rows that were mapped to OTHER: SELECT id FROM couriers WHERE vehicle_type = 'OTHER';
-- 3) couriers.max_active_deliveries, default 1. CHECK = 1 on purpose: the V035 index uq_deliveries_active_courier
--    already allows at most one active delivery per courier, so a larger value would be a lie. Raising it needs a
--    new migration that drops/replaces that index AND DispatchService.COURIER_BUSY_DELIVERY_STATUSES logic.
-- 4) status_changed_at (presence sweeper grace) and suspension_reason (OPS, free text — never logged/audited).
--
-- Locking: ADD COLUMN with constant defaults is metadata-only (PG 11+); the backfill UPDATEs, the CHECKs and the
-- trigger take ACCESS EXCLUSIVE on couriers for the duration of this transaction (small table: one row per courier).
-- Compatibility: additive. Old application code keeps reading/writing `available` (the trigger keeps status in step)
-- and vehicle_type as a String; old code writing an unknown vehicle text fails the CHECK (only POST /couriers writes it).
-- Rollback / forward-fix: the previous application version runs on this schema. To undo the schema:
--   DROP TRIGGER trg_couriers_sync_status ON couriers; DROP FUNCTION couriers_sync_status();
--   ALTER TABLE couriers DROP CONSTRAINT ck_couriers_status, DROP CONSTRAINT ck_couriers_vehicle_type,
--     DROP CONSTRAINT ck_couriers_max_active_deliveries, DROP COLUMN status, DROP COLUMN status_changed_at,
--     DROP COLUMN suspension_reason, DROP COLUMN max_active_deliveries;  (vehicle text mapping is not reversible)

ALTER TABLE couriers
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    ADD COLUMN status_changed_at TIMESTAMPTZ,
    ADD COLUMN suspension_reason VARCHAR(500),
    ADD COLUMN max_active_deliveries SMALLINT NOT NULL DEFAULT 1;

UPDATE couriers
   SET status = CASE WHEN available AND verification_status = 'APPROVED' THEN 'AVAILABLE' ELSE 'OFFLINE' END,
       status_changed_at = updated_at;

UPDATE couriers c
   SET status = 'BUSY'
 WHERE EXISTS (SELECT 1 FROM deliveries d
                WHERE d.courier_id = c.id
                  AND d.status IN ('ACCEPTED', 'ARRIVED_AT_VENDOR', 'PICKED_UP', 'ON_ROUTE_TO_CUSTOMER', 'ARRIVED_AT_CUSTOMER'));

UPDATE couriers SET available = status IN ('AVAILABLE', 'BUSY');

UPDATE couriers
   SET vehicle_type = CASE
        WHEN vehicle_type IS NULL OR btrim(vehicle_type) = '' THEN NULL
        WHEN vehicle_type ~* '^\s*(mot|scooter)' THEN 'MOTORBIKE'
        WHEN vehicle_type ~* '^\s*(bic|bike|cicl)' THEN 'BICYCLE'
        WHEN vehicle_type ~* '^\s*(car|auto|viat)' THEN 'CAR'
        WHEN vehicle_type ~* '^\s*(a\s*p[eé]|on[\s_-]*foot|walk|pedestre|p[eé]\s*$)' THEN 'ON_FOOT'
        ELSE 'OTHER'
       END
 WHERE vehicle_type IS DISTINCT FROM CASE
        WHEN vehicle_type IN ('MOTORBIKE', 'BICYCLE', 'CAR', 'ON_FOOT', 'OTHER') THEN vehicle_type END;

ALTER TABLE couriers
    ADD CONSTRAINT ck_couriers_status CHECK (status IN ('OFFLINE', 'AVAILABLE', 'BUSY', 'SUSPENDED')),
    ADD CONSTRAINT ck_couriers_vehicle_type CHECK (vehicle_type IS NULL OR vehicle_type IN ('MOTORBIKE', 'BICYCLE', 'CAR', 'ON_FOOT', 'OTHER')),
    ADD CONSTRAINT ck_couriers_max_active_deliveries CHECK (max_active_deliveries = 1);

-- Keeps the deprecated `available` flag derived from status, and maps legacy boolean-only writes onto status.
CREATE OR REPLACE FUNCTION couriers_sync_status() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.available AND NEW.status = 'OFFLINE' THEN
            NEW.status := 'AVAILABLE';
        END IF;
        IF NEW.status_changed_at IS NULL THEN
            NEW.status_changed_at := now();
        END IF;
    ELSIF NEW.status IS NOT DISTINCT FROM OLD.status AND NEW.available IS DISTINCT FROM OLD.available THEN
        IF NEW.available AND NEW.status = 'OFFLINE' THEN
            NEW.status := 'AVAILABLE';
            NEW.status_changed_at := now();
        ELSIF NOT NEW.available AND NEW.status = 'AVAILABLE' THEN
            NEW.status := 'OFFLINE';
            NEW.status_changed_at := now();
        END IF;
    END IF;
    NEW.available := NEW.status IN ('AVAILABLE', 'BUSY');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_couriers_sync_status
    BEFORE INSERT OR UPDATE ON couriers
    FOR EACH ROW EXECUTE FUNCTION couriers_sync_status();

-- Dispatch eligibility (tenant, zone, AVAILABLE) and the presence sweeper (status, status_changed_at).
CREATE INDEX idx_couriers_tenant_zone_status ON couriers (tenant_id, operating_zone_id, status);
CREATE INDEX idx_couriers_status_changed ON couriers (status, status_changed_at);

COMMENT ON COLUMN couriers.status IS 'LAC-42: OFFLINE | AVAILABLE | BUSY | SUSPENDED. Dispatch offers only to AVAILABLE.';
COMMENT ON COLUMN couriers.available IS 'Deprecated (V060): derived by trigger = status IN (AVAILABLE, BUSY).';
COMMENT ON COLUMN couriers.max_active_deliveries IS 'LAC-42: fixed at 1 while uq_deliveries_active_courier (V035) exists.';
