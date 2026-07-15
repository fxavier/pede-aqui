package com.delivery.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.catalog.service.PriceModerationService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PriceModerationServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private SkuRepository skuRepository;
    private ProductRepository productRepository;
    private AuditLogService auditLogService;
    private PriceModerationService service;

    private Product product;
    private Sku sku;

    @BeforeEach
    void setUp() {
        skuRepository = mock(SkuRepository.class);
        productRepository = mock(ProductRepository.class);
        auditLogService = mock(AuditLogService.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        service = new PriceModerationService(skuRepository, productRepository, tenantContext, auditLogService);

        product = new Product(productId, tenantId, vendorId, UUID.randomUUID(), "Arroz", "Pacote 1kg");
        product.approve();
        sku = new Sku(UUID.randomUUID(), tenantId, productId, "SKU-1", "Arroz 1kg", new BigDecimal("100.00"));
        sku.submitPendingPrice(new BigDecimal("150.00"), "vendor-user-1");

        when(skuRepository.findByTenantIdAndId(tenantId, sku.getId())).thenReturn(Optional.of(sku));
        when(skuRepository.save(any(Sku.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listsPendingChangesWithDeltaAndSubmitter() {
        when(skuRepository.findByTenantIdAndPendingPriceIsNotNull(tenantId)).thenReturn(List.of(sku));
        when(productRepository.findAllById(List.of(productId))).thenReturn(List.of(product));

        var rows = service.listPending();

        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.skuId()).isEqualTo(sku.getId());
        assertThat(row.productId()).isEqualTo(productId);
        assertThat(row.productName()).isEqualTo("Arroz");
        assertThat(row.vendorId()).isEqualTo(vendorId);
        assertThat(row.currentPrice()).isEqualByComparingTo("100.00");
        assertThat(row.pendingPrice()).isEqualByComparingTo("150.00");
        assertThat(row.deltaPercent()).isEqualByComparingTo("50");
        assertThat(row.submittedBy()).isEqualTo("vendor-user-1");
        assertThat(row.submittedAt()).isNotNull();
    }

    @Test
    void approvePromotesPendingPriceAndClearsSlot() {
        service.approve(sku.getId());

        assertThat(sku.getPrice()).isEqualByComparingTo("150.00");
        assertThat(sku.hasPendingPrice()).isFalse();
        assertThat(sku.getPendingPriceSubmittedAt()).isNull();
        assertThat(sku.getPendingPriceSubmittedBy()).isNull();
        // Product status is not part of price moderation; it stays ACTIVE.
        assertThat(product.getStatus()).isEqualTo("ACTIVE");
        verify(auditLogService).log(eq("PRODUCT_PRICE_APPROVED"), eq("sku"), eq(sku.getId().toString()), contains("100.00 -> 150.00"), eq("SUCCESS"));
    }

    @Test
    void rejectClearsSlotWithoutChangingLivePrice() {
        service.reject(sku.getId(), "Aumento excessivo");

        assertThat(sku.getPrice()).isEqualByComparingTo("100.00");
        assertThat(sku.hasPendingPrice()).isFalse();
        verify(auditLogService).log(eq("PRODUCT_PRICE_REJECTED"), eq("sku"), eq(sku.getId().toString()), contains("Aumento excessivo"), eq("SUCCESS"));
    }

    @Test
    void approveWithoutPendingChangeReturns404() {
        sku.clearPendingPrice();

        assertThatThrownBy(() -> service.approve(sku.getId()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCode()).isEqualTo("pending_price_not_found");
                });
    }

    @Test
    void skuOutsideTenantReturns404() {
        UUID foreignSkuId = UUID.randomUUID();
        when(skuRepository.findByTenantIdAndId(tenantId, foreignSkuId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(foreignSkuId))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
