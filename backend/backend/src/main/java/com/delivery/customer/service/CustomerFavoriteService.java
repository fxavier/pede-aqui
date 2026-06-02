package com.delivery.customer.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.customer.entity.CustomerFavoriteVendor;
import com.delivery.customer.repository.CustomerFavoriteVendorRepository;
import com.delivery.geo.dto.SearchVendorResponse;
import com.delivery.geo.service.SearchService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Service for managing customer favorite vendors. */
@Service
public class CustomerFavoriteService {
    
    private final CustomerFavoriteVendorRepository favoriteRepository;
    private final TenantContext tenantContext;
    private final SearchService searchService;

    public CustomerFavoriteService(CustomerFavoriteVendorRepository favoriteRepository, 
                                   TenantContext tenantContext,
                                   SearchService searchService) {
        this.favoriteRepository = favoriteRepository;
        this.tenantContext = tenantContext;
        this.searchService = searchService;
    }

    /** Add a vendor to customer's favorites. */
    @Transactional
    @CacheEvict(value = "vendorSearch", allEntries = true)
    public void addFavoriteVendor(UUID customerId, UUID vendorId) {
        UUID tenantId = getTenantId();
        
        // Check if already exists
        if (favoriteRepository.findByTenantIdAndCustomerIdAndVendorId(tenantId, customerId, vendorId).isPresent()) {
            return; // Already favorited, no action needed
        }
        
        CustomerFavoriteVendor favorite = new CustomerFavoriteVendor(tenantId, customerId, vendorId);
        favoriteRepository.save(favorite);
    }

    /** Remove a vendor from customer's favorites. */
    @Transactional
    @CacheEvict(value = "vendorSearch", allEntries = true)
    public void removeFavoriteVendor(UUID customerId, UUID vendorId) {
        UUID tenantId = getTenantId();
        favoriteRepository.deleteByTenantIdAndCustomerIdAndVendorId(tenantId, customerId, vendorId);
    }

    /** Check if a vendor is favorited by customer. */
    public boolean isFavoriteVendor(UUID customerId, UUID vendorId) {
        UUID tenantId = getTenantId();
        return favoriteRepository.findByTenantIdAndCustomerIdAndVendorId(tenantId, customerId, vendorId).isPresent();
    }

    /** Get customer's favorite vendors with search data. */
    public List<SearchVendorResponse> getFavoriteVendors(UUID customerId, Double latitude, Double longitude) {
        UUID tenantId = getTenantId();
        
        List<UUID> favoriteVendorIds = favoriteRepository.findVendorIdsByTenantIdAndCustomerId(tenantId, customerId);
        
        if (favoriteVendorIds.isEmpty()) {
            return List.of();
        }

        // Get search results for all vendors and filter to favorites
        List<SearchVendorResponse> allVendors = searchService.searchVendors(latitude, longitude, null, null, null, null, "RELEVANCE", 0);
        Set<UUID> favoriteIds = Set.copyOf(favoriteVendorIds);
        
        return allVendors.stream()
                .filter(vendor -> favoriteIds.contains(vendor.vendorId()))
                .collect(Collectors.toList());
    }

    /** Get list of favorite vendor IDs for a customer. */
    public List<UUID> getFavoriteVendorIds(UUID customerId) {
        UUID tenantId = getTenantId();
        return favoriteRepository.findVendorIdsByTenantIdAndCustomerId(tenantId, customerId);
    }

    private UUID getTenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}