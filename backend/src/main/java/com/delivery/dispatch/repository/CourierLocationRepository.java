package com.delivery.dispatch.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to the LAC-40 location tables (V061): the per-courier latest position (upsert, PostGIS point built in
 * SQL) and the append-only route history (batch insert, batched retention delete). JDBC rather than JPA because the
 * rows are write-heavy, have no lifecycle and the geography column needs SQL functions.
 */
@Repository
public class CourierLocationRepository {

    /** One position as stored (latest position or a history point). */
    public record StoredLocation(UUID courierId, double latitude, double longitude, Double accuracyMeters, Double speedMps,
                                 Double headingDegrees, Instant recordedAt, Instant receivedAt) {}

    /** Latest position of an on-duty courier joined with its status, for the OPS zone view. */
    public record ZoneLocation(UUID courierId, String status, StoredLocation location) {}

    private static final RowMapper<StoredLocation> LOCATION = (rs, i) -> new StoredLocation(
            rs.getObject("courier_id", UUID.class), rs.getDouble("latitude"), rs.getDouble("longitude"),
            nullableDouble(rs.getObject("accuracy_m")), nullableDouble(rs.getObject("speed_mps")),
            nullableDouble(rs.getObject("heading_deg")),
            rs.getTimestamp("recorded_at").toInstant(), rs.getTimestamp("received_at").toInstant());

    private final JdbcTemplate jdbc;

    public CourierLocationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Latest stored position of the courier, if any. */
    public Optional<StoredLocation> findLast(UUID courierId) {
        return jdbc.query("SELECT * FROM courier_last_locations WHERE courier_id = ?", LOCATION, courierId).stream().findFirst();
    }

    /**
     * Upserts the latest position: the stored point is replaced only by a newer {@code recordedAt}; {@code received_at}
     * (presence) is always moved forward.
     */
    public void upsertLast(UUID tenantId, StoredLocation point) {
        jdbc.update("""
                INSERT INTO courier_last_locations (courier_id, tenant_id, location, latitude, longitude, accuracy_m, speed_mps,
                                                    heading_deg, recorded_at, received_at)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (courier_id) DO UPDATE
                   SET location = EXCLUDED.location, latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude,
                       accuracy_m = EXCLUDED.accuracy_m, speed_mps = EXCLUDED.speed_mps, heading_deg = EXCLUDED.heading_deg,
                       recorded_at = EXCLUDED.recorded_at, received_at = EXCLUDED.received_at
                 WHERE courier_last_locations.recorded_at < EXCLUDED.recorded_at""",
                point.courierId(), tenantId, point.longitude(), point.latitude(), point.latitude(), point.longitude(),
                point.accuracyMeters(), point.speedMps(), point.headingDegrees(),
                Timestamp.from(point.recordedAt()), Timestamp.from(point.receivedAt()));
        touch(point.courierId(), point.receivedAt());
    }

    /** Moves the presence timestamp forward (a batch whose points were all dropped is still a heartbeat). */
    public void touch(UUID courierId, Instant receivedAt) {
        jdbc.update("UPDATE courier_last_locations SET received_at = ? WHERE courier_id = ? AND received_at < ?",
                Timestamp.from(receivedAt), courierId, Timestamp.from(receivedAt));
    }

    /** Deletes the courier's latest position (courier went off duty). */
    public void deleteLast(UUID courierId) {
        jdbc.update("DELETE FROM courier_last_locations WHERE courier_id = ?", courierId);
    }

    /** Appends route points of one delivery. */
    public void insertHistory(UUID tenantId, UUID deliveryId, List<StoredLocation> points) {
        jdbc.batchUpdate("""
                INSERT INTO courier_location_history (tenant_id, courier_id, delivery_id, latitude, longitude, accuracy_m,
                                                      speed_mps, heading_deg, recorded_at, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", points, points.size(), (ps, p) -> {
            ps.setObject(1, tenantId);
            ps.setObject(2, p.courierId());
            ps.setObject(3, deliveryId);
            ps.setDouble(4, p.latitude());
            ps.setDouble(5, p.longitude());
            setReal(ps, 6, p.accuracyMeters());
            setReal(ps, 7, p.speedMps());
            setReal(ps, 8, p.headingDegrees());
            ps.setTimestamp(9, Timestamp.from(p.recordedAt()));
            ps.setTimestamp(10, Timestamp.from(p.receivedAt()));
        });
    }

    /** Deletes up to {@code batchSize} history rows received before the cutoff; returns how many were deleted. */
    public int deleteHistoryReceivedBefore(Instant cutoff, int batchSize) {
        return jdbc.update("""
                DELETE FROM courier_location_history
                 WHERE id IN (SELECT id FROM courier_location_history WHERE received_at < ? LIMIT ?)""",
                Timestamp.from(cutoff), batchSize);
    }

    /** Latest positions of the zone's on-duty (AVAILABLE/BUSY) couriers, most recently seen first. */
    public List<ZoneLocation> findOnDutyInZone(UUID tenantId, UUID zoneId, int limit) {
        return jdbc.query("""
                SELECT c.status, l.* FROM courier_last_locations l
                  JOIN couriers c ON c.id = l.courier_id
                 WHERE c.tenant_id = ? AND l.tenant_id = ? AND c.operating_zone_id = ? AND c.status IN ('AVAILABLE', 'BUSY')
                 ORDER BY l.received_at DESC, l.courier_id
                 LIMIT ?""",
                (rs, i) -> new ZoneLocation(rs.getObject("courier_id", UUID.class), rs.getString("status"), LOCATION.mapRow(rs, i)),
                tenantId, tenantId, zoneId, limit);
    }

    private static void setReal(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static Double nullableDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
