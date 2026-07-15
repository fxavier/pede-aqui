package com.delivery.catalog.service;

import com.delivery.catalog.dto.PendingPriceChangeResponse;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OPS/ADMIN moderation of over-threshold price changes: list, approve, or reject pending prices. */
@Service
public class PriceModerationService {
    // audit_logs.business_reference is VARCHAR(120)
    private static final int AUDIT_REFERENCE_MAX_LENGTH = 120;

    private final SkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public PriceModerationService(SkuRepository skuRepository, ProductRepository productRepository, TenantContext tenantContext, AuditLogService auditLogService) {
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    /** Lists tenant-scoped SKUs awaiting price moderation with the computed delta. */
    @Transactional(readOnly = true)
    public List<PendingPriceChangeResponse> listPending() {
        UUID tenantId = tenantId();
        List<Sku> pending = skuRepository.findByTenantIdAndPendingPriceIsNotNull(tenantId);
        List<UUID> productIds = pending.stream().map(Sku::getProductId).distinct().toList();
        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
                .filter(product -> tenantId.equals(product.getTenantId()))
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return pending.stream().map(sku -> {
            Product product = products.get(sku.getProductId());
            return new PendingPriceChangeResponse(
                    sku.getId(),
                    sku.getProductId(),
                    product != null ? product.getName() : null,
                    product != null ? product.getVendorId() : null,
                    sku.getPrice(),
                    sku.getPendingPrice(),
                    deltaPercent(sku.getPrice(), sku.getPendingPrice()),
                    sku.getPendingPriceSubmittedBy(),
                    sku.getPendingPriceSubmittedAt());
        }).toList();
    }

    /** Approves a pending change: price = pending_price, slot cleared; the product stays ACTIVE. */
    @Transactional
    public void approve(UUID skuId) {
        Sku sku = findPendingSku(skuId);
        BigDecimal oldPrice = sku.getPrice();
        BigDecimal newPrice = sku.getPendingPrice();
        sku.approvePendingPrice();
        skuRepository.save(sku);
        auditLogService.log(AuditActions.PRODUCT_PRICE_APPROVED, "sku", skuId.toString(),
                truncate("price " + oldPrice + " -> " + newPrice), "SUCCESS");
    }

    /** Rejects a pending change: slot cleared, live price untouched; reason is recorded in the audit trail. */
    @Transactional
    public void reject(UUID skuId, String reason) {
        Sku sku = findPendingSku(skuId);
        BigDecimal rejectedPrice = sku.getPendingPrice();
        sku.clearPendingPrice();
        skuRepository.save(sku);
        auditLogService.log(AuditActions.PRODUCT_PRICE_REJECTED, "sku", skuId.toString(),
                truncate("rejected " + rejectedPrice + ": " + reason), "SUCCESS");
    }

    private Sku findPendingSku(UUID skuId) {
        Sku sku = skuRepository.findByTenantIdAndId(tenantId(), skuId)
                .orElseThrow(() -> new BusinessException("sku_not_found", "SKU not found", HttpStatus.NOT_FOUND));
        if (!sku.hasPendingPrice()) {
            throw new BusinessException("pending_price_not_found", "SKU has no pending price change", HttpStatus.NOT_FOUND);
        }
        return sku;
    }

    private BigDecimal deltaPercent(BigDecimal current, BigDecimal proposed) {
        if (current == null || current.signum() == 0) {
            return null;
        }
        return proposed.subtract(current).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(current, 4, RoundingMode.HALF_UP);
    }

    private String truncate(String value) {
        return value != null && value.length() > AUDIT_REFERENCE_MAX_LENGTH ? value.substring(0, AUDIT_REFERENCE_MAX_LENGTH) : value;
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
