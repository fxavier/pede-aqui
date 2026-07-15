package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.entity.CartItem;
import com.delivery.cart.repository.CartRepository;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.inventory.service.InventoryService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.service.CheckoutDiscount;
import com.delivery.order.service.CheckoutDiscountResolver;
import com.delivery.order.service.OrderService;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.service.PaymentService;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/**
 * D9 — checkout regression: idempotency and single-save flow intact, discounted totals reconcile
 * with the canonical formula, and legacy carts keep discount_total = 0 with unchanged totals.
 */
class CheckoutDiscountRegressionTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID cartId = UUID.randomUUID();
    private final UUID skuId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final String keycloakUserId = "kc-user";

    private final CartRepository cartRepository = mock(CartRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final AppUserProfileRepository userProfileRepository = mock(AppUserProfileRepository.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final SkuRepository skuRepository = mock(SkuRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CheckoutDiscountResolver discountResolver = mock(CheckoutDiscountResolver.class);

    private final OrderService service = new OrderService(cartRepository, orderRepository, paymentRepository,
            deliveryRepository, inventoryService, userProfileRepository, vendorRepository, notificationService,
            paymentService, auditLogService, mapper, tenantContext, skuRepository, productRepository, discountResolver);

    private Cart cart;

    @BeforeEach
    void setUp() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(keycloakUserId));
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(Optional.of(new AppUserProfile(customerId, tenantId, keycloakUserId, "c@x.mz", "Customer", Set.of())));

        cart = new Cart(cartId, tenantId, customerId, vendorId);
        cart.addItem(new CartItem(UUID.randomUUID(), cart, tenantId, skuId, "P1", "P1", new BigDecimal("50.00"), 2));
        // subtotal 100.00, fees 10.00, taxes 8.00 → undiscounted total 118.00
        cart.updateTotals(new BigDecimal("10.00"), new BigDecimal("8.00"), BigDecimal.ZERO, new BigDecimal("118.00"));
        when(cartRepository.findByTenantIdAndId(tenantId, cartId)).thenReturn(Optional.of(cart));

        when(orderRepository.findByTenantIdAndCheckoutIdempotencyKey(any(), anyString())).thenReturn(Optional.empty());
        // A non-null mapped response is required: checkout relies on Optional.map over it for the idempotent path.
        when(mapper.toResponse(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            return new com.delivery.order.dto.OrderResponse(order.getId(), order.getReference(), order.getStatus(),
                    order.getTotal(), null, null, null, order.getCreatedAt(), List.of());
        });
        when(orderRepository.existsByTenantIdAndDeliveryConfirmationCodeHash(any(), anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(skuRepository.findAllById(anyCollection()))
                .thenReturn(List.of(new Sku(skuId, tenantId, productId, "SKU-1", "P1", new BigDecimal("50.00"))));
        when(productRepository.findAllById(anyCollection()))
                .thenReturn(List.of(new Product(productId, tenantId, vendorId, categoryId, "P1", null)));
    }

    @Test
    void legacyCheckoutWithoutPromotionKeepsZeroDiscountAndUnchangedTotal() {
        when(discountResolver.resolve(any(Cart.class), any(UUID.class))).thenReturn(CheckoutDiscount.none());

        service.checkout(cartId, "idem-1");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(orderCaptor.capture());
        Order order = orderCaptor.getValue();
        assertThat(order.getDiscountTotal()).isEqualByComparingTo("0");
        assertThat(order.getAppliedPromotionId()).isNull();
        assertThat(order.getTotal()).isEqualByComparingTo("118.00");
        assertThat(cart.getStatus()).isEqualTo("CHECKED_OUT");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("118.00");
    }

    @Test
    void discountedCheckoutReconcilesTotalsWithCanonicalFormula() {
        UUID promotionId = UUID.randomUUID();
        when(discountResolver.resolve(any(Cart.class), any(UUID.class)))
                .thenReturn(new CheckoutDiscount(promotionId, new BigDecimal("10.00")));

        service.checkout(cartId, "idem-2");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order order = orderCaptor.getValue();
        assertThat(order.getAppliedPromotionId()).isEqualTo(promotionId);
        assertThat(order.getDiscountTotal()).isEqualByComparingTo("10.00");
        // total = subtotal + fees + taxes − discount = 100 + 10 + 8 − 10
        assertThat(order.getTotal()).isEqualByComparingTo("108.00");
        assertThat(order.getSubtotal().add(order.getFees()).add(order.getTaxes()).subtract(order.getDiscountTotal()))
                .isEqualByComparingTo(order.getTotal());
        // The category snapshot (A7) is populated at order creation for CATEGORY-scoped reporting.
        assertThat(order.getItems()).extracting(OrderItem::getCategoryIdSnapshot).containsExactly(categoryId);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("108.00");
    }

    @Test
    void idempotentRetryReturnsExistingOrderWithoutReResolvingDiscount() {
        Order existing = new Order(UUID.randomUUID(), tenantId, "PA-1", customerId, vendorId,
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("118.00"), "idem-3", "hash", "123456");
        when(orderRepository.findByTenantIdAndCheckoutIdempotencyKey(tenantId, "idem-3")).thenReturn(Optional.of(existing));

        service.checkout(cartId, "idem-3");

        verify(discountResolver, never()).resolve(any(), any());
        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void discountResolutionFailureAbortsCheckoutBeforePersistingTheOrder() {
        when(discountResolver.resolve(any(Cart.class), any(UUID.class)))
                .thenThrow(new BusinessException("promotion_usage_exhausted", "Promotion usage limit has been reached",
                        HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> service.checkout(cartId, "idem-4"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "promotion_usage_exhausted");

        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        assertThat(cart.getStatus()).isEqualTo("ACTIVE");
    }
}
