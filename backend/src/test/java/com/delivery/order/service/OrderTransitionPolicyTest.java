package com.delivery.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;

/** W4-A: the vendor rejection policy is a narrower rule that must stay inside the order state machine table. */
class OrderTransitionPolicyTest {
    @Test
    void vendorRejectionStatusesCanAllBeCancelled() {
        assertThat(OrderService.VENDOR_REJECTION_ALLOWED_STATUSES).isNotEmpty();
        for (OrderStatus from : OrderService.VENDOR_REJECTION_ALLOWED_STATUSES) {
            assertThat(OrderStateMachine.canTransition(from, OrderStatus.CANCELLED)).as(from.name()).isTrue();
        }
    }
}
