package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.CourierDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierDocumentRepository extends JpaRepository<CourierDocument, UUID> {
    List<CourierDocument> findAllByCourierIdAndTenantId(UUID courierId, UUID tenantId);
}