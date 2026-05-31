package com.delivery.catalog.repository;

import com.delivery.catalog.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped category queries. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByTenantIdAndActiveTrue(UUID tenantId);
    List<Category> findByActiveTrue();
}
