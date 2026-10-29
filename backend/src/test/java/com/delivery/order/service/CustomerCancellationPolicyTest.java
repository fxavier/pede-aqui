package com.delivery.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.service.CustomerCancellationPolicy.Phase;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** LAC-26: the customer cancellation policy table (every OrderStatus) and its fee/refund arithmetic. */
class CustomerCancellationPolicyTest {

    @Test
    void everyStatusHasTheDecidedPhase() {
        Map<OrderStatus, Phase> expected = new EnumMap<>(OrderStatus.class);
        expected.put(OrderStatus.PAYMENT_PENDING, Phase.FREE);
        expected.put(OrderStatus.PAYMENT_CONFIRMED, Phase.FREE);
        expected.put(OrderStatus.ACCEPTED_BY_VENDOR, Phase.REQUEST);
        expected.put(OrderStatus.PREPARING, Phase.REQUEST);
        expected.put(OrderStatus.CANCELLED, Phase.ALREADY_CANCELLED);
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(CustomerCancellationPolicy.phaseOf(status)).as(status.name())
                    .isEqualTo(expected.getOrDefault(status, Phase.NOT_CANCELLABLE));
        }
        // READY_FOR_PICKUP onwards, refunded and legacy rows are never cancellable by the customer.
        assertThat(CustomerCancellationPolicy.phaseOf(OrderStatus.READY_FOR_PICKUP)).isEqualTo(Phase.NOT_CANCELLABLE);
        assertThat(CustomerCancellationPolicy.phaseOf(OrderStatus.DELIVERED)).isEqualTo(Phase.NOT_CANCELLABLE);
        assertThat(CustomerCancellationPolicy.phaseOf(OrderStatus.REFUNDED)).isEqualTo(Phase.NOT_CANCELLABLE);
    }

    @Test
    void cancellablePhasesStayInsideTheOrderStateMachine() {
        for (OrderStatus status : OrderStatus.values()) {
            Phase phase = CustomerCancellationPolicy.phaseOf(status);
            if (phase == Phase.FREE || phase == Phase.REQUEST) {
                assertThat(OrderStateMachine.canTransition(status, OrderStatus.CANCELLED)).as(status.name()).isTrue();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "90.00, 0.10, 9.00",
            "33.33, 0.10, 3.33",
            "33.35, 0.10, 3.34",   // 3.335 -> HALF_UP
            "0.05, 0.10, 0.01",    // 0.005 -> HALF_UP
            "100.00, 0, 0.00",
            "0, 0.10, 0.00",
            "100.00, 1, 100.00"})
    void feeIsSubtotalTimesRateHalfUpTwoDecimals(String subtotal, String rate, String fee) {
        assertThat(CustomerCancellationPolicy.fee(new BigDecimal(subtotal), new BigDecimal(rate))).isEqualByComparingTo(fee)
                .satisfies(value -> assertThat(value.scale()).isEqualTo(2));
    }

    @ParameterizedTest
    @CsvSource({
            // refundable, quoted fee, applied fee, refund
            "100.00, 9.00, 9.00, 91.00",
            "5.00, 9.00, 5.00, 0.00",      // fee capped at what is still refundable: refund never negative
            "0.00, 9.00, 0.00, 0.00",
            "-1.00, 9.00, 0.00, 0.00",     // over-refunded payment (inconsistent): no fee, no refund
            "60.00, 9.00, 9.00, 51.00"})   // after an earlier partial refund of 40.00
    void refundIsRefundableMinusTheCappedFee(String refundable, String quoted, String applied, String refund) {
        BigDecimal fee = CustomerCancellationPolicy.appliedFee(new BigDecimal(quoted), new BigDecimal(refundable));
        assertThat(fee).isEqualByComparingTo(applied);
        assertThat(CustomerCancellationPolicy.refundAfterFee(new BigDecimal(refundable), fee)).isEqualByComparingTo(refund);
    }
}
