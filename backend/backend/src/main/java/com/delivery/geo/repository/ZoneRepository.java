package com.delivery.geo.repository;

import com.delivery.geo.entity.Zone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped persistence access for operations zones. */
public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    List<Zone> findByTenantId(UUID tenantId);
    Optional<Zone> findByTenantIdAndId(UUID tenantId, UUID id);
}
