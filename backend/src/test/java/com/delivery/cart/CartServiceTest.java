package com.delivery.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.mapper.CartMapper;
import com.delivery.cart.repository.CartRepository;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.CategoryRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.catalog.api.PharmacySalePolicy;
import com.delivery.vendor.api.StoreAvailabilityService;
import com.delivery.vendor.api.StoreClosedException;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.repository.InventoryItemRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class CartServiceTest {
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final StoreAvailabilityService storeAvailability = mock(StoreAvailabilityService.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private PharmacySalePolicy pharmacyPolicy = new PharmacySalePolicy(categoryRepository, false);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final String keycloakUserId = "test-keycloak-user-id";

    @Test
    void rejectsItemsFromAnotherVendorInActiveCart() {
        UUID firstVendor = UUID.randomUUID();
        UUID secondVendor = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        CartRepository cartRepository = mock(CartRepository.class);
        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE"))
                .thenReturn(Optional.of(new Cart(UUID.randomUUID(), tenantId, customerId, firstVendor)));

        CartService service = service(cartRepository, mock(SkuRepository.class), mock(InventoryItemRepository.class));

        assertThatThrownBy(() -> service.addItem(secondVendor, skuId, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("MVP carts can contain products from one vendor only");
    }

    @Test
    void rejectsInvalidQuantity() {
        CartService service = service(mock(CartRepository.class), mock(SkuRepository.class), mock(InventoryItemRepository.class));

        assertThatThrownBy(() -> service.addItem(UUID.randomUUID(), UUID.randomUUID(), 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quantity must be positive");
    }

    @Test
    void rejectsUnavailableStockBeforeAddingItem() {
        UUID vendorId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        CartRepository cartRepository = mock(CartRepository.class);
        SkuRepository skuRepository = mock(SkuRepository.class);
        InventoryItemRepository inventoryRepository = mock(InventoryItemRepository.class);
        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE")).thenReturn(Optional.empty());
        when(skuRepository.findByTenantIdAndId(tenantId, skuId)).thenReturn(Optional.of(new Sku(skuId, tenantId, UUID.randomUUID(), "SKU-1", "Item", new BigDecimal("9.99"))));
        when(inventoryRepository.findByTenantIdAndSkuId(tenantId, skuId)).thenReturn(Optional.of(new InventoryItem(UUID.randomUUID(), tenantId, vendorId, skuId, 1)));
        when(productRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.of(activeProduct(vendorId)));

        CartService service = service(cartRepository, skuRepository, inventoryRepository);

        assertThatThrownBy(() -> service.addItem(vendorId, skuId, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Not enough stock is available");
    }

    // --- INC-05 / COR-05: only ACTIVE products with active SKUs can enter the cart ---

    @Test
    void rejectsSkuOfPendingProduct() {
        UUID vendorId = UUID.randomUUID();
        Product pending = new Product(UUID.randomUUID(), tenantId, vendorId, UUID.randomUUID(), "Arroz", null);
        CartRepository cartRepository = cartFor(vendorId);
        SkuRepository skuRepository = mock(SkuRepository.class);
        Sku sku = new Sku(UUID.randomUUID(), tenantId, pending.getId(), "SKU-1", "1kg", new BigDecimal("9.99"));
        when(skuRepository.findByTenantIdAndId(tenantId, sku.getId())).thenReturn(Optional.of(sku));
        when(productRepository.findByTenantIdAndId(tenantId, pending.getId())).thenReturn(Optional.of(pending));
        InventoryItemRepository inventoryRepository = stocked(vendorId, sku.getId());

        CartService service = service(cartRepository, skuRepository, inventoryRepository);

        assertThatThrownBy(() -> service.addItem(vendorId, sku.getId(), 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("product_not_available");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(cartRepository, never()).save(any());
    }

    @Test
    void rejectsSkuOfRejectedProduct() {
        UUID vendorId = UUID.randomUUID();
        Product rejected = new Product(UUID.randomUUID(), tenantId, vendorId, UUID.randomUUID(), "Arroz", null);
        rejected.reject();
        CartRepository cartRepository = cartFor(vendorId);
        SkuRepository skuRepository = mock(SkuRepository.class);
        Sku sku = new Sku(UUID.randomUUID(), tenantId, rejected.getId(), "SKU-1", "1kg", new BigDecimal("9.99"));
        when(skuRepository.findByTenantIdAndId(tenantId, sku.getId())).thenReturn(Optional.of(sku));
        when(productRepository.findByTenantIdAndId(tenantId, rejected.getId())).thenReturn(Optional.of(rejected));

        CartService service = service(cartRepository, skuRepository, stocked(vendorId, sku.getId()));

        assertThatThrownBy(() -> service.addItem(vendorId, sku.getId(), 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("product_not_available"));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveSku() {
        UUID vendorId = UUID.randomUUID();
        Product product = activeProduct(vendorId);
        CartRepository cartRepository = cartFor(vendorId);
        SkuRepository skuRepository = mock(SkuRepository.class);
        Sku sku = new Sku(UUID.randomUUID(), tenantId, product.getId(), "SKU-1", "1kg", new BigDecimal("9.99"));
        ReflectionTestUtils.setField(sku, "active", false);
        when(skuRepository.findByTenantIdAndId(tenantId, sku.getId())).thenReturn(Optional.of(sku));
        when(productRepository.findByTenantIdAndId(tenantId, product.getId())).thenReturn(Optional.of(product));

        CartService service = service(cartRepository, skuRepository, stocked(vendorId, sku.getId()));

        assertThatThrownBy(() -> service.addItem(vendorId, sku.getId(), 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("sku_inactive");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addsActiveSkuOfActiveProduct() {
        UUID vendorId = UUID.randomUUID();
        Product product = activeProduct(vendorId);
        CartRepository cartRepository = cartFor(vendorId);
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SkuRepository skuRepository = mock(SkuRepository.class);
        Sku sku = new Sku(UUID.randomUUID(), tenantId, product.getId(), "SKU-1", "1kg", new BigDecimal("9.99"));
        when(skuRepository.findByTenantIdAndId(tenantId, sku.getId())).thenReturn(Optional.of(sku));
        when(productRepository.findByTenantIdAndId(tenantId, product.getId())).thenReturn(Optional.of(product));

        CartService service = service(cartRepository, skuRepository, stocked(vendorId, sku.getId()));

        assertThat(service.addItem(vendorId, sku.getId(), 1).items()).hasSize(1);
    }

    // --- W1-D cart bug: the SKU must belong to the vendor named in the request ---

    @Test
    void rejectsSkuSoldByAnotherVendor() {
        UUID requestVendor = UUID.randomUUID();
        Product otherVendorsProduct = activeProduct(UUID.randomUUID());
        CartRepository cartRepository = cartFor(requestVendor);
        SkuRepository skuRepository = skuOf(otherVendorsProduct);

        CartService service = service(cartRepository, skuRepository, stocked(requestVendor, skuIdOf(skuRepository)));

        assertThatThrownBy(() -> service.addItem(requestVendor, skuIdOf(skuRepository), 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("sku_vendor_mismatch");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
        verify(cartRepository, never()).save(any());
    }

    // --- LAC-27: closed stores do not accept items ---

    @Test
    void rejectsItemWhenTheStoreIsClosed() {
        UUID vendorId = UUID.randomUUID();
        Product product = activeProduct(vendorId);
        CartRepository cartRepository = cartFor(vendorId);
        SkuRepository skuRepository = skuOf(product);
        org.mockito.Mockito.doThrow(new StoreClosedException(vendorId, null)).when(storeAvailability).requireOpen(vendorId);

        CartService service = service(cartRepository, skuRepository, stocked(vendorId, skuIdOf(skuRepository)));

        assertThatThrownBy(() -> service.addItem(vendorId, skuIdOf(skuRepository), 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("store_closed");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
        verify(cartRepository, never()).save(any());
    }

    // --- LAC-64: pharmacy vertical blocked by configuration ---

    @Test
    void rejectsPrescriptionProductWhilePharmacyIsDisabled() {
        UUID vendorId = UUID.randomUUID();
        Product product = activeProduct(vendorId);
        product.markPrescriptionRequired();

        assertPharmacyRejection(vendorId, product, "pharmacy_not_available");
    }

    @Test
    void rejectsProductOfThePharmacyVerticalWhilePharmacyIsDisabled() {
        UUID vendorId = UUID.randomUUID();
        Product product = activeProduct(vendorId);
        when(categoryRepository.findById(product.getCategoryId()))
                .thenReturn(Optional.of(new com.delivery.catalog.entity.Category(product.getCategoryId(), tenantId, "Medicamentos", "pharmacy")));

        assertPharmacyRejection(vendorId, product, "pharmacy_not_available");
    }

    @Test
    void prescriptionProductsStayUnsellableWhenPharmacyIsEnabled() {
        pharmacyPolicy = new PharmacySalePolicy(categoryRepository, true);
        UUID vendorId = UUID.randomUUID();
        Product product = activeProduct(vendorId);
        product.markPrescriptionRequired();

        assertPharmacyRejection(vendorId, product, "prescription_required");
    }

    @Test
    void nonPrescriptionPharmacyProductIsSellableWhenPharmacyIsEnabled() {
        pharmacyPolicy = new PharmacySalePolicy(categoryRepository, true);
        UUID vendorId = UUID.randomUUID();
        Product product = activeProduct(vendorId);
        when(categoryRepository.findById(product.getCategoryId()))
                .thenReturn(Optional.of(new com.delivery.catalog.entity.Category(product.getCategoryId(), tenantId, "Higiene", "pharmacy")));
        CartRepository cartRepository = cartFor(vendorId);
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SkuRepository skuRepository = skuOf(product);

        CartService service = service(cartRepository, skuRepository, stocked(vendorId, skuIdOf(skuRepository)));

        assertThat(service.addItem(vendorId, skuIdOf(skuRepository), 1).items()).hasSize(1);
    }

    @Test
    void checkoutEligibilityRejectsAClosedStoreAndAPharmacyProduct() {
        UUID vendorId = UUID.randomUUID();
        Product product = activeProduct(vendorId);
        SkuRepository skuRepository = skuOf(product);
        Cart cart = new Cart(UUID.randomUUID(), tenantId, customerId, vendorId);
        cart.addItem(new com.delivery.cart.entity.CartItem(UUID.randomUUID(), cart, tenantId, skuIdOf(skuRepository), "1kg", "1kg", new BigDecimal("9.99"), 1));
        CartService service = service(mock(CartRepository.class), skuRepository, mock(InventoryItemRepository.class));

        service.requireCheckoutEligible(tenantId, cart); // open store, ordinary product: no exception

        product.markPrescriptionRequired();
        assertThatThrownBy(() -> service.requireCheckoutEligible(tenantId, cart))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("pharmacy_not_available"));

        org.mockito.Mockito.doThrow(new StoreClosedException(vendorId, null)).when(storeAvailability).requireOpen(vendorId);
        assertThatThrownBy(() -> service.requireCheckoutEligible(tenantId, cart))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("store_closed"));
    }

    private void assertPharmacyRejection(UUID vendorId, Product product, String expectedCode) {
        CartRepository cartRepository = cartFor(vendorId);
        SkuRepository skuRepository = skuOf(product);
        CartService service = service(cartRepository, skuRepository, stocked(vendorId, skuIdOf(skuRepository)));

        assertThatThrownBy(() -> service.addItem(vendorId, skuIdOf(skuRepository), 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(expectedCode);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
        verify(cartRepository, never()).save(any());
    }

    private final java.util.Map<SkuRepository, UUID> skuIds = new java.util.IdentityHashMap<>();

    private SkuRepository skuOf(Product product) {
        SkuRepository skuRepository = mock(SkuRepository.class);
        Sku sku = new Sku(UUID.randomUUID(), tenantId, product.getId(), "SKU-1", "1kg", new BigDecimal("9.99"));
        when(skuRepository.findByTenantIdAndId(tenantId, sku.getId())).thenReturn(Optional.of(sku));
        when(productRepository.findByTenantIdAndId(tenantId, product.getId())).thenReturn(Optional.of(product));
        skuIds.put(skuRepository, sku.getId());
        return skuRepository;
    }

    private UUID skuIdOf(SkuRepository skuRepository) {
        return skuIds.get(skuRepository);
    }

    private Product activeProduct(UUID vendorId) {
        Product product = new Product(UUID.randomUUID(), tenantId, vendorId, UUID.randomUUID(), "Arroz", null);
        product.approve();
        return product;
    }

    private CartRepository cartFor(UUID vendorId) {
        CartRepository cartRepository = mock(CartRepository.class);
        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE"))
                .thenReturn(Optional.of(new Cart(UUID.randomUUID(), tenantId, customerId, vendorId)));
        return cartRepository;
    }

    private InventoryItemRepository stocked(UUID vendorId, UUID skuId) {
        InventoryItemRepository inventoryRepository = mock(InventoryItemRepository.class);
        when(inventoryRepository.findByTenantIdAndSkuId(tenantId, skuId)).thenReturn(Optional.of(new InventoryItem(UUID.randomUUID(), tenantId, vendorId, skuId, 10)));
        return inventoryRepository;
    }

    private CartService service(CartRepository cartRepository, SkuRepository skuRepository, InventoryItemRepository inventoryRepository) {
        TenantContext tenantContext = mock(TenantContext.class);
        AppUserProfileRepository userProfileRepository = mock(AppUserProfileRepository.class);
        AppUserProfile userProfile = new AppUserProfile(customerId, tenantId, keycloakUserId, "test@example.com", "Test User", java.util.Set.of());
        
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(keycloakUserId));
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)).thenReturn(Optional.of(userProfile));
        
        return new CartService(cartRepository, skuRepository, productRepository, new CartMapper(), com.delivery.cart.PricingFixtures.platformDefaults(), tenantContext, inventoryRepository, userProfileRepository, storeAvailability, pharmacyPolicy);
    }
}
