package com.delivery.order;

import static org.mockito.Mockito.mock;

import com.delivery.common.security.TenantContext;
import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderStatusHistoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test-only helpers around order status (W4-A): {@link Order} only changes status through {@link OrderStateMachine},
 * so unit-test fixtures put an order straight into a starting status here (the same effect the removed
 * {@code mark*()} setters had: a terminal PIN outcome clears the plain PIN), and get a state machine wired with mocks.
 */
public final class OrderTestStatus {
    private OrderTestStatus() {
    }

    /** Puts the fixture order in {@code status} without a transition (no table check, history or event). */
    public static Order force(Order order, OrderStatus status) {
        ReflectionTestUtils.setField(order, "status", status);
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
            ReflectionTestUtils.setField(order, "deliveryConfirmationCodeDisplay", null);
        }
        return order;
    }

    /** Fixture of a seller-rejected order: CANCELLED + SELLER_REJECTED + the vendor text. */
    public static Order forceSellerRejected(Order order, String vendorReason) {
        force(order, OrderStatus.CANCELLED);
        ReflectionTestUtils.setField(order, "cancellationReason", CancellationReason.SELLER_REJECTED);
        ReflectionTestUtils.setField(order, "rejectionReason", vendorReason);
        return order;
    }

    /** A real state machine (real transition table) whose history repository, publisher and tenant context are mocks. */
    public static OrderStateMachine stateMachine() {
        return new OrderStateMachine(mock(OrderStatusHistoryRepository.class), mock(ApplicationEventPublisher.class), mock(TenantContext.class));
    }

    /** A real state machine using the given (mock) publisher, so tests can verify the published events. */
    public static OrderStateMachine stateMachine(ApplicationEventPublisher publisher) {
        return new OrderStateMachine(mock(OrderStatusHistoryRepository.class), publisher, mock(TenantContext.class));
    }
}
