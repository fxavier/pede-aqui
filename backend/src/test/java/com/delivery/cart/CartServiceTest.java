package com.delivery.cart;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.cart.entity.Cart;
import com.delivery.cart.mapper.CartMapper;
import com.delivery.cart.repository.CartRepository;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.repository.InventoryItemRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Test
    void rejectsItemsFromAnotherVendorInActiveCart() {
        UUID firstVendor = UUID.randomUUID();
        UUID secondVendor = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        CartRepository cartRepository = mock(CartRepository.class);
        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE"))
                .thenReturn(Optional.of(new Cart(UUID.randomUUID(), tenantId, customerId, firstVendor)));

        CartService service = service(cartRepository, mock(SkuRepository.class), mock(InventoryItemRepository.class));

        assertThatThrownBy(() -> service.addItem(customerId, secondVendor, skuId, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("MVP carts can contain products from one vendor only");
    }

    @Test
    void rejectsInvalidQuantity() {
        CartService service = service(mock(CartRepository.class), mock(SkuRepository.class), mock(InventoryItemRepository.class));

        assertThatThrownBy(() -> service.addItem(customerId, UUID.randomUUID(), UUID.randomUUID(), 0))
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

        CartService service = service(cartRepository, skuRepository, inventoryRepository);

        assertThatThrownBy(() -> service.addItem(customerId, vendorId, skuId, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Not enough stock is available");
    }

    private CartService service(CartRepository cartRepository, SkuRepository skuRepository, InventoryItemRepository inventoryRepository) {
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        return new CartService(cartRepository, skuRepository, new CartMapper(), new PricingService(), tenantContext, inventoryRepository);
    }
}
