package com.delivery.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.cart.service.PricingService;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.common.pricing.DeliveryFeeBasis;
import com.delivery.common.pricing.DeliveryFeeQuote;
import com.delivery.common.security.TenantContext;
import com.delivery.geo.dto.SearchResponse;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * LAC-51 (AS-14): the vendor search must really be served from the "vendorSearch" cache through the Spring proxy,
 * with a key that includes every filter (minRating, maxDeliveryMinutes) and the browse tenant. Uses an in-memory
 * cache manager; the Redis round-trip and TTL are covered by VendorSearchCacheRedisTest.
 */
@SpringJUnitConfig(SearchServiceCachingTest.Config.class)
class SearchServiceCachingTest {
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID VENDOR_ID = UUID.randomUUID();

    @Configuration
    @EnableCaching
    static class Config {
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager(VendorSearchCache.CACHE_NAME); }
        @Bean TenantContext tenantContext() { return mock(TenantContext.class); }
        @Bean ProductRepository productRepository() { return mock(ProductRepository.class); }
        @Bean VendorRepository vendorRepository() { return mock(VendorRepository.class); }
        @Bean PricingService pricingService() { return mock(PricingService.class); }
        @Bean VendorSearchCache vendorSearchCache() { return new VendorSearchCache(); }
        @Bean SearchService searchService(TenantContext t, ProductRepository p, VendorRepository v, PricingService ps,
                                          VendorSearchCache c) {
            return new SearchService(t, p, v, ps, c);
        }
    }

    @Autowired SearchService searchService;
    @Autowired TenantContext tenantContext;
    @Autowired ProductRepository productRepository;
    @Autowired VendorRepository vendorRepository;
    @Autowired PricingService pricingService;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(tenantContext, productRepository, vendorRepository, pricingService);
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        Vendor vendor = new Vendor(VENDOR_ID, TENANT_A, "Loja Cache", null, null, null, "Dona", "123", "84", "Rua", "", null);
        when(tenantContext.browseTenantId()).thenReturn(Optional.of(TENANT_A));
        when(productRepository.findDistinctVendorIdsByTenantIdAndStatus(any(), anyString())).thenReturn(List.of(VENDOR_ID));
        when(vendorRepository.findAllById(any())).thenReturn(List.of(vendor));
        when(pricingService.estimateDeliveryFees(anyCollection(), any())).thenAnswer(inv -> {
            Collection<Vendor> vendors = inv.getArgument(0);
            return vendors.stream().collect(Collectors.toMap(Vendor::getId, v -> new DeliveryFeeQuote(
                    new BigDecimal("50.00"), DeliveryFeeBasis.POLICY_BASE, null, null, null, null, null)));
        });
    }

    @Test
    void secondIdenticalSearchIsServedFromTheCache() {
        SearchResponse first = search(-25.96512, 32.58321, null, null, 0);
        SearchResponse second = search(-25.96512, 32.58321, null, null, 0);

        assertThat(second).isEqualTo(first);
        assertThat(first.vendors()).hasSize(1);
        verify(vendorRepository, times(1)).findAllById(any());
    }

    @Test
    void coordinatesWithinTheSameThousandthShareTheCacheEntry() {
        search(-25.96512, 32.58321, null, null, 0);
        search(-25.96538, 32.58349, null, null, 0); // both round to (-25.965, 32.583)

        verify(vendorRepository, times(1)).findAllById(any());
    }

    @Test
    void differentPagesOfTheSameQueryReuseOneCachedResultSet() {
        search(-25.96512, 32.58321, null, null, 0);
        search(-25.96512, 32.58321, null, null, 1);

        verify(vendorRepository, times(1)).findAllById(any());
    }

    @Test
    void differentMinRatingIsACacheMissAndIsFilteredCorrectly() {
        SearchResponse unfiltered = search(-25.96512, 32.58321, null, null, 0);
        SearchResponse filtered = search(-25.96512, 32.58321, 4.5, null, 0); // vendor rating is 0.0

        assertThat(unfiltered.vendors()).hasSize(1);
        assertThat(filtered.vendors()).isEmpty();
        verify(vendorRepository, times(2)).findAllById(any());
    }

    @Test
    void differentMaxDeliveryMinutesIsACacheMiss() {
        SearchResponse unfiltered = search(-25.96512, 32.58321, null, null, 0);
        SearchResponse filtered = search(-25.96512, 32.58321, null, 10, 0); // vendor ETA is 45 min

        assertThat(unfiltered.vendors()).hasSize(1);
        assertThat(filtered.vendors()).isEmpty();
        verify(vendorRepository, times(2)).findAllById(any());
    }

    @Test
    void differentBrowseTenantIsACacheMiss() {
        search(-25.96512, 32.58321, null, null, 0);
        when(tenantContext.browseTenantId()).thenReturn(Optional.of(TENANT_B));
        when(productRepository.findDistinctVendorIdsByTenantIdAndStatus(TENANT_B, "ACTIVE")).thenReturn(List.of());
        when(vendorRepository.findAllById(List.of())).thenReturn(List.of());

        SearchResponse otherTenant = search(-25.96512, 32.58321, null, null, 0);

        assertThat(otherTenant.vendors()).isEmpty();
        verify(vendorRepository, times(2)).findAllById(any());
    }

    private SearchResponse search(double lat, double lng, Double minRating, Integer maxDeliveryMinutes, int page) {
        return searchService.searchVendorsWithCount(lat, lng, 5_000_000, null, minRating, maxDeliveryMinutes, "RELEVANCE", page);
    }
}
