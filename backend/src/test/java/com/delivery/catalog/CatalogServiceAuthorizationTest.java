package com.delivery.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.catalog.dto.CreateProductRequest;
import com.delivery.catalog.dto.CreateProductVariationGroupRequest;
import com.delivery.catalog.dto.CreateSkuRequest;
import com.delivery.catalog.dto.ProductResponse;
import com.delivery.catalog.dto.UpdateProductVariationOptionRequest;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.ProductVariationGroup;
import com.delivery.catalog.entity.ProductVariationOption;
import com.delivery.catalog.mapper.CatalogMapper;
import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.ProductVariationGroupRepository;
import com.delivery.catalog.repository.ProductVariationOptionRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.catalog.service.CatalogService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.repository.InventoryItemRepository;
import com.delivery.upload.service.StorageUrlService;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** INC-05 (AS-04): own-vendor scoping, tenant-scoped lookups and moderation audit in CatalogService. */
class CatalogServiceAuthorizationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID ownVendorId = UUID.randomUUID();
    private final UUID otherVendorId = UUID.randomUUID();

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final SkuRepository skuRepository = mock(SkuRepository.class);
    private final InventoryItemRepository inventoryRepository = mock(InventoryItemRepository.class);
    private final ProductVariationGroupRepository groupRepository = mock(ProductVariationGroupRepository.class);
    private final ProductVariationOptionRepository optionRepository = mock(ProductVariationOptionRepository.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private CatalogService service;

    @BeforeEach
    void setUp() {
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(vendorRepository.findByTenantIdAndId(tenantId, ownVendorId)).thenReturn(Optional.of(mock(Vendor.class)));
        when(vendorRepository.findByTenantIdAndId(tenantId, otherVendorId)).thenReturn(Optional.of(mock(Vendor.class)));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(optionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new CatalogService(mock(CategoryRepository.class), productRepository, skuRepository, inventoryRepository,
                groupRepository, optionRepository, new CatalogMapper(mock(StorageUrlService.class)), tenantContext,
                vendorRepository, mock(StorageUrlService.class), new CatalogAccessGuard(), auditLogService);
        authenticate("ROLE_VENDOR_ADMIN", Map.of("vendor_id", ownVendorId.toString()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- createProduct ---

    @Test
    void vendorAdminCannotCreateProductForAnotherVendor() {
        assertThatThrownBy(() -> service.createProduct(productRequest(otherVendorId)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProductRejectsVendorOutsideTenant() {
        UUID foreignVendor = UUID.randomUUID();
        authenticate("ROLE_OPS", Map.of());
        when(vendorRepository.findByTenantIdAndId(tenantId, foreignVendor)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProduct(productRequest(foreignVendor)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(productRepository, never()).save(any());
    }

    @Test
    void vendorAdminCreatesPendingProductForOwnVendor() {
        ProductResponse response = service.createProduct(productRequest(ownVendorId));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.vendorId()).isEqualTo(ownVendorId);
    }

    // --- createSku ---

    @Test
    void createSkuRejectsProductOutsideTenant() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findByTenantIdAndId(tenantId, productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSku(skuRequest(productId, ownVendorId)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(skuRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void vendorAdminCannotCreateSkuOnAnotherVendorsProduct() {
        Product product = product(otherVendorId);

        assertThatThrownBy(() -> service.createSku(skuRequest(product.getId(), otherVendorId)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(skuRepository, never()).save(any());
    }

    @Test
    void createSkuRejectsVendorIdThatDoesNotOwnTheProduct() {
        authenticate("ROLE_OPS", Map.of());
        Product product = product(ownVendorId);

        assertThatThrownBy(() -> service.createSku(skuRequest(product.getId(), otherVendorId)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("vendor_mismatch");
                });
        verify(skuRepository, never()).save(any());
    }

    @Test
    void createSkuSeedsInventoryForProductVendor() {
        Product product = product(ownVendorId);

        service.createSku(skuRequest(product.getId(), ownVendorId));

        ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryRepository).save(captor.capture());
        assertThat(captor.getValue().getVendorId()).isEqualTo(ownVendorId);
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getQuantityAvailable()).isEqualTo(5);
    }

    // --- moderation audit ---

    @Test
    void approveProductIsAudited() {
        authenticate("ROLE_OPS", Map.of());
        Product product = product(ownVendorId);

        ProductResponse response = service.approveProduct(product.getId());

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(auditLogService).log(eq(AuditActions.PRODUCT_APPROVED), eq("product"), eq(product.getId().toString()), anyString(), eq("SUCCESS"));
    }

    @Test
    void rejectProductIsAudited() {
        authenticate("ROLE_OPS", Map.of());
        Product product = product(ownVendorId);

        ProductResponse response = service.rejectProduct(product.getId());

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(auditLogService).log(eq(AuditActions.PRODUCT_REJECTED), eq("product"), eq(product.getId().toString()), anyString(), eq("SUCCESS"));
    }

    @Test
    void approvingNonPendingProductIsNotAudited() {
        authenticate("ROLE_OPS", Map.of());
        Product product = product(ownVendorId);
        product.approve();

        assertThatThrownBy(() -> service.approveProduct(product.getId())).isInstanceOf(BusinessException.class);
        verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    // --- variations ---

    @Test
    void vendorAdminCannotCreateVariationGroupOnAnotherVendorsProduct() {
        Product product = product(otherVendorId);
        CreateProductVariationGroupRequest request = new CreateProductVariationGroupRequest(product.getId(), "Tamanho", null, true, 0, 1, 0);

        assertThatThrownBy(() -> service.createProductVariationGroup(request))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(groupRepository, never()).save(any());
    }

    @Test
    void vendorAdminCannotEditVariationOptionOfAnotherVendor() {
        Product product = product(otherVendorId);
        ProductVariationGroup group = new ProductVariationGroup(UUID.randomUUID(), tenantId, product.getId(), "Tamanho");
        ProductVariationOption option = new ProductVariationOption(UUID.randomUUID(), tenantId, group.getId(), "Grande");
        when(groupRepository.findByTenantIdAndId(tenantId, group.getId())).thenReturn(Optional.of(group));
        when(optionRepository.findByTenantIdAndId(tenantId, option.getId())).thenReturn(Optional.of(option));

        assertThatThrownBy(() -> service.updateProductVariationOption(option.getId(),
                        new UpdateProductVariationOptionRequest("Enorme", null, BigDecimal.ONE, true, 0)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(optionRepository, never()).save(any());
    }

    @Test
    void vendorAdminCanCreateVariationGroupOnOwnProduct() {
        Product product = product(ownVendorId);
        CreateProductVariationGroupRequest request = new CreateProductVariationGroupRequest(product.getId(), "Tamanho", null, true, 0, 1, 0);

        assertThat(service.createProductVariationGroup(request).name()).isEqualTo("Tamanho");
    }

    private Product product(UUID vendorId) {
        Product product = new Product(UUID.randomUUID(), tenantId, vendorId, UUID.randomUUID(), "Arroz", "1kg");
        when(productRepository.findByTenantIdAndId(tenantId, product.getId())).thenReturn(Optional.of(product));
        return product;
    }

    private CreateProductRequest productRequest(UUID vendorId) {
        return new CreateProductRequest(vendorId, UUID.randomUUID(), "Arroz", null, false, false, null, null, null);
    }

    private CreateSkuRequest skuRequest(UUID productId, UUID vendorId) {
        return new CreateSkuRequest(productId, vendorId, "SKU-1", "1kg", new BigDecimal("10.00"), 5);
    }

    private void authenticate(String role, Map<String, String> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        extraClaims.forEach(builder::claim);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority(role))));
    }
}
