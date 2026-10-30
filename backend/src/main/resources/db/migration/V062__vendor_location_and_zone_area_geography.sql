-- W5-C (LAC-39) — expand: PostGIS geography for vendor positions and zone areas.
--
--   * vendors.location geography(Point,4326), derived from latitude/longitude (same pattern as V046 for
--     customer_addresses.location) by backfill + BEFORE trigger, with a GiST index used by vendor search
--     (ST_DWithin) and the pricing engine (ST_Distance).
--   * zones.area geography(MultiPolygon,4326), derived from geometry_wkt (POLYGON or MULTIPOLYGON, SRID 4326,
--     ST_IsValid) by backfill + BEFORE trigger, with a GiST index used by geo.api.ServiceArea (ST_Covers).
--   * No vendor_zones table: the vendor -> zone link already exists as vendors.delivery_zone_id (V016) and the
--     register only needs a single delivery zone per store for now (many-to-many is LAC-60 territory).
--
-- Expand/contract: latitude/longitude and geometry_wkt remain the columns the application writes during the
-- transition; the geography columns are derived by trigger so every writer (JPA, seed scripts, psql) stays
-- coherent without hibernate-spatial. The contract (stop writing lat/lng/WKT, drop those columns) is a later
-- package, after the zone editor (LAC-60) writes `area` directly.
--
-- Locking (vendors and zones are small, operator-managed tables):
--   * ADD COLUMN without default: ACCESS EXCLUSIVE, metadata only, instantaneous.
--   * UPDATE backfill: row locks on the rows with coordinates / WKT, inside the migration transaction
--     (blocks concurrent writers of those rows for the length of the migration; readers are not blocked).
--   * CREATE INDEX ... USING GIST: SHARE lock (blocks writes, not reads) while building. Not CONCURRENTLY because
--     Flyway runs each migration in a transaction; with hundreds of rows the build is instantaneous. If either
--     table ever gets large, build the index outside Flyway with CREATE INDEX CONCURRENTLY first.
--   * CREATE TRIGGER: SHARE ROW EXCLUSIVE, brief.
-- Compatibility with the previous application version: full. Both columns are nullable and unmapped by JPA
-- (ddl-auto=validate ignores unmapped columns); the previous version keeps writing latitude/longitude and
-- geometry_wkt and the triggers keep the new columns in sync. Its straight-line distance query computed the same
-- value from latitude/longitude, so old and new instances return identical distances during a rolling deploy.
-- Rollback (only if needed; prefer forward-fix). The new application version needs vendors.location, so roll back
-- the application first, then:
--   DROP TRIGGER trg_vendors_sync_location ON vendors;   DROP FUNCTION vendors_sync_location();
--   DROP TRIGGER trg_zones_sync_area ON zones;           DROP FUNCTION zones_sync_area();
--   DROP FUNCTION zones_area_from_wkt(text);
--   DROP INDEX idx_vendors_location; DROP INDEX idx_zones_area;
--   ALTER TABLE vendors DROP COLUMN location; ALTER TABLE zones DROP COLUMN area;
--   DELETE FROM flyway_schema_history WHERE version = '62';
--
-- Report queries (run before and after; rows listed here are never guessed, they need manual correction):
--   -- vendors whose coordinates cannot become a location (half-set, (0,0) placeholder, out of range)
--   SELECT id, tenant_id, name, latitude, longitude FROM vendors
--    WHERE (latitude IS NULL) <> (longitude IS NULL)
--       OR (latitude = 0 AND longitude = 0)
--       OR latitude NOT BETWEEN -90 AND 90 OR longitude NOT BETWEEN -180 AND 180;
--   -- vendors without any position (excluded from radius search)
--   SELECT id, tenant_id, name FROM vendors WHERE location IS NULL;
--   -- zones whose WKT is set but is not a valid (multi)polygon (after the migration)
--   SELECT id, tenant_id, name, left(geometry_wkt, 80) AS wkt FROM zones
--    WHERE geometry_wkt IS NOT NULL AND btrim(geometry_wkt) <> '' AND area IS NULL;

-- ---------------------------------------------------------------------------------------------------------
-- vendors.location
-- ---------------------------------------------------------------------------------------------------------

ALTER TABLE vendors ADD COLUMN location geography(Point, 4326);

-- Backfill: complete, in-range pairs only; (0,0) is the "not set" placeholder the app used (GeoPoint.of).
UPDATE vendors
   SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
 WHERE location IS NULL
   AND latitude IS NOT NULL AND longitude IS NOT NULL
   AND NOT (latitude = 0 AND longitude = 0)
   AND latitude BETWEEN -90 AND 90
   AND longitude BETWEEN -180 AND 180;

CREATE INDEX idx_vendors_location ON vendors USING GIST (location);

-- Keeps location coherent with latitude/longitude while both coexist (expand phase). Unusable coordinates give
-- NULL instead of an error so a bad pin never blocks a vendor profile update.
CREATE OR REPLACE FUNCTION vendors_sync_location() RETURNS trigger AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL
       AND NOT (NEW.latitude = 0 AND NEW.longitude = 0)
       AND NEW.latitude BETWEEN -90 AND 90 AND NEW.longitude BETWEEN -180 AND 180 THEN
        NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    ELSE
        NEW.location := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vendors_sync_location
    BEFORE INSERT OR UPDATE OF latitude, longitude ON vendors
    FOR EACH ROW EXECUTE FUNCTION vendors_sync_location();

COMMENT ON COLUMN vendors.location IS 'Store position (WGS84); derived from latitude/longitude by trigger during the LAC-39 expand phase';

-- ---------------------------------------------------------------------------------------------------------
-- zones.area
-- ---------------------------------------------------------------------------------------------------------

ALTER TABLE zones ADD COLUMN area geography(MultiPolygon, 4326);

-- Parses a zone WKT into a service area: POLYGON/MULTIPOLYGON, SRID 4326 (plain WKT or EWKT with SRID=4326),
-- topologically valid (ST_IsValid) and inside geography range. Anything else (blank, unparsable, POINT, invalid
-- ring, other SRID) yields NULL instead of an error so one bad zone can neither abort this migration nor block a
-- zone save; such rows are listed by the report query above.
CREATE OR REPLACE FUNCTION zones_area_from_wkt(wkt text) RETURNS geography AS $$
DECLARE
    g geometry;
BEGIN
    IF wkt IS NULL OR btrim(wkt) = '' THEN
        RETURN NULL;
    END IF;
    g := ST_GeomFromEWKT(btrim(wkt));
    IF ST_SRID(g) = 0 THEN
        g := ST_SetSRID(g, 4326);
    END IF;
    IF ST_SRID(g) <> 4326 OR GeometryType(g) NOT IN ('POLYGON', 'MULTIPOLYGON') OR ST_IsEmpty(g) OR NOT ST_IsValid(g) THEN
        RETURN NULL;
    END IF;
    RETURN ST_Multi(g)::geography;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

UPDATE zones SET area = zones_area_from_wkt(geometry_wkt) WHERE geometry_wkt IS NOT NULL;

CREATE INDEX idx_zones_area ON zones USING GIST (area);

CREATE OR REPLACE FUNCTION zones_sync_area() RETURNS trigger AS $$
BEGIN
    NEW.area := zones_area_from_wkt(NEW.geometry_wkt);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_zones_sync_area
    BEFORE INSERT OR UPDATE OF geometry_wkt ON zones
    FOR EACH ROW EXECUTE FUNCTION zones_sync_area();

COMMENT ON COLUMN zones.area IS 'Service area (WGS84); derived from geometry_wkt by trigger during the LAC-39 expand phase; NULL when the WKT is not a valid (multi)polygon';
