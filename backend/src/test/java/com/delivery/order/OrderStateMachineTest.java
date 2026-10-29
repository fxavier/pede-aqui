package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.entity.OrderStatusHistory;
import com.delivery.order.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * LAC-25 (W4-A): the explicit transition table of the current OrderStatus enum, and the guarantees around it — an
 * illegal transition is a 409 with no side effect, every legal one writes exactly one history row and publishes
 * exactly one OrderStatusChanged, and no sequence of commands can leave the table (property-style random walk).
 */
class OrderStateMachineTest {
    private OrderStatusHistoryRepository historyRepository;
    private ApplicationEventPublisher publisher;
    private TenantContext tenantContext;
    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        historyRepository = mock(OrderStatusHistoryRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        tenantContext = mock(TenantContext.class);
        stateMachine = new OrderStateMachine(historyRepository, publisher, tenantContext);
    }

    @Test
    void tableMatchesTheTransitionsTheCodePathsPerform() {
        // checkout -> payment -> vendor fulfilment -> dispatch -> courier -> delivered
        assertAllowed(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_CONFIRMED, OrderStatus.CANCELLED);
        assertAllowed(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.ACCEPTED_BY_VENDOR, OrderStatus.CANCELLED, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.ACCEPTED_BY_VENDOR, OrderStatus.PREPARING, OrderStatus.CANCELLED, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.READY_FOR_PICKUP, OrderStatus.ASSIGNED_TO_COURIER, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.ASSIGNED_TO_COURIER, OrderStatus.PICKED_UP, OrderStatus.DELIVERING, OrderStatus.DELIVERED,
                OrderStatus.CANCELLED, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.PICKED_UP, OrderStatus.DELIVERING, OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.DELIVERING, OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.DELIVERED, OrderStatus.REFUNDED);
        // terminal
        assertAllowed(OrderStatus.CANCELLED);
        assertAllowed(OrderStatus.REFUNDED);
        // legacy values: never written, existing rows can only be closed
        assertAllowed(OrderStatus.PENDING, OrderStatus.CANCELLED);
        assertAllowed(OrderStatus.REJECTED_BY_VENDOR, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED);
        assertAllowed(OrderStatus.DISPATCH_PENDING, OrderStatus.ASSIGNED_TO_COURIER, OrderStatus.REFUNDED);
        assertThat(OrderStateMachine.LEGACY_STATUSES).containsExactlyInAnyOrder(
                OrderStatus.PENDING, OrderStatus.REJECTED_BY_VENDOR, OrderStatus.DISPATCH_PENDING, OrderStatus.REFUND_PENDING);
        // nothing ever moves back to a legacy value or to the initial status
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus legacy : OrderStateMachine.LEGACY_STATUSES) {
                assertThat(OrderStateMachine.canTransition(from, legacy)).as(from + " -> " + legacy).isFalse();
            }
            assertThat(OrderStateMachine.canTransition(from, OrderStatus.PAYMENT_PENDING)).isFalse();
            assertThat(OrderStateMachine.canTransition(from, from)).as("self transition " + from).isFalse();
        }
    }

    @Test
    void illegalTransitionIs409WithoutAnySideEffect() {
        Order order = newOrder();
        OrderTestStatus.force(order, OrderStatus.DELIVERED);

        assertThatThrownBy(() -> stateMachine.cancel(order, CancellationReason.VENDOR_TIMEOUT, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("invalid_order_transition");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getCancellationReason()).isNull();
        verify(historyRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void legalTransitionAppliesWritesOneHistoryRowAndPublishesOneEvent() {
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("kc-vendor"));
        Order order = newOrder();
        OrderTestStatus.force(order, OrderStatus.PAYMENT_CONFIRMED);

        stateMachine.cancelBySellerRejection(order, "Sem stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.SELLER_REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Sem stock");
        assertThat(order.getDeliveryConfirmationCodeDisplay()).isNull(); // PIN cleared on CANCELLED
        ArgumentCaptor<OrderStatusHistory> row = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(row.capture());
        assertThat(row.getValue().getFromStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
        assertThat(row.getValue().getToStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(row.getValue().getActorType()).isEqualTo("USER");
        assertThat(row.getValue().getActorId()).isEqualTo("kc-vendor");
        assertThat(row.getValue().getReason()).isEqualTo("SELLER_REJECTED"); // vendor free text stays out of the history
        ArgumentCaptor<OrderStatusChanged> event = ArgumentCaptor.forClass(OrderStatusChanged.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().previousStatus()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(event.getValue().newStatus()).isEqualTo("CANCELLED");
        assertThat(event.getValue().orderId()).isEqualTo(order.getId());
    }

    @Test
    void creationWritesAHistoryRowFromNullWithoutAnEvent() {
        Order order = newOrder();

        stateMachine.recordCreated(order);

        ArgumentCaptor<OrderStatusHistory> row = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(row.capture());
        assertThat(row.getValue().getFromStatus()).isNull();
        assertThat(row.getValue().getToStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(row.getValue().getActorType()).isEqualTo("SYSTEM");
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    /**
     * Property (LAC-25 acceptance criterion): whatever sequence of commands is attempted, the order only ever moves
     * along the table; every accepted command is one history row + one event, every refused one changes nothing.
     */
    @Test
    void noSequenceOfCommandsLeavesTheTransitionTable() {
        Random random = new Random(20261029L);
        OrderStatus[] targets = OrderStatus.values();
        for (int run = 0; run < 500; run++) {
            setUp();
            Order order = newOrder();
            List<OrderStatus> path = new ArrayList<>(List.of(order.getStatus()));
            int accepted = 0;
            for (int step = 0; step < 12; step++) {
                OrderStatus before = order.getStatus();
                OrderStatus target = targets[random.nextInt(targets.length)];
                try {
                    if (target == OrderStatus.CANCELLED && random.nextBoolean()) {
                        stateMachine.cancel(order, CancellationReason.PAYMENT_EXPIRED, null);
                    } else {
                        stateMachine.transition(order, target, "random");
                    }
                    assertThat(OrderStateMachine.canTransition(before, target)).as(before + " -> " + target).isTrue();
                    assertThat(order.getStatus()).isEqualTo(target);
                    path.add(target);
                    accepted++;
                } catch (BusinessException refused) {
                    assertThat(OrderStateMachine.canTransition(before, target)).as(before + " -> " + target).isFalse();
                    assertThat(order.getStatus()).isEqualTo(before);
                }
            }
            for (int i = 1; i < path.size(); i++) {
                assertThat(OrderStateMachine.canTransition(path.get(i - 1), path.get(i))).isTrue();
            }
            verify(historyRepository, org.mockito.Mockito.times(accepted)).save(any());
            verify(publisher, org.mockito.Mockito.times(accepted)).publishEvent(any(Object.class));
        }
    }

    private void assertAllowed(OrderStatus from, OrderStatus... targets) {
        Set<OrderStatus> expected = targets.length == 0 ? EnumSet.noneOf(OrderStatus.class) : EnumSet.of(targets[0], targets);
        assertThat(OrderStateMachine.allowedTargets(from)).as("targets of " + from).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static Order newOrder() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), "PA-SM", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("11.00"), "key", "hash", "123456");
    }
}
