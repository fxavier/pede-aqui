package com.delivery.auth.service;

import com.delivery.auth.dto.MeResponse;
import com.delivery.auth.mapper.AppUserProfileMapper;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains profile lookup logic for authenticated users. */
@Service
public class AppUserProfileService {
    private final AppUserProfileRepository repository;
    private final AppUserProfileMapper mapper;
    private final TenantContext tenantContext;

    public AppUserProfileService(AppUserProfileRepository repository, AppUserProfileMapper mapper, TenantContext tenantContext) {
        this.repository = repository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    /** Returns the current user's tenant-scoped profile. */
    @Transactional(readOnly = true)
    public MeResponse getCurrentUserProfile() {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new NotFoundException("Tenant context was not found"));
        String keycloakUserId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new NotFoundException("Authenticated user was not found"));
        return repository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .map(mapper::toMeResponse)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
    }
}
