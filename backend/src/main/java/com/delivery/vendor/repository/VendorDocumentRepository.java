package com.delivery.vendor.repository;

import com.delivery.vendor.entity.VendorDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides persistence access for vendor document metadata. */
public interface VendorDocumentRepository extends JpaRepository<VendorDocument, UUID> {
    List<VendorDocument> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);
}
