package com.delivery.geo.service;

import com.delivery.cart.service.PricingService;
import com.delivery.common.pricing.DeliveryFeeQuote;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.common.security.TenantContext;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.geo.dto.SearchResponse;
import com.delivery.geo.dto.SearchSortOption;
import com.delivery.geo.dto.SearchVendorResponse;
import com.delivery.vendor.api.StoreAvailabilityService;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Provides tenant-scoped vendor discovery backed by real vendor records. */
@Service
public class SearchService {
    private final TenantContext tenantContext;
    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final PricingService pricingService;
    private final VendorSearchCache searchCache;
    private final StoreAvailabilityService storeAvailability;
    private static final int PAGE_SIZE = 20;
    private static final int BASE_DELIVERY_MINUTES = 15;
    private static final int PER_KM_MINUTES = 3;

    public SearchService(TenantContext tenantContext, ProductRepository productRepository, VendorRepository vendorRepository,
                         PricingService pricingService, VendorSearchCache searchCache, StoreAvailabilityService storeAvailability) {
        this.storeAvailability = storeAvailability;
        this.tenantContext = tenantContext;
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.pricingService = pricingService;
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

    private List<SearchVendorResponse> search(VendorSearchQuery query) {
        List<Vendor> vendors = vendorRepository.findAllById(resolveVendorIds(query.tenantId(), query.category()));

        double normalizedLat = query.latitude() == null ? 0.0d : query.latitude();
        double normalizedLng = query.longitude() == null ? 0.0d : query.longitude();
        Double minRating = query.minRating();
        Integer maxDeliveryMinutes = query.maxDeliveryMinutes();
        // Same engine and distance function as checkout (INC-13): the fee shown is the fee charged for this position.
        Map<UUID, DeliveryFeeQuote> fees = pricingService.estimateDeliveryFees(vendors, GeoPoint.of(query.latitude(), query.longitude()).orElse(null));

        return vendors.stream()
                .map(vendor -> createVendorResponse(vendor, fees.get(vendor.getId()), normalizedLat, normalizedLng))
                .filter(vendor -> isWithinRadius(vendor, query.radiusMeters()))
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
     * Builds the search row from the real vendor record. Distance and delivery fee come from the pricing engine
     * (PostGIS straight line when vendor and customer both have coordinates); without them the fee is the tenant
     * base fee and the distance a deterministic stand-in used only for ordering/filtering.
     */
    private SearchVendorResponse createVendorResponse(Vendor vendor, DeliveryFeeQuote fee, double latitude, double longitude) {
        double distanceKm = fee.distanceKm() != null ? fee.distanceKm().doubleValue() : pseudoDistanceKm(vendor.getId(), latitude, longitude);
        int distanceMeters = (int) (distanceKm * 1000);
        int estimatedDeliveryMinutes = vendor.getEstimatedDeliveryMinutes() > 0
                ? vendor.getEstimatedDeliveryMinutes()
                : BASE_DELIVERY_MINUTES + (int) (distanceKm * PER_KM_MINUTES);

        return new SearchVendorResponse(
                vendor.getId(),
                vendor.getName(),
                distanceKm,
                distanceMeters,
                vendor.isAvailable(),
                vendor.getRating(),
                estimatedDeliveryMinutes,
                fee.amount(),
                fee.basis().name(),
                false, // placeholder: open state is stamped after the cache read (LAC-27)
                null
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
}
