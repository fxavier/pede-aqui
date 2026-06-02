package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.repository.CartRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.inventory.service.InventoryService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.service.OrderService;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.vendor.repository.VendorRepository;
import com.delivery.notification.service.NotificationService;
import com.delivery.payment.service.PaymentService;
import com.delivery.payment.entity.Payment;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VendorFulfillmentTest {
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
    void rejectsOrderWithReasonAndTriggersRefundFlow() {
        UUID orderId = UUID.randomUUID();
        Order order = paymentConfirmedOrder(orderId);
        OrderRepository orderRepository = repositoryWith(orderId, order);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(UUID.randomUUID());
        when(payment.getAmount()).thenReturn(new BigDecimal("115.00"));
        when(paymentRepository.findByTenantIdAndOrderId(tenantId, orderId)).thenReturn(Optional.of(payment));
        AuditLogService auditLogService = mock(AuditLogService.class);
        
        OrderService service = serviceWithAuditService(orderRepository, paymentRepository, auditLogService);

        var response = service.rejectByVendor(orderId, "Sem ingredientes");

        assertThat(response.status()).isEqualTo(OrderStatus.REFUND_PENDING);
        assertThat(order.getRejectionReason()).isEqualTo("Sem ingredientes");
        verify(auditLogService).log("ORDER_REJECTED", "order", orderId.toString(), order.getReference(), "SUCCESS");
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
                mock(PaymentService.class),
                auditLogService,
                new OrderMapper(mock(AppUserProfileRepository.class), tenantContext),
                tenantContext);
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
