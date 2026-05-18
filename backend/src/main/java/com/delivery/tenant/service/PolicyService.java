package com.delivery.tenant.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.tenant.dto.FeePolicyResponse;
import com.delivery.tenant.dto.UpsertFeePolicyRequest;
import com.delivery.tenant.entity.FeePolicy;
import com.delivery.tenant.repository.FeePolicyRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages tenant fee, tax, commission, and cancellation policy configuration. */
@Service
public class PolicyService {
    private final FeePolicyRepository feePolicyRepository;
    private final TenantContext tenantContext;

    public PolicyService(FeePolicyRepository feePolicyRepository, TenantContext tenantContext) {
        this.feePolicyRepository = feePolicyRepository;
        this.tenantContext = tenantContext;
    }

    /** Creates or updates tenant fee policy values. */
    @Transactional
    public FeePolicyResponse upsert(UpsertFeePolicyRequest request) {
        UUID tenantId = tenantId();
        FeePolicy policy = feePolicyRepository.findByTenantId(tenantId)
                .map(existing -> {
                    existing.update(request.deliveryFee(), request.serviceFee(), request.taxRate(), request.commissionRate(), request.cancellationPolicy());
                    return existing;
                })
                .orElseGet(() -> feePolicyRepository.save(new FeePolicy(UUID.randomUUID(), tenantId, request.deliveryFee(), request.serviceFee(), request.taxRate(), request.commissionRate(), request.cancellationPolicy())));
        return toResponse(policy);
    }

    /** Returns current tenant policy configuration when available. */
    @Transactional(readOnly = true)
    public FeePolicyResponse get() {
        FeePolicy policy = feePolicyRepository.findByTenantId(tenantId())
                .orElseThrow(() -> new BusinessException("policy_not_found", "Fee policy was not configured", HttpStatus.NOT_FOUND));
        return toResponse(policy);
    }

    private FeePolicyResponse toResponse(FeePolicy policy) {
        return new FeePolicyResponse(policy.getId(), policy.getDeliveryFee(), policy.getServiceFee(), policy.getTaxRate(), policy.getCommissionRate(), policy.getCancellationPolicy());
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
