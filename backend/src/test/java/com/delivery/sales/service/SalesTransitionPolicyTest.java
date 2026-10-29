package com.delivery.sales.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;

/**
 * W4-A: the sales cancel set and the status-override allow-list are narrower policies than the order state machine;
 * every pair they allow must also be in its transition table (otherwise the action would always answer 409).
 */
class SalesTransitionPolicyTest {
    @Test
    void overrideAllowListIsASubsetOfTheStateMachineTable() {
        SalesService.OVERRIDE_ALLOW_LIST.forEach((from, to) ->
                assertThat(OrderStateMachine.canTransition(from, to)).as(from + " -> " + to).isTrue());
    }

    @Test
    void everyCancellableStatusCanBeCancelled() {
        for (OrderStatus from : SalesService.CANCELLABLE_STATUSES) {
            assertThat(OrderStateMachine.canTransition(from, OrderStatus.CANCELLED)).as(from.name()).isTrue();
        }
    }
}
