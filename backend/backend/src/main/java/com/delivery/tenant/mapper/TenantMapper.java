package com.delivery.tenant.mapper;

import com.delivery.tenant.dto.TenantResponse;
import com.delivery.tenant.entity.Tenant;
import org.springframework.stereotype.Component;

/** Converts tenant entities to DTOs. */
@Component
public class TenantMapper {
    public TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus(),
                tenant.getDefaultCurrency(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }
}
