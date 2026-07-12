package com.delivery.geo.service;

import com.delivery.common.security.TenantContext;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.geo.dto.SearchResponse;
import com.delivery.geo.dto.SearchSortOption;
import com.delivery.geo.dto.SearchVendorResponse;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Provides tenant-scoped vendor discovery backed by real vendor records. */
@Service
public class SearchService {
    private final TenantContext tenantContext;
    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private static final int PAGE_SIZE = 20;
    private static final double BASE_DELIVERY_FEE = 2.50;
    private static final double PER_KM_FEE = 0.50;
    private static final int BASE_DELIVERY_MINUTES = 15;
    private static final int PER_KM_MINUTES = 3;
    private static final double EARTH_RADIUS_KM = 6371.0;

    public SearchService(TenantContext tenantContext, ProductRepository productRepository, VendorRepository vendorRepository) {
        this.tenantContext = tenantContext;
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
    }

    /** Enhanced search with Redis caching, pagination, and filtering. Anonymous requests search across all tenants. */
    public List<SearchVendorResponse> searchVendors(Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        Optional<UUID> tenantId = tenantContext.currentTenantId();
        return searchVendorsWithCache(tenantId.orElse(null), latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort, page);
    }

    /** Enhanced search that includes totalCount for API response. Anonymous requests search across all tenants. */
    public SearchResponse searchVendorsWithCount(Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        Optional<UUID> tenantId = tenantContext.currentTenantId();
        UUID tid = tenantId.orElse(null);

        List<SearchVendorResponse> allResults = searchVendorsInternalForCount(tid, latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort);
        int totalCount = allResults.size();
        List<SearchVendorResponse> pageResults = searchVendorsWithCache(tid, latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort, page);

        return new SearchResponse(pageResults, totalCount, page, PAGE_SIZE);
    }

    @Cacheable(value = "vendorSearch", key = "(#tenantId != null ? #tenantId : '*') + '_' + #latitude + '_' + #longitude + '_' + #radius + '_' + #category + '_' + #sort + '_' + #page")
    private List<SearchVendorResponse> searchVendorsWithCache(UUID tenantId, Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        return searchVendorsInternal(tenantId, latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort, page);
    }

    private List<SearchVendorResponse> searchVendorsInternal(UUID tenantId, Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        List<SearchVendorResponse> allResults = searchVendorsInternalForCount(tenantId, latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort);

        // Pagination
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, allResults.size());

        if (startIndex >= allResults.size()) {
            return List.of();
        }

        return allResults.subList(startIndex, endIndex);
    }

    private List<SearchVendorResponse> searchVendorsInternalForCount(UUID tenantId, Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort) {
        List<Vendor> vendors = vendorRepository.findAllById(resolveVendorIds(tenantId, category));

        double normalizedLat = latitude == null ? 0.0d : latitude;
        double normalizedLng = longitude == null ? 0.0d : longitude;
        int searchRadius = radius == null ? 5000 : radius;

        return vendors.stream()
                .map(vendor -> createVendorResponse(vendor, normalizedLat, normalizedLng))
                .filter(vendor -> isWithinRadius(vendor, searchRadius))
                .filter(vendor -> minRating == null || vendor.rating() >= minRating)
                .filter(vendor -> maxDeliveryMinutes == null || vendor.estimatedDeliveryMinutes() <= maxDeliveryMinutes)
                .sorted(getComparator(sort))
                .toList();
    }

    /** Resolves vendor IDs scoped to a tenant when present, or across all tenants for anonymous access. */
    private List<UUID> resolveVendorIds(UUID tenantId, String category) {
        if (tenantId == null) {
            return category == null
                    ? productRepository.findDistinctVendorIdsByStatus("ACTIVE")
                    : productRepository.findDistinctVendorIdsByCategoryIdAndStatus(UUID.fromString(category), "ACTIVE");
        }
        return category == null
                ? productRepository.findDistinctVendorIdsByTenantIdAndStatus(tenantId, "ACTIVE")
                : productRepository.findDistinctVendorIdsByTenantIdAndCategoryIdAndStatus(tenantId, UUID.fromString(category), "ACTIVE");
    }

    /** Legacy method for backward compatibility. */
    public List<SearchVendorResponse> searchNearby(Double latitude, Double longitude, UUID categoryId, Boolean available) {
        String categoryStr = categoryId != null ? categoryId.toString() : null;
        return searchVendors(latitude, longitude, 5000, categoryStr, null, null, "DISTANCE", 0);
    }

    /** Builds the search row from the real vendor record; only distance (and its derived fee/ETA fallback) is estimated. */
    private SearchVendorResponse createVendorResponse(Vendor vendor, double latitude, double longitude) {
        double distanceKm = distanceKm(vendor, latitude, longitude);
        int distanceMeters = (int) (distanceKm * 1000);
        int estimatedDeliveryMinutes = vendor.getEstimatedDeliveryMinutes() > 0
                ? vendor.getEstimatedDeliveryMinutes()
                : BASE_DELIVERY_MINUTES + (int) (distanceKm * PER_KM_MINUTES);
        double deliveryFee = BASE_DELIVERY_FEE + (distanceKm * PER_KM_FEE);

        return new SearchVendorResponse(
                vendor.getId(),
                vendor.getName(),
                distanceKm,
                distanceMeters,
                vendor.isAvailable(),
                vendor.getRating(),
                estimatedDeliveryMinutes,
                Math.round(deliveryFee * 100.0) / 100.0
        );
    }

    /** Haversine distance when both sides have coordinates; deterministic pseudo-distance otherwise. */
    private double distanceKm(Vendor vendor, double latitude, double longitude) {
        boolean hasVendorCoords = vendor.getLatitude() != null && vendor.getLongitude() != null
                && !(vendor.getLatitude() == 0.0 && vendor.getLongitude() == 0.0);
        boolean hasCustomerCoords = latitude != 0.0 || longitude != 0.0;
        if (hasVendorCoords && hasCustomerCoords) {
            return haversineKm(latitude, longitude, vendor.getLatitude(), vendor.getLongitude());
        }
        // ponytail: no coordinates on either side yet — deterministic stand-in so lists stay stable.
        return pseudoDistanceKm(vendor.getId(), latitude, longitude);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
        return Math.round(km * 100.0d) / 100.0d;
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
}
