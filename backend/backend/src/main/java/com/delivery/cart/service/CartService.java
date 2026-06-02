package com.delivery.cart.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.dto.CartResponse;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.entity.CartItem;
import com.delivery.cart.mapper.CartMapper;
import com.delivery.cart.repository.CartRepository;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.inventory.repository.InventoryItemRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contains cart rules including one vendor per MVP cart. */
@Service
public class CartService {
    private final CartRepository cartRepository;
    private final SkuRepository skuRepository;
    private final CartMapper mapper;
    private final PricingService pricingService;
    private final TenantContext tenantContext;
    private final InventoryItemRepository inventoryItemRepository;
    private final AppUserProfileRepository userProfileRepository;

    public CartService(CartRepository cartRepository, SkuRepository skuRepository, CartMapper mapper, PricingService pricingService, TenantContext tenantContext, InventoryItemRepository inventoryItemRepository, AppUserProfileRepository userProfileRepository) {
        this.cartRepository = cartRepository;
        this.skuRepository = skuRepository;
        this.mapper = mapper;
        this.pricingService = pricingService;
        this.tenantContext = tenantContext;
        this.inventoryItemRepository = inventoryItemRepository;
        this.userProfileRepository = userProfileRepository;
    }

    /** Adds a SKU to the active cart and rejects mixed-vendor carts. */
    @Transactional
    public CartResponse addItem(UUID vendorId, UUID skuId, int quantity) {
        if (quantity <= 0) throw new BusinessException("invalid_quantity", "Quantity must be positive", HttpStatus.BAD_REQUEST);
        UUID tenantId = tenantId();
        UUID customerId = getCurrentCustomerId();
        Cart cart = cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE")
                .orElseGet(() -> new Cart(UUID.randomUUID(), tenantId, customerId, vendorId));
        if (!cart.getVendorId().equals(vendorId)) {
            throw new BusinessException("single_vendor_cart", "MVP carts can contain products from one vendor only", HttpStatus.CONFLICT);
        }
        Sku sku = skuRepository.findByTenantIdAndId(tenantId, skuId).orElseThrow(() -> new NotFoundException("SKU was not found"));
        inventoryItemRepository.findByTenantIdAndSkuId(tenantId, skuId)
                .filter(item -> item.getQuantityAvailable() >= quantity)
                .orElseThrow(() -> new BusinessException("insufficient_stock", "Not enough stock is available", HttpStatus.CONFLICT));
        CartItem item = new CartItem(UUID.randomUUID(), cart, tenantId, skuId, sku.getName(), sku.getName(), sku.getPrice(), quantity);
        cart.addItem(item);
        var pricing = pricingService.calculate(cart.getSubtotal(), null);
        cart.updateTotals(pricing.deliveryFee().add(pricing.serviceFee()), pricing.tax(), pricing.discount(), pricing.total());
        return mapper.toResponse(cartRepository.save(cart));
    }

    /** Loads a tenant-scoped cart for checkout with customer ownership validation. */
    @Transactional(readOnly = true)
    public Cart getCart(UUID cartId) {
        UUID tenantId = tenantId();
        UUID customerId = getCurrentCustomerId();
        Cart cart = cartRepository.findByTenantIdAndId(tenantId, cartId).orElseThrow(() -> new NotFoundException("Cart was not found"));
        if (!cart.getCustomerId().equals(customerId)) {
            throw new BusinessException("cart_access_denied", "Access to this cart is not permitted", HttpStatus.FORBIDDEN);
        }
        return cart;
    }

    private UUID getCurrentCustomerId() {
        UUID tenantId = tenantId();
        String keycloakUserId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        AppUserProfile currentUser = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
        return currentUser.getId();
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }
}
