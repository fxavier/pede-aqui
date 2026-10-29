package com.delivery.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.order.service.ReservationReconciler;
import com.delivery.order.service.ReservationReconciler.Action;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** LAC-23: the reconciler's V043 classification, and its status sets pinned to the delivery module's rules. */
class ReservationReconcilerClassificationTest {

    @ParameterizedTest(name = "order {0} + delivery {1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            // goods left the store -> commit (delivery wins over order status)
            "ASSIGNED_TO_COURIER, PICKED_UP,            COMMIT",
            "DELIVERING,          ON_ROUTE_TO_CUSTOMER, COMMIT",
            "DELIVERED,           NULL,                 COMMIT",
            "CANCELLED,           FAILED_DELIVERY,      COMMIT",
            "REFUNDED,            DELIVERED,            COMMIT",
            // died before pickup -> release
            "CANCELLED,           NULL,                 RELEASE",
            "CANCELLED,           CANCELLED,            RELEASE",
            "REJECTED_BY_VENDOR,  NULL,                 RELEASE",
            "REFUNDED,            NULL,                 RELEASE",
            "REFUNDED,            CANCELLED,            RELEASE",
            "REFUNDED,            DISPATCH_PENDING,     RELEASE",
            // refunded but a courier is still engaged before pickup -> leave for ops
            "REFUNDED,            ACCEPTED,             NEEDS_ATTENTION",
            "REFUNDED,            ARRIVED_AT_VENDOR,    NEEDS_ATTENTION",
            // live orders -> nothing
            "PAYMENT_CONFIRMED,   NULL,                 NONE",
            "READY_FOR_PICKUP,    ACCEPTED,             NONE",
            "PAYMENT_PENDING,     NULL,                 NONE"})
    void classifiesLikeTheV043DataFix(String orderStatus, String deliveryStatus, Action expected) {
        assertThat(ReservationReconciler.classify(orderStatus, deliveryStatus)).isEqualTo(expected);
    }

    @Test
    void goodsLeftTheStoreSetMatchesDeliveryStatus() throws Exception {
        Set<String> expected = Arrays.stream(DeliveryStatus.values())
                .filter(DeliveryStatus::isGoodsLeftTheStore).map(Enum::name).collect(Collectors.toSet());
        for (String status : expected) {
            assertThat(ReservationReconciler.classify("ACCEPTED_BY_VENDOR", status)).as(status).isEqualTo(Action.COMMIT);
        }
        for (DeliveryStatus status : DeliveryStatus.values()) {
            if (!status.isGoodsLeftTheStore()) {
                assertThat(ReservationReconciler.classify("ACCEPTED_BY_VENDOR", status.name())).as(status.name()).isEqualTo(Action.NONE);
            }
        }
    }
}
