package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.repository.CartRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.inventory.service.InventoryService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.service.OrderService;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VendorFulfillmentTest {
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void acceptsPaymentConfirmedOrder() {
        UUID orderId = UUID.randomUUID();
        OrderRepository repository = repositoryWith(orderId, paymentConfirmedOrder(orderId));
        OrderService service = service(repository);

        var response = service.acceptByVendor(orderId);

        assertThat(response.status()).isEqualTo(OrderStatus.ACCEPTED_BY_VENDOR);
    }

    @Test
    void rejectsOrderWithReason() {
        UUID orderId = UUID.randomUUID();
        OrderRepository repository = repositoryWith(orderId, paymentConfirmedOrder(orderId));
        OrderService service = service(repository);

        var response = service.rejectByVendor(orderId, "Sem ingredientes");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void movesAcceptedOrderToPreparingAndReadyForPickup() {
        UUID orderId = UUID.randomUUID();
        Order order = paymentConfirmedOrder(orderId);
        order.markAcceptedByVendor();
        OrderRepository repository = repositoryWith(orderId, order);
        OrderService service = service(repository);

        var preparing = service.markPreparing(orderId);
        var ready = service.markReadyForPickup(orderId);

        assertThat(preparing.status()).isEqualTo(OrderStatus.PREPARING);
        assertThat(ready.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
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
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        return new OrderService(
                mock(CartRepository.class),
                orderRepository,
                mock(PaymentRepository.class),
                mock(DeliveryRepository.class),
                mock(InventoryService.class),
                mock(AppUserProfileRepository.class),
                mock(VendorRepository.class),
                new OrderMapper(),
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
