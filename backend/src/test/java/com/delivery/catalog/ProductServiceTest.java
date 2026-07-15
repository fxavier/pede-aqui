package com.delivery.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.catalog.config.CatalogPriceReviewProperties;
import com.delivery.catalog.dto.UpdateProductRequest;
import com.delivery.catalog.entity.Category;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.catalog.service.ProductService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.upload.service.StorageUrlService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProductServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private ProductRepository productRepository;
    private SkuRepository skuRepository;
    private CategoryRepository categoryRepository;
    private TenantContext tenantContext;
    private CatalogAccessGuard accessGuard;
    private AuditLogService auditLogService;
    private StorageUrlService storageUrlService;
    private CatalogPriceReviewProperties properties;
    private ProductService service;

    private Product product;
    private Sku sku;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        skuRepository = mock(SkuRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        tenantContext = mock(TenantContext.class);
        accessGuard = mock(CatalogAccessGuard.class);
        auditLogService = mock(AuditLogService.class);
        storageUrlService = mock(StorageUrlService.class);
        properties = new CatalogPriceReviewProperties();
        service = new ProductService(productRepository, skuRepository, categoryRepository, tenantContext, accessGuard, auditLogService, storageUrlService, properties);

        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("vendor-user-1"));

        product = new Product(productId, tenantId, vendorId, UUID.randomUUID(), "Arroz", "Pacote 1kg");
        product.approve(); // ACTIVE
        sku = new Sku(UUID.randomUUID(), tenantId, productId, "SKU-1", "Arroz 1kg", new BigDecimal("100.00"));

        when(productRepository.findByTenantIdAndId(tenantId, productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(skuRepository.findByTenantIdAndProductIdAndActiveTrue(tenantId, productId)).thenReturn(List.of(sku));
        when(skuRepository.save(any(Sku.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- US-2 price-review branch matrix ---

    @Test
    void appliesPriceInPlaceWhenReviewDisabled() {
        properties.setEnabled(false);

        var response = service.updatePrice(productId, new BigDecimal("500.00"));

        assertThat(response.reviewRequired()).isFalse();
        assertThat(response.currentPrice()).isEqualByComparingTo("500.00");
        assertThat(response.pendingPrice()).isNull();
        assertThat(sku.getPrice()).isEqualByComparingTo("500.00");
        assertThat(sku.hasPendingPrice()).isFalse();
        verify(auditLogService).log(eq("PRODUCT_PRICE_UPDATED"), eq("sku"), eq(sku.getId().toString()), any(), eq("SUCCESS"));
    }

    @Test
    void appliesPriceInPlaceWithinThreshold() {
        var response = service.updatePrice(productId, new BigDecimal("110.00")); // 10% <= 20%

        assertThat(response.reviewRequired()).isFalse();
        assertThat(sku.getPrice()).isEqualByComparingTo("110.00");
        assertThat(sku.hasPendingPrice()).isFalse();
    }

    @Test
    void appliesPriceInPlaceAtExactThresholdBoundary() {
        // |Δ%| strictly greater than the threshold triggers review; 20% == 20% applies in place.
        var response = service.updatePrice(productId, new BigDecimal("120.00"));

        assertThat(response.reviewRequired()).isFalse();
        assertThat(sku.getPrice()).isEqualByComparingTo("120.00");
    }

    @Test
    void queuesPendingPriceOverThresholdWithoutTouchingLivePriceOrStatus() {
        var response = service.updatePrice(productId, new BigDecimal("150.00")); // 50% > 20%

        assertThat(response.reviewRequired()).isTrue();
        assertThat(response.currentPrice()).isEqualByComparingTo("100.00");
        assertThat(response.pendingPrice()).isEqualByComparingTo("150.00");
        // Product keeps selling at the approved price and is never taken offline.
        assertThat(sku.getPrice()).isEqualByComparingTo("100.00");
        assertThat(sku.getPendingPrice()).isEqualByComparingTo("150.00");
        assertThat(sku.getPendingPriceSubmittedBy()).isEqualTo("vendor-user-1");
        assertThat(sku.getPendingPriceSubmittedAt()).isNotNull();
        assertThat(product.getStatus()).isEqualTo("ACTIVE");
        verify(auditLogService).log(eq("PRODUCT_PRICE_PENDING"), eq("sku"), eq(sku.getId().toString()), any(), eq("SUCCESS"));
    }

    @Test
    void rejectsSecondPriceEditWhilePendingWith409() {
        sku.submitPendingPrice(new BigDecimal("150.00"), "vendor-user-1");

        assertThatThrownBy(() -> service.updatePrice(productId, new BigDecimal("105.00")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("price_change_pending");
                });
        assertThat(sku.getPendingPrice()).isEqualByComparingTo("150.00");
    }

    @Test
    void rejectsPriceEditWithZeroActiveSkusWith409() {
        when(skuRepository.findByTenantIdAndProductIdAndActiveTrue(tenantId, productId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.updatePrice(productId, new BigDecimal("110.00")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("single_sku_required");
                });
    }

    @Test
    void rejectsPriceEditWithMultipleActiveSkusWith409() {
        Sku second = new Sku(UUID.randomUUID(), tenantId, productId, "SKU-2", "Arroz 5kg", new BigDecimal("450.00"));
        when(skuRepository.findByTenantIdAndProductIdAndActiveTrue(tenantId, productId)).thenReturn(List.of(sku, second));

        assertThatThrownBy(() -> service.updatePrice(productId, new BigDecimal("110.00")))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- tenant & vendor isolation ---

    @Test
    void rejectsProductOutsideTenantWith404() {
        UUID foreignProductId = UUID.randomUUID();
        when(productRepository.findByTenantIdAndId(tenantId, foreignProductId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePrice(foreignProductId, new BigDecimal("110.00")))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void propagatesVendorGuardDenialAsForbidden() {
        doThrow(new BusinessException("vendor_access_denied", "denied", HttpStatus.FORBIDDEN))
                .when(accessGuard).ensureCanManageVendor(vendorId);

        assertThatThrownBy(() -> service.updateProduct(productId, new UpdateProductRequest("Novo", null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(productRepository, never()).save(any());
    }

    // --- US-1 partial update ---

    @Test
    void updatesOnlySuppliedFieldsAndKeepsProductActive() {
        var response = service.updateProduct(productId, new UpdateProductRequest("Arroz Premium", null, null, null));

        assertThat(response.name()).isEqualTo("Arroz Premium");
        assertThat(response.description()).isEqualTo("Pacote 1kg"); // untouched
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.price()).isEqualByComparingTo("100.00");
        verify(auditLogService).log(eq("PRODUCT_UPDATED"), eq("product"), eq(productId.toString()), contains("name"), eq("SUCCESS"));
    }

    @Test
    void updatesPrescriptionFlagWithAuditDiff() {
        var response = service.updateProduct(productId, new UpdateProductRequest(null, null, null, true));

        assertThat(response.requiresPrescription()).isTrue();
        verify(auditLogService).log(eq("PRODUCT_UPDATED"), eq("product"), eq(productId.toString()), contains("requiresPrescription: false -> true"), eq("SUCCESS"));
    }

    @Test
    void noOpUpdateWritesNoAudit() {
        service.updateProduct(productId, new UpdateProductRequest(null, null, null, null));

        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsCategoryFromAnotherTenantWith400() {
        UUID foreignCategoryId = UUID.randomUUID();
        when(categoryRepository.findByTenantIdAndId(tenantId, foreignCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProduct(productId, new UpdateProductRequest(null, null, foreignCategoryId, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("category_not_found");
                });
    }

    @Test
    void updatesCategoryWithinTenant() {
        UUID newCategoryId = UUID.randomUUID();
        when(categoryRepository.findByTenantIdAndId(tenantId, newCategoryId))
                .thenReturn(Optional.of(new Category(newCategoryId, tenantId, "Mercearia", "GROCERY", null)));

        var response = service.updateProduct(productId, new UpdateProductRequest(null, null, newCategoryId, null));

        assertThat(response.categoryId()).isEqualTo(newCategoryId);
    }

    @Test
    void rejectsBlankNameWith400() {
        assertThatThrownBy(() -> service.updateProduct(productId, new UpdateProductRequest("   ", null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- US-3 image ---

    @Test
    void setsImageWhenStorageKeyBelongsToTenantNamespace() {
        String key = "tenants/" + tenantId + "/uploads/product/user/123-foto.jpg";
        when(storageUrlService.presignGet(key)).thenReturn("https://storage/presigned");

        var response = service.setImage(productId, key);

        assertThat(product.getPrimaryImageKey()).isEqualTo(key);
        assertThat(response.imageUrl()).isEqualTo("https://storage/presigned");
        verify(auditLogService).log(eq("PRODUCT_IMAGE_SET"), eq("product"), eq(productId.toString()), any(), eq("SUCCESS"));
    }

    @Test
    void rejectsForeignTenantStorageKeyWith400() {
        String foreignKey = "tenants/" + UUID.randomUUID() + "/uploads/product/user/123-foto.jpg";

        assertThatThrownBy(() -> service.setImage(productId, foreignKey))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("invalid_storage_key");
                });
        assertThat(product.getPrimaryImageKey()).isNull();
    }

    @Test
    void rejectsMalformedStorageKeyWith400() {
        assertThatThrownBy(() -> service.setImage(productId, "not-a-tenant-key.jpg"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.setImage(productId, "tenants/" + tenantId + "/uploads/../../../etc/passwd"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void clearsImageAndAudits() {
        product.setPrimaryImageKey("tenants/" + tenantId + "/uploads/product/user/1-a.jpg");

        service.clearImage(productId);

        assertThat(product.getPrimaryImageKey()).isNull();
        verify(auditLogService).log(eq("PRODUCT_IMAGE_CLEARED"), eq("product"), eq(productId.toString()), any(), eq("SUCCESS"));
    }
}
