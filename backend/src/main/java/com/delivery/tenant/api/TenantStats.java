package com.delivery.tenant.api;

import com.delivery.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cross-tenant counts for the platform super-admin dashboard (read-only facade over the tenant repository). */
@Service
public class TenantStats {
    private final TenantRepository tenantRepository;

    public TenantStats(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /** Number of tenants, any status. */
    @Transactional(readOnly = true)
    public long countAll() {
        return tenantRepository.count();
    }

    /** Number of tenants with the given status (e.g. ACTIVE). */
    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        return tenantRepository.countByStatus(status);
    }
}
