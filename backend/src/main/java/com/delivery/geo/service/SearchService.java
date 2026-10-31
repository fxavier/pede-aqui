package com.delivery.geo.service;

import com.delivery.common.pricing.DeliveryFeeQuote;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.common.security.TenantContext;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.geo.api.DeliveryFeeEstimator;
import com.delivery.geo.dto.SearchResponse;
import com.delivery.geo.dto.SearchSortOption;
import com.delivery.geo.dto.SearchVendorResponse;
import com.delivery.geo.repository.VendorProximityRepository;
import com.delivery.geo.repository.VendorProximityRepository.VendorProximity;
import com.delivery.vendor.api.StoreAvailabilityService;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Provides tenant-scoped vendor discovery backed by real vendor records. */
@Service
public class SearchService {
    private final TenantContext tenantContext;
    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final DeliveryFeeEstimator feeEstimator;
    private final VendorSearchCache searchCache;
    private final StoreAvailabilityService storeAvailability;
    private final VendorProximityRepository proximityRepository;
    private static final int PAGE_SIZE = 20;
    private static final int BASE_DELIVERY_MINUTES = 15;
    private static final int PER_KM_MINUTES = 3;

    public SearchService(TenantContext tenantContext, ProductRepository productRepository, VendorRepository vendorRepository,
                         DeliveryFeeEstimator feeEstimator, VendorSearchCache searchCache, StoreAvailabilityService storeAvailability,
                         VendorProximityRepository proximityRepository) {
        this.storeAvailability = storeAvailability;
        this.proximityRepository = proximityRepository;
        this.tenantContext = tenantContext;
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.feeEstimator = feeEstimator;
        this.searchCache = searchCache;
    }

    /** Cached, paginated vendor search. Anonymous and customer requests search across all tenants. */
    public List<SearchVendorResponse> searchVendors(Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        return page(allResults(latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort), page);
    }

    /** Cached vendor search with the total count for the API response; count and page come from one cached result set. */
    public SearchResponse searchVendorsWithCount(Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort, Integer page) {
        List<SearchVendorResponse> allResults = allResults(latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort);
        return new SearchResponse(page(allResults, page), allResults.size(), page, PAGE_SIZE);
    }

    /**
     * Full filtered and sorted result set, served from the vendorSearch cache (LAC-51), then stamped with each store's
     * open state at request time and re-ordered so closed stores come last (LAC-27), keeping the cached order within
     * the open and the closed group (stable sort).
     * <p>Open state is deliberately computed AFTER the cache read instead of being cached (or keyed by a time bucket):
     * a store opens/closes at exact minutes and OPS closures must take effect immediately, so a cached open flag would
     * be wrong for up to the TTL/bucket length. The cost is three indexed queries per search over the result's vendors.
     */
    private List<SearchVendorResponse> allResults(Double latitude, Double longitude, Integer radius, String category, Double minRating, Integer maxDeliveryMinutes, String sort) {
        VendorSearchQuery query = VendorSearchQuery.of(tenantContext.browseTenantId().orElse(null),
                latitude, longitude, radius, category, minRating, maxDeliveryMinutes, sort);
        List<SearchVendorResponse> cached = searchCache.results(query, () -> search(query));
        Map<UUID, StoreAvailabilityService.Availability> availability =
                storeAvailability.availabilityNow(cached.stream().map(SearchVendorResponse::vendorId).toList());
        return cached.stream()
                .map(row -> {
                    StoreAvailabilityService.Availability state = availability.get(row.vendorId());
                    return state == null ? row.withOpenState(false, null) : row.withOpenState(state.open(), state.nextOpeningAt());
                })
                .sorted(Comparator.comparing(SearchVendorResponse::open).reversed())
                .toList();
    }

    private static List<SearchVendorResponse> page(List<SearchVendorResponse> allResults, Integer page) {
        int startIndex = (page == null ? 0 : page) * PAGE_SIZE;
        if (startIndex >= allResults.size()) {
            return List.of();
        }
        return allResults.subList(startIndex, Math.min(startIndex + PAGE_SIZE, allResults.size()));
    }

    /**
     * Uncached search. With a usable customer point (LAC-39) the candidate set comes from one PostGIS query: vendors
     * whose {@code location} is within the radius ({@code ST_DWithin}, GiST index) and that sell an ACTIVE product in
     * scope, with their geodesic distance; vendors without a location cannot be placed and are excluded. Without a
     * point the radius cannot be applied: every vendor in scope is returned with an unknown (null) distance.
     */
    private List<SearchVendorResponse> search(VendorSearchQuery query) {
        Optional<GeoPoint> point = GeoPoint.of(query.latitude(), query.longitude());
        Map<UUID, Double> distancesKm = new HashMap<>();
        List<Vendor> vendors;
        if (point.isPresent()) {
            for (VendorProximity row : proximityRepository.findWithinRadius(query.tenantId(),
                    query.category() == null ? null : UUID.fromString(query.category()),
                    point.get().latitude(), point.get().longitude(), query.radiusMeters())) {
                distancesKm.put(row.vendorId(), row.distanceKm());
            }
            vendors = vendorRepository.findAllById(List.copyOf(distancesKm.keySet()));
        } else {
            vendors = vendorRepository.findAllById(resolveVendorIds(query.tenantId(), query.category()));
        }

        Double minRating = query.minRating();
        Integer maxDeliveryMinutes = query.maxDeliveryMinutes();
        // Same engine and distance function as checkout (INC-13): the fee shown is the fee charged for this position.
        Map<UUID, DeliveryFeeQuote> fees = feeEstimator.estimateDeliveryFees(vendors, point.orElse(null));

        return vendors.stream()
                .map(vendor -> createVendorResponse(vendor, fees.get(vendor.getId()), distancesKm.get(vendor.getId())))
                .filter(vendor -> minRating == null || vendor.rating() >= minRating)
                .filter(vendor -> maxDeliveryMinutes == null || vendor.estimatedDeliveryMinutes() <= maxDeliveryMinutes)
                .sorted(getComparator(query.sort()))
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

    /**
     * Builds the search row from the real vendor record. The distance is the PostGIS geodesic distance from the radius
     * query (2 decimals, like the pricing engine's), or null when the customer's position is unknown; the delivery fee
     * comes from the pricing engine (tenant base fee when either position is unknown).
     */
    private SearchVendorResponse createVendorResponse(Vendor vendor, DeliveryFeeQuote fee, Double rawDistanceKm) {
        Double distanceKm = rawDistanceKm == null ? null
                : BigDecimal.valueOf(rawDistanceKm).setScale(2, RoundingMode.HALF_UP).doubleValue();
        Integer distanceMeters = rawDistanceKm == null ? null : (int) Math.round(rawDistanceKm * 1000);
        int estimatedDeliveryMinutes = vendor.getEstimatedDeliveryMinutes() > 0
                ? vendor.getEstimatedDeliveryMinutes()
                : BASE_DELIVERY_MINUTES + (int) ((distanceKm == null ? 0 : distanceKm) * PER_KM_MINUTES);

        return new SearchVendorResponse(
                vendor.getId(),
                vendor.getName(),
                distanceKm,
                distanceMeters,
                vendor.isAvailable(),
                vendor.getRating(),
                vendor.getRatingCount(),
                estimatedDeliveryMinutes,
                fee.amount(),
                fee.basis().name(),
                false, // placeholder: open state is stamped after the cache read (LAC-27)
                null
        );
    }

    private Comparator<SearchVendorResponse> getComparator(String sort) {
        SearchSortOption sortOption = sort != null ? SearchSortOption.valueOf(sort.toUpperCase()) : SearchSortOption.RELEVANCE;

        return switch (sortOption) {
            case DISTANCE -> Comparator.comparing(SearchVendorResponse::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()));
            case RATING -> Comparator.comparing(SearchVendorResponse::rating).reversed();
            case RELEVANCE -> Comparator.comparing(this::calculateRelevanceScore).reversed();
        };
    }

    private double calculateRelevanceScore(SearchVendorResponse vendor) {
        // Score = (rating * 0.4) + (1/distanceKm * 0.4) + (isOpen * 0.2)
        double ratingScore = vendor.rating() * 0.4;
        // Unknown distance (no customer position, LAC-39) contributes nothing.
        double distanceScore = vendor.distanceKm() == null ? 0.0
                : vendor.distanceKm() > 0 ? (1.0 / vendor.distanceKm()) * 0.4 : 0.4;
        double availabilityScore = vendor.available() ? 0.2 : 0.0;
        return ratingScore + distanceScore + availabilityScore;
    }
}
