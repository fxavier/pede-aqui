package com.delivery.tenant.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.tenant.dto.FeePolicyResponse;
import com.delivery.tenant.dto.UpsertFeePolicyRequest;
import com.delivery.tenant.entity.FeePolicy;
import com.delivery.tenant.repository.FeePolicyRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages tenant fee, tax, commission, and cancellation policy configuration. */
@Service
public class PolicyService {
    private final FeePolicyRepository feePolicyRepository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public PolicyService(FeePolicyRepository feePolicyRepository, TenantContext tenantContext, AuditLogService auditLogService) {
        this.feePolicyRepository = feePolicyRepository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    /** Creates or updates tenant fee policy values; the pricing engine applies them to the next quotes (INC-13). */
    @Transactional
    public FeePolicyResponse upsert(UpsertFeePolicyRequest request) {
        UUID tenantId = tenantId();
        FeePolicy policy = feePolicyRepository.findByTenantId(tenantId)
                .map(existing -> {
                    existing.update(request.deliveryFee(), request.serviceFee(), request.taxRate(), request.commissionRate(), request.cancellationPolicy());
                    return existing;
                })
                .orElseGet(() -> new FeePolicy(UUID.randomUUID(), tenantId, request.deliveryFee(), request.serviceFee(), request.taxRate(), request.commissionRate(), request.cancellationPolicy()));
        if (request.serviceFeeRate() != null) {
            policy.changeServiceFeeRate(request.serviceFeeRate());
        }
        // saveAndFlush so the response carries the bumped version that the next order quotes will record.
        FeePolicyResponse response = toResponse(feePolicyRepository.saveAndFlush(policy));
        // LAC-15: fee-policy changes are audited with the values now in force (no personal data).
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", response.version());
        details.put("deliveryFee", response.deliveryFee());
        details.put("serviceFee", response.serviceFee());
        details.put("serviceFeeRate", response.serviceFeeRate());
        details.put("taxRate", response.taxRate());
        details.put("commissionRate", response.commissionRate());
        details.put("cancellationPolicy", response.cancellationPolicy());
        auditLogService.log(AuditActions.FEE_POLICY_UPDATED, "fee_policy", response.id().toString(), "v" + response.version(), "SUCCESS", details);
        return response;
    }

    /** Returns current tenant policy configuration when available. */
    @Transactional(readOnly = true)
    public FeePolicyResponse get() {
        FeePolicy policy = feePolicyRepository.findByTenantId(tenantId())
                .orElseThrow(() -> new BusinessException("policy_not_found", "Fee policy was not configured", HttpStatus.NOT_FOUND));
        return toResponse(policy);
    }

    private FeePolicyResponse toResponse(FeePolicy policy) {
        return new FeePolicyResponse(policy.getId(), policy.getDeliveryFee(), policy.getServiceFee(), policy.getTaxRate(), policy.getCommissionRate(), policy.getCancellationPolicy(),
                policy.getServiceFeeRate(), policy.getVersion());
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
