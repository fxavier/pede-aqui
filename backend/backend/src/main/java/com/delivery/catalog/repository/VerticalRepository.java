package com.delivery.catalog.repository;

import com.delivery.catalog.entity.Vertical;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerticalRepository extends JpaRepository<Vertical, UUID> {
    List<Vertical> findByTenantId(UUID tenantId);
    Optional<Vertical> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndSlugAndIdNot(UUID tenantId, String slug, UUID excludeId);
}
