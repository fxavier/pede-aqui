package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.repository.CartRepository;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.inventory.service.InventoryService;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import com.delivery.marketing.api.CheckoutDiscount;
import com.delivery.order.service.OrderService;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.vendor.repository.VendorRepository;
import com.delivery.notification.service.NotificationService;
import com.delivery.payment.service.PaymentService;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.RefundStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VendorFulfillmentTest {
    private final com.delivery.order.service.OrderStockService orderStockService = mock(com.delivery.order.service.OrderStockService.class);
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void acceptsPaymentConfirmedOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = paymentConfirmedOrder(orderId);
        OrderRepository repository = repositoryWith(orderId, order);
        AuditLogService auditLogService = mock(AuditLogService.class);
        OrderService service = serviceWithAuditService(repository, mock(PaymentRepository.class), auditLogService);

        var response = service.acceptByVendor(orderId);

        assertThat(response.status()).isEqualTo(OrderStatus.ACCEPTED_BY_VENDOR);
        verify(auditLogService).log("ORDER_ACCEPTED", "order", orderId.toString(), order.getReference(), "SUCCESS");
    }

    @Test
    void rejectsOrderWithReasonAsSellerRejectedCancellationAndRefundsTheRemainder() {
        UUID orderId = UUID.randomUUID();
        Order order = paymentConfirmedOrder(orderId);
        OrderRepository orderRepository = repositoryWith(orderId, order);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment payment = mock(Payment.class);
        UUID paymentId = UUID.randomUUID();
        when(payment.getId()).thenReturn(paymentId);
        when(payment.getAmount()).thenReturn(new BigDecimal("115.00"));
        when(paymentRepository.findByTenantIdAndOrderIdForUpdate(tenantId, orderId)).thenReturn(Optional.of(payment));
        AuditLogService auditLogService = mock(AuditLogService.class);
        PaymentService paymentService = mock(PaymentService.class);
        when(paymentService.requestRemainingRefund(paymentId, "Vendor rejection: Sem ingredientes", "seller-reject-" + orderId))
                .thenReturn(Optional.of(new RefundResponse(UUID.randomUUID(), paymentId, orderId, new BigDecimal("115.00"), "Vendor rejection: Sem ingredientes", RefundStatus.REQUESTED)));

        OrderService service = serviceWithAuditService(orderRepository, paymentRepository, auditLogService, paymentService);

        var response = service.rejectByVendor(orderId, "Sem ingredientes");

        // Unified seller rejection: every rejection ends CANCELLED + SELLER_REJECTED; the financial state lives on the payment.
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo(CancellationReason.SELLER_REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.SELLER_REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Sem ingredientes");
        assertThat(order.getDeliveryConfirmationCodeDisplay()).isNull(); // PIN cleared like any cancellation
        verify(auditLogService).log("ORDER_CANCELLED_SELLER_REJECTED", "order", orderId.toString(), "refund_remaining=115.00", "SUCCESS");
        verify(paymentService, never()).requestRefund(any(), any()); // no legacy vendor-rejection-<id> refund
        verify(orderStockService).release(order); // INC-07 (COR-01)
    }

    @Test
    void repeatedRejectionOfACancelledOrLegacyRejectedOrderReturnsCurrentStateWithoutSideEffects() {
        for (Order order : List.of(cancelledByOps(), legacyRejected())) {
            UUID orderId = order.getId();
            OrderRepository orderRepository = repositoryWith(orderId, order);
            PaymentRepository paymentRepository = mock(PaymentRepository.class);
            AuditLogService auditLogService = mock(AuditLogService.class);
            PaymentService paymentService = mock(PaymentService.class);
            OrderStatus before = order.getStatus();
            OrderService service = serviceWithAuditService(orderRepository, paymentRepository, auditLogService, paymentService);

            var response = service.rejectByVendor(orderId, "Outro motivo");

            assertThat(response.status()).isEqualTo(before);
            assertThat(order.getStatus()).isEqualTo(before);
            assertThat(order.getRejectionReason()).isNotEqualTo("Outro motivo");
            verifyNoInteractions(paymentService, auditLogService);
            verify(orderStockService, never()).release(order);
        }
    }

    private Order cancelledByOps() {
        Order order = paymentConfirmedOrder(UUID.randomUUID());
        order.markCancelled();
        return order;
    }

    private Order legacyRejected() {
        Order order = paymentConfirmedOrder(UUID.randomUUID());
        ReflectionTestUtils.setField(order, "status", OrderStatus.REJECTED_BY_VENDOR);
        ReflectionTestUtils.setField(order, "rejectionReason", "Sem stock");
        return order;
    }

    @Test
    void movesAcceptedOrderToPreparingAndReadyForPickup() {
        UUID orderId = UUID.randomUUID();
        Order order = paymentConfirmedOrder(orderId);
        order.markAcceptedByVendor();
        OrderRepository repository = repositoryWith(orderId, order);
        AuditLogService auditLogService = mock(AuditLogService.class);
        OrderService service = serviceWithAuditService(repository, mock(PaymentRepository.class), auditLogService);

        var preparing = service.markPreparing(orderId);
        var ready = service.markReadyForPickup(orderId);

        assertThat(preparing.status()).isEqualTo(OrderStatus.PREPARING);
        assertThat(ready.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        verify(auditLogService).log("ORDER_PREPARING", "order", orderId.toString(), order.getReference(), "SUCCESS");
        verify(auditLogService).log("ORDER_READY_FOR_PICKUP", "order", orderId.toString(), order.getReference(), "SUCCESS");
    }

    @Test
    void blocksInvalidTransitionToPreparing() {
        UUID orderId = UUID.randomUUID();
        OrderRepository repository = repositoryWith(orderId, paymentConfirmedOrder(orderId));
        OrderService service = service(repository);

        assertThatThrownBy(() -> service.markPreparing(orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only accepted orders can move to preparing");
    }

    private OrderService service(OrderRepository orderRepository) {
        return serviceWithAuditService(orderRepository, mock(PaymentRepository.class), mock(AuditLogService.class));
    }

    private OrderService serviceWithPaymentRepo(OrderRepository orderRepository, PaymentRepository paymentRepository) {
        return serviceWithAuditService(orderRepository, paymentRepository, mock(AuditLogService.class));
    }

    private OrderService serviceWithAuditService(OrderRepository orderRepository, PaymentRepository paymentRepository, AuditLogService auditLogService) {
        return serviceWithAuditService(orderRepository, paymentRepository, auditLogService, mock(PaymentService.class));
    }

    private OrderService serviceWithAuditService(OrderRepository orderRepository, PaymentRepository paymentRepository, AuditLogService auditLogService, PaymentService paymentService) {
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        return new OrderService(
                mock(CartRepository.class),
                orderRepository,
                paymentRepository,
                mock(DeliveryRepository.class),
                mock(InventoryService.class),
                mock(AppUserProfileRepository.class),
                mock(VendorRepository.class),
                mock(NotificationService.class),
                paymentService,
                auditLogService,
                new OrderMapper(mock(AppUserProfileRepository.class), tenantContext),
                tenantContext,
                mock(SkuRepository.class),
                mock(ProductRepository.class),
                (cart, customerId) -> CheckoutDiscount.none(),
                mock(com.delivery.delivery.service.DeliveryCodeHasher.class),
                orderStockService,
                mock(com.delivery.cart.service.CartService.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class),
                new com.delivery.order.service.DeliveryAddressResolver(mock(com.delivery.customer.repository.CustomerAddressRepository.class), true), com.delivery.cart.PricingFixtures.platformDefaults(),
                com.delivery.vendor.MerchantScopeFixtures.legacyFallback(tenantContext), mock(com.delivery.delivery.service.DeliveryRetirementService.class), mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    private OrderRepository repositoryWith(UUID orderId, Order order) {
        OrderRepository repository = mock(OrderRepository.class);
        when(repository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
        return repository;
    }

    private Order paymentConfirmedOrder(UUID orderId) {
        Order order = new Order(
                orderId,
                tenantId,
                "PA-TEST-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                new BigDecimal("115.00"),
                "idem-1",
                "hash",
                "123456");
        order.markPaymentConfirmed();
        return order;
    }
}
