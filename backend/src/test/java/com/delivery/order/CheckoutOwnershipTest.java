package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.repository.CartRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.inventory.service.InventoryService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import com.delivery.marketing.api.CheckoutDiscountResolver;
import com.delivery.order.service.OrderService;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.service.PaymentService;
import com.delivery.vendor.repository.VendorRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Verifies the real checkout ownership enforcement in OrderService. The controller-slice
 * CheckoutSecurityApiTest mocks OrderService and therefore cannot exercise this rule; the
 * enforcement lives in OrderService.createOrder, so it is tested here at the service layer.
 */
class CheckoutOwnershipTest {
    private final CartRepository cartRepository = mock(CartRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final AppUserProfileRepository userProfileRepository = mock(AppUserProfileRepository.class);
    private final TenantContext tenantContext = mock(TenantContext.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID otherCustomerId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID cartId = UUID.randomUUID();
    private final String keycloakUserId = "caller-keycloak-id";
    private final String idempotencyKey = "key-123";

    private OrderService newService() {
        return new OrderService(
                cartRepository, orderRepository, mock(PaymentRepository.class), mock(DeliveryRepository.class),
                mock(InventoryService.class), userProfileRepository, mock(VendorRepository.class),
                mock(NotificationService.class), mock(PaymentService.class), mock(AuditLogService.class),
                mock(OrderMapper.class), tenantContext, mock(SkuRepository.class), mock(ProductRepository.class),
                mock(CheckoutDiscountResolver.class), mock(com.delivery.delivery.service.DeliveryCodeHasher.class),
                mock(com.delivery.order.service.OrderStockService.class),
                mock(com.delivery.cart.service.CartService.class), mock(org.springframework.transaction.PlatformTransactionManager.class), new com.delivery.order.service.DeliveryAddressResolver(mock(com.delivery.customer.repository.CustomerAddressRepository.class), true), com.delivery.cart.PricingFixtures.platformDefaults(),
                com.delivery.vendor.MerchantScopeFixtures.legacyFallback(tenantContext), mock(com.delivery.delivery.api.DeliveryRetirementService.class), mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    private void stubCaller() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(keycloakUserId));
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(Optional.of(new AppUserProfile(callerId, tenantId, keycloakUserId, "caller@example.com", "Caller", Set.of())));
        when(orderRepository.findByTenantIdAndCheckoutIdempotencyKey(tenantId, idempotencyKey))
                .thenReturn(Optional.empty());
    }

    @Test
    void rejectsCheckoutOfAnotherCustomersCart() {
        stubCaller();
        Cart foreignCart = new Cart(cartId, tenantId, otherCustomerId, vendorId);
        when(cartRepository.lockByTenantIdAndId(tenantId, cartId)).thenReturn(Optional.of(foreignCart));

        BusinessException ex = catchThrowableOfType(
                () -> newService().checkout(cartId, idempotencyKey), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("cart_access_denied");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownersCartPassesTheOwnershipGate() {
        stubCaller();
        // Caller owns this cart; it is empty, so the ownership gate passes and the flow
        // fails later on the invalid-cart check — proving access was NOT denied.
        Cart ownCart = new Cart(cartId, tenantId, callerId, vendorId);
        when(cartRepository.lockByTenantIdAndId(tenantId, cartId)).thenReturn(Optional.of(ownCart));

        assertThatThrownBy(() -> newService().checkout(cartId, idempotencyKey))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isNotEqualTo("cart_access_denied"));
    }
}
