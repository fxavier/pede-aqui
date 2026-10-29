package com.delivery.geo.service;

import com.delivery.geo.dto.SearchVendorResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Caches full vendor search result sets in the "vendorSearch" cache (LAC-51). A separate bean so the call from
 * SearchService goes through the Spring cache proxy (a @Cacheable on a self-invoked private method never applies).
 * Entries live {@link #TTL}; store/product changes therefore surface within a minute.
 */
@Component
public class VendorSearchCache {
    public static final String CACHE_NAME = "vendorSearch";
    public static final Duration TTL = Duration.ofSeconds(60);

    /** Returns the cached result set for the query, computing it with the loader on a miss. */
    @Cacheable(cacheNames = CACHE_NAME, key = "#query.cacheKey()")
    public List<SearchVendorResponse> results(VendorSearchQuery query, Supplier<List<SearchVendorResponse>> loader) {
        // Plain ArrayList: immutable JDK lists do not round-trip through the Redis JSON serializer.
        return new ArrayList<>(loader.get());
    }
}
