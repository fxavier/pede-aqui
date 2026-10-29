-- V061: courier GPS consent, latest positions and route history (LAC-40, W5-B).
--
-- 1) couriers.location_consent_accepted_at / location_consent_version: explicit consent given by the courier at
--    onboarding (PUT /couriers/me/location-consent). No location is accepted without consent to the CURRENT text
--    version (app.courier-location.consent-version).
-- 2) courier_last_locations: one row per courier (upsert, newest recorded_at wins), the database source of truth for
--    the latest position (OPS map, future dispatch ranking) and for presence (received_at, presence sweeper).
--    The row is deleted when the courier goes off duty (OFFLINE / SUSPENDED / consent withdrawn).
-- 3) courier_location_history: route audit, written only while the courier works a delivery (data minimisation:
--    idle on-duty points update the latest position only). Retention 30 days by received_at, deleted in batches by
--    the courier-location-retention job (LAC-63).
--    Plain table instead of declarative partitioning, on purpose: expected MVP volume is small (tens of couriers,
--    one point per ~10 s only during deliveries => well under 1 M rows/day), partitions would need a second job to
--    create them ahead of time plus a composite PK (id, recorded_at), and the app GPS sender is not shipped yet.
--    The BRIN index on received_at keeps the batched retention DELETE cheap on this append-only table. Move to
--    monthly/daily RANGE partitions (DROP PARTITION instead of DELETE) when sustained volume passes ~5 M rows/day.
--
-- Locking: ADD COLUMN without default is metadata-only; the CREATE TABLE/INDEX statements touch only new tables.
-- Compatibility: additive; the previous application version ignores all of it.
-- Rollback / forward-fix: DROP TABLE courier_location_history, courier_last_locations;
--   ALTER TABLE couriers DROP COLUMN location_consent_accepted_at, DROP COLUMN location_consent_version;
-- The V017 table courier_location_updates was never written by the application and is left untouched (drop it in a
-- later contract migration).

ALTER TABLE couriers
    ADD COLUMN location_consent_accepted_at TIMESTAMPTZ,
    ADD COLUMN location_consent_version VARCHAR(40);

CREATE TABLE courier_last_locations (
    courier_id   UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    location     geography(Point, 4326) NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    accuracy_m   REAL,
    speed_mps    REAL,
    heading_deg  REAL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_courier_last_locations_courier_id FOREIGN KEY (courier_id) REFERENCES couriers (id),
    CONSTRAINT fk_courier_last_locations_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_courier_last_locations_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_courier_last_locations_lng CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_courier_last_locations_tenant_received ON courier_last_locations (tenant_id, received_at);
CREATE INDEX idx_courier_last_locations_location ON courier_last_locations USING GIST (location);

CREATE TABLE courier_location_history (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL,
    courier_id   UUID NOT NULL,
    delivery_id  UUID NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    accuracy_m   REAL,
    speed_mps    REAL,
    heading_deg  REAL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_courier_location_history_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_courier_location_history_courier_id FOREIGN KEY (courier_id) REFERENCES couriers (id),
    CONSTRAINT fk_courier_location_history_delivery_id FOREIGN KEY (delivery_id) REFERENCES deliveries (id),
    CONSTRAINT ck_courier_location_history_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_courier_location_history_lng CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_courier_location_history_delivery_recorded ON courier_location_history (delivery_id, recorded_at);
CREATE INDEX idx_courier_location_history_courier_recorded ON courier_location_history (courier_id, recorded_at);
CREATE INDEX brin_courier_location_history_received ON courier_location_history USING BRIN (received_at);

COMMENT ON TABLE courier_last_locations IS 'LAC-40: latest position per on-duty courier (upsert); deleted when the courier goes off duty.';
COMMENT ON TABLE courier_location_history IS 'LAC-40: route points during deliveries; 30-day retention (courier-location-retention job).';
COMMENT ON COLUMN couriers.location_consent_version IS 'LAC-40: consent text version accepted by the courier (null = no consent).';
