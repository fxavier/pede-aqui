package com.delivery.geo.repository;

import com.delivery.geo.entity.Zone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides tenant-scoped persistence access for operations zones. */
public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    List<Zone> findByTenantId(UUID tenantId);
    Optional<Zone> findByTenantIdAndId(UUID tenantId, UUID id);

    /** ST_Covers of one ACTIVE tenant zone's area over a WGS84 point (LAC-39); false when the zone has no valid area. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM zones z
                            WHERE z.tenant_id = :tenantId AND z.id = :zoneId AND z.status = 'ACTIVE'
                              AND ST_Covers(z.area, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography)))
            """, nativeQuery = true)
    boolean zoneCovers(@Param("tenantId") UUID tenantId, @Param("zoneId") UUID zoneId,
                       @Param("latitude") double latitude, @Param("longitude") double longitude);

    /** ST_Covers of any ACTIVE tenant zone's area over a WGS84 point (LAC-39), GiST-indexed on zones.area. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM zones z
                            WHERE z.tenant_id = :tenantId AND z.status = 'ACTIVE'
                              AND ST_Covers(z.area, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography)))
            """, nativeQuery = true)
    boolean anyZoneCovers(@Param("tenantId") UUID tenantId,
                          @Param("latitude") double latitude, @Param("longitude") double longitude);
}
