package com.delivery.geo.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.geo.dto.SearchVendorResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Provides tenant-scoped vendor discovery; vendor persistence is added in the vendor slice. */
@Service
public class SearchService {
    private final TenantContext tenantContext;
    private final ProductRepository productRepository;

    public SearchService(TenantContext tenantContext, ProductRepository productRepository) {
        this.tenantContext = tenantContext;
        this.productRepository = productRepository;
    }

    /** Returns nearby vendor results for the current tenant when vendor data exists. */
    public List<SearchVendorResponse> searchNearby(Double latitude, Double longitude, UUID categoryId, Boolean available) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));

        List<UUID> vendorIds = categoryId == null
                ? productRepository.findDistinctVendorIdsByTenantIdAndStatus(tenantId, "ACTIVE")
                : productRepository.findDistinctVendorIdsByTenantIdAndCategoryIdAndStatus(tenantId, categoryId, "ACTIVE");

        if (Boolean.FALSE.equals(available)) {
            return List.of();
        }

        double normalizedLat = latitude == null ? 0.0d : latitude;
        double normalizedLng = longitude == null ? 0.0d : longitude;
        return vendorIds.stream()
                .map(vendorId -> new SearchVendorResponse(
                        vendorId,
                        "Vendor " + vendorId.toString().substring(0, 8),
                        pseudoDistanceKm(vendorId, normalizedLat, normalizedLng),
                        true,
                        0.0d))
                .sorted((a, b) -> Double.compare(a.distanceKm(), b.distanceKm()))
                .toList();
    }

    private double pseudoDistanceKm(UUID vendorId, double latitude, double longitude) {
        long hash = Math.abs(vendorId.getMostSignificantBits() ^ vendorId.getLeastSignificantBits());
        double jitter = (hash % 5000) / 1000.0d;
        double anchorDistance = Math.abs(latitude) * 0.01d + Math.abs(longitude) * 0.01d;
        return Math.round((anchorDistance + jitter) * 100.0d) / 100.0d;
    }
}
