package com.delivery.catalog.service;

import com.delivery.catalog.config.CatalogPriceReviewProperties;
import com.delivery.catalog.dto.PriceUpdateResponse;
import com.delivery.catalog.dto.ProductEditResponse;
import com.delivery.catalog.dto.UpdateProductRequest;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.upload.service.StorageUrlService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Backoffice product management: attribute edits, single-SKU price changes with review, and image linking. */
@Service
public class ProductService {
    static final String ACTION_PRODUCT_UPDATED = "PRODUCT_UPDATED";
    static final String ACTION_PRODUCT_PRICE_UPDATED = "PRODUCT_PRICE_UPDATED";
    static final String ACTION_PRODUCT_PRICE_PENDING = "PRODUCT_PRICE_PENDING";
    static final String ACTION_PRODUCT_IMAGE_SET = "PRODUCT_IMAGE_SET";
    static final String ACTION_PRODUCT_IMAGE_CLEARED = "PRODUCT_IMAGE_CLEARED";
    // audit_logs.business_reference is VARCHAR(120)
    private static final int AUDIT_REFERENCE_MAX_LENGTH = 120;
    // Over-threshold sentinel used when the current price is zero and any change is a full change.
    private static final BigDecimal FULL_CHANGE_PERCENT = new BigDecimal("999999");

    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;
    private final CategoryRepository categoryRepository;
    private final TenantContext tenantContext;
    private final CatalogAccessGuard accessGuard;
    private final AuditLogService auditLogService;
    private final StorageUrlService storageUrlService;
    private final CatalogPriceReviewProperties priceReviewProperties;

    public ProductService(ProductRepository productRepository, SkuRepository skuRepository, CategoryRepository categoryRepository, TenantContext tenantContext, CatalogAccessGuard accessGuard, AuditLogService auditLogService, StorageUrlService storageUrlService, CatalogPriceReviewProperties priceReviewProperties) {
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
        this.categoryRepository = categoryRepository;
        this.tenantContext = tenantContext;
        this.accessGuard = accessGuard;
        this.auditLogService = auditLogService;
        this.storageUrlService = storageUrlService;
        this.priceReviewProperties = priceReviewProperties;
    }

    /** Applies a partial attribute update; ACTIVE products stay ACTIVE and visible. */
    @Transactional
    public ProductEditResponse updateProduct(UUID productId, UpdateProductRequest request) {
        UUID tenantId = tenantId();
        Product product = findProduct(tenantId, productId);
        accessGuard.ensureCanManageVendor(product.getVendorId());

        List<String> diff = new ArrayList<>();
        if (request.name() != null && !request.name().equals(product.getName())) {
            if (request.name().isBlank()) {
                throw new BusinessException("invalid_name", "Product name must not be blank", HttpStatus.BAD_REQUEST);
            }
            diff.add(diffEntry("name", product.getName(), request.name()));
            product.setName(request.name());
        }
        if (request.description() != null && !request.description().equals(product.getDescription())) {
            diff.add(diffEntry("description", product.getDescription(), request.description()));
            product.setDescription(request.description());
        }
        if (request.categoryId() != null && !request.categoryId().equals(product.getCategoryId())) {
            // Category must belong to the caller's tenant (AC-1.5).
            categoryRepository.findByTenantIdAndId(tenantId, request.categoryId())
                    .orElseThrow(() -> new BusinessException("category_not_found", "Category not found in this tenant", HttpStatus.BAD_REQUEST));
            diff.add(diffEntry("categoryId", product.getCategoryId(), request.categoryId()));
            product.setCategoryId(request.categoryId());
        }
        if (request.requiresPrescription() != null && request.requiresPrescription() != product.isRequiresPrescriptionMetadata()) {
            diff.add(diffEntry("requiresPrescription", product.isRequiresPrescriptionMetadata(), request.requiresPrescription()));
            product.setRequiresPrescription(request.requiresPrescription());
        }

        if (!diff.isEmpty()) {
            productRepository.save(product);
            auditLogService.log(ACTION_PRODUCT_UPDATED, "product", productId.toString(), truncate(String.join("; ", diff)), "SUCCESS");
        }
        return toEditResponse(product, singleActiveSkuOrNull(tenantId, productId));
    }

    /** Changes the single-SKU price: applies in place or queues moderation when over the review threshold. */
    @Transactional
    public PriceUpdateResponse updatePrice(UUID productId, BigDecimal newPrice) {
        UUID tenantId = tenantId();
        Product product = findProduct(tenantId, productId);
        accessGuard.ensureCanManageVendor(product.getVendorId());

        Sku sku = resolveSingleActiveSku(tenantId, productId);
        if (sku.hasPendingPrice()) {
            // One pending change at a time (AC-2.4).
            throw new BusinessException("price_change_pending", "A pending price change already exists; wait for approval or rejection", HttpStatus.CONFLICT);
        }

        BigDecimal currentPrice = sku.getPrice();
        BigDecimal deltaPercent = deltaPercent(currentPrice, newPrice);
        boolean reviewRequired = priceReviewProperties.isEnabled()
                && deltaPercent.compareTo(priceReviewProperties.getThresholdPercent()) > 0;

        if (reviewRequired) {
            // Product keeps selling at the approved price; it is never taken offline (AC-2.3).
            String submittedBy = tenantContext.currentKeycloakUserId().orElse("unknown");
            sku.submitPendingPrice(newPrice, submittedBy);
            skuRepository.save(sku);
            auditLogService.log(ACTION_PRODUCT_PRICE_PENDING, "sku", sku.getId().toString(),
                    truncate("price " + currentPrice + " -> pending " + newPrice + " (reviewRequired=true)"), "SUCCESS");
            return new PriceUpdateResponse(sku.getId(), currentPrice, newPrice, true);
        }

        sku.applyPrice(newPrice);
        skuRepository.save(sku);
        auditLogService.log(ACTION_PRODUCT_PRICE_UPDATED, "sku", sku.getId().toString(),
                truncate("price " + currentPrice + " -> " + newPrice + " (reviewRequired=false)"), "SUCCESS");
        return new PriceUpdateResponse(sku.getId(), newPrice, null, false);
    }

    /** Links an uploaded image (presigned flow) as the product's primary image after ownership validation. */
    @Transactional
    public ProductEditResponse setImage(UUID productId, String storageKey) {
        UUID tenantId = tenantId();
        Product product = findProduct(tenantId, productId);
        accessGuard.ensureCanManageVendor(product.getVendorId());

        // The key must come from this tenant's image upload namespace (AC-3.2).
        String expectedPrefix = "tenants/" + tenantId + "/uploads/";
        if (!storageKey.startsWith(expectedPrefix) || storageKey.contains("..")) {
            throw new BusinessException("invalid_storage_key", "storageKey does not belong to this tenant's image namespace", HttpStatus.BAD_REQUEST);
        }

        product.setPrimaryImageKey(storageKey);
        productRepository.save(product);
        auditLogService.log(ACTION_PRODUCT_IMAGE_SET, "product", productId.toString(), truncate(storageKey), "SUCCESS");
        return toEditResponse(product, singleActiveSkuOrNull(tenantId, productId));
    }

    /** Clears the product's primary image. */
    @Transactional
    public void clearImage(UUID productId) {
        UUID tenantId = tenantId();
        Product product = findProduct(tenantId, productId);
        accessGuard.ensureCanManageVendor(product.getVendorId());

        product.setPrimaryImageKey(null);
        productRepository.save(product);
        auditLogService.log(ACTION_PRODUCT_IMAGE_CLEARED, "product", productId.toString(), null, "SUCCESS");
    }

    private Product findProduct(UUID tenantId, UUID productId) {
        return productRepository.findByTenantIdAndId(tenantId, productId)
                .orElseThrow(() -> new BusinessException("product_not_found", "Product not found", HttpStatus.NOT_FOUND));
    }

    /** Guards the single-SKU assumption: zero or multiple active SKUs must be managed at SKU level (AC-2.5). */
    private Sku resolveSingleActiveSku(UUID tenantId, UUID productId) {
        List<Sku> activeSkus = skuRepository.findByTenantIdAndProductIdAndActiveTrue(tenantId, productId);
        if (activeSkus.size() != 1) {
            throw new BusinessException("single_sku_required",
                    "Product has " + activeSkus.size() + " active SKUs; manage prices at SKU level", HttpStatus.CONFLICT);
        }
        return activeSkus.get(0);
    }

    private Sku singleActiveSkuOrNull(UUID tenantId, UUID productId) {
        List<Sku> activeSkus = skuRepository.findByTenantIdAndProductIdAndActiveTrue(tenantId, productId);
        return activeSkus.size() == 1 ? activeSkus.get(0) : null;
    }

    /** |new − current| / current × 100; a change from a zero base counts as a full change. */
    private BigDecimal deltaPercent(BigDecimal current, BigDecimal proposed) {
        if (current == null || current.signum() == 0) {
            return proposed.signum() == 0 ? BigDecimal.ZERO : FULL_CHANGE_PERCENT;
        }
        return proposed.subtract(current).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(current, 4, RoundingMode.HALF_UP);
    }

    private ProductEditResponse toEditResponse(Product product, Sku sku) {
        return new ProductEditResponse(
                product.getId(),
                product.getVendorId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.getStatus(),
                product.isRequiresPrescriptionMetadata(),
                storageUrlService.presignGet(product.getPrimaryImageKey()),
                sku != null ? sku.getPrice() : null,
                sku != null ? sku.getPendingPrice() : null,
                product.getUpdatedAt());
    }

    private String diffEntry(String field, Object before, Object after) {
        return field + ": " + before + " -> " + after;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > AUDIT_REFERENCE_MAX_LENGTH ? value.substring(0, AUDIT_REFERENCE_MAX_LENGTH) : value;
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
