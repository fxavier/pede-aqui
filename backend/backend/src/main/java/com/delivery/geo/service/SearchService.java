package com.delivery.geo.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.geo.dto.SearchResponse;
import com.delivery.geo.dto.SearchSortOption;
import com.delivery.geo.dto.SearchVendorResponse;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Provides tenant-scoped vendor discovery; vendor persistence is added in the vendor slice. */
@Service
public class SearchService {
    private final TenantContext tenantContext;
    private final ProductRepository productRepository;
    private static final int PAGE_SIZE = 20;
    private static final double BASE_DELIVERY_FEE = 2.50;
    private static final double PER_KM_FEE = 0.50;
    private static final int BASE_DELIVERY_MINUTES = 15;
    private static final int PER_KM_MINUTES = 3;

    public SearchService(TenantContext tenantContext, ProductRepository productRepository) {
        this.tenantContext = tenantContext;
        this.productRepository = productRepository;
    }

    /** Enhanced search with Redis caching, pagination, and filtering. */
    public List<SearchVendorResponse> searchVendors(Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));

        return searchVendorsWithCache(tenantId, latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort, page);
    }

    /** Enhanced search that includes totalCount for API response. */
    public SearchResponse searchVendorsWithCount(Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));

        // Get total count before pagination
        List<SearchVendorResponse> allResults = searchVendorsInternalForCount(tenantId, latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort);
        int totalCount = allResults.size();
        
        // Get paginated results
        List<SearchVendorResponse> pageResults = searchVendorsWithCache(tenantId, latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort, page);
        
        return new SearchResponse(pageResults, totalCount, page, PAGE_SIZE);
    }

    @Cacheable(value = "vendorSearch", key = "#tenantId + '_' + #latitude + '_' + #longitude + '_' + #radius + '_' + #category + '_' + #sort + '_' + #page")
    private List<SearchVendorResponse> searchVendorsWithCache(UUID tenantId, Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        return searchVendorsInternal(tenantId, latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort, page);
    }

    private List<SearchVendorResponse> searchVendorsInternal(UUID tenantId, Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        // Get vendor IDs from products (existing logic)
        List<UUID> vendorIds = category == null
                ? productRepository.findDistinctVendorIdsByTenantIdAndStatus(tenantId, "ACTIVE")
                : productRepository.findDistinctVendorIdsByTenantIdAndCategoryIdAndStatus(tenantId, UUID.fromString(category), "ACTIVE");

        double normalizedLat = latitude == null ? 0.0d : latitude;
        double normalizedLng = longitude == null ? 0.0d : longitude;
        int searchRadius = radius == null ? 5000 : radius;

        List<SearchVendorResponse> allResults = vendorIds.stream()
                .map(vendorId -> createVendorResponse(vendorId, normalizedLat, normalizedLng))
                .filter(vendor -> isWithinRadius(vendor, searchRadius))
                .filter(vendor -> minRating == null || vendor.rating() >= minRating)
                .filter(vendor -> maxDeliveryMinutes == null || vendor.estimatedDeliveryMinutes() <= maxDeliveryMinutes)
                .sorted(getComparator(sort))
                .toList();

        // Pagination
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, allResults.size());
        
        if (startIndex >= allResults.size()) {
            return List.of();
        }

        return allResults.subList(startIndex, endIndex);
    }

    private List<SearchVendorResponse> searchVendorsInternalForCount(UUID tenantId, Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort) {
        // Get vendor IDs from products (existing logic)
        List<UUID> vendorIds = category == null
                ? productRepository.findDistinctVendorIdsByTenantIdAndStatus(tenantId, "ACTIVE")
                : productRepository.findDistinctVendorIdsByTenantIdAndCategoryIdAndStatus(tenantId, UUID.fromString(category), "ACTIVE");

        double normalizedLat = latitude == null ? 0.0d : latitude;
        double normalizedLng = longitude == null ? 0.0d : longitude;
        int searchRadius = radius == null ? 5000 : radius;

        return vendorIds.stream()
                .map(vendorId -> createVendorResponse(vendorId, normalizedLat, normalizedLng))
                .filter(vendor -> isWithinRadius(vendor, searchRadius))
                .filter(vendor -> minRating == null || vendor.rating() >= minRating)
                .filter(vendor -> maxDeliveryMinutes == null || vendor.estimatedDeliveryMinutes() <= maxDeliveryMinutes)
                .sorted(getComparator(sort))
                .toList();
    }

    /** Legacy method for backward compatibility. */
    public List<SearchVendorResponse> searchNearby(Double latitude, Double longitude, UUID categoryId, Boolean available) {
        String categoryStr = categoryId != null ? categoryId.toString() : null;
        return searchVendors(latitude, longitude, 5000, categoryStr, null, null, "DISTANCE", 0);
    }

    private SearchVendorResponse createVendorResponse(UUID vendorId, double latitude, double longitude) {
        double distanceKm = pseudoDistanceKm(vendorId, latitude, longitude);
        int distanceMeters = (int) (distanceKm * 1000);
        boolean available = true; // TODO: Get from vendor availability
        double rating = pseudoRating(vendorId);
        int estimatedDeliveryMinutes = BASE_DELIVERY_MINUTES + (int) (distanceKm * PER_KM_MINUTES);
        double deliveryFee = BASE_DELIVERY_FEE + (distanceKm * PER_KM_FEE);

        return new SearchVendorResponse(
                vendorId,
                "Vendor " + vendorId.toString().substring(0, 8),
                distanceKm,
                distanceMeters,
                available,
                rating,
                estimatedDeliveryMinutes,
                Math.round(deliveryFee * 100.0) / 100.0
        );
    }

    private boolean isWithinRadius(SearchVendorResponse vendor, int radiusMeters) {
        return vendor.distanceMeters() <= radiusMeters;
    }

    private Comparator<SearchVendorResponse> getComparator(String sort) {
        SearchSortOption sortOption = sort != null ? SearchSortOption.valueOf(sort.toUpperCase()) : SearchSortOption.RELEVANCE;
        
        return switch (sortOption) {
            case DISTANCE -> Comparator.comparing(SearchVendorResponse::distanceKm);
            case RATING -> Comparator.comparing(SearchVendorResponse::rating).reversed();
            case RELEVANCE -> Comparator.comparing(this::calculateRelevanceScore).reversed();
        };
    }

    private double calculateRelevanceScore(SearchVendorResponse vendor) {
        // Score = (rating * 0.4) + (1/distanceKm * 0.4) + (isOpen * 0.2)
        double ratingScore = vendor.rating() * 0.4;
        double distanceScore = vendor.distanceKm() > 0 ? (1.0 / vendor.distanceKm()) * 0.4 : 0.4;
        double availabilityScore = vendor.available() ? 0.2 : 0.0;
        return ratingScore + distanceScore + availabilityScore;
    }

    private double pseudoDistanceKm(UUID vendorId, double latitude, double longitude) {
        long hash = Math.abs(vendorId.getMostSignificantBits() ^ vendorId.getLeastSignificantBits());
        double jitter = (hash % 5000) / 1000.0d;
        double anchorDistance = Math.abs(latitude) * 0.01d + Math.abs(longitude) * 0.01d;
        return Math.round((anchorDistance + jitter) * 100.0d) / 100.0d;
    }

    private double pseudoRating(UUID vendorId) {
        long hash = Math.abs(vendorId.getLeastSignificantBits());
        return 3.0 + ((hash % 20) / 10.0); // Rating between 3.0 and 5.0
    }
}
