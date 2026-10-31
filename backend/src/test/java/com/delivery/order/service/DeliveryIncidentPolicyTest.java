package com.delivery.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.delivery.api.DeliveryFailureReason;
import com.delivery.order.entity.DeliveryIncident.Amounts;
import com.delivery.order.entity.DeliveryIncident.Decision;
import com.delivery.order.entity.IncidentCostBearer;
import com.delivery.order.entity.IncidentGoodsDisposition;
import com.delivery.order.entity.IncidentOutcome;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** LAC-44 (W7-B): pins the default outcome table by reason, per-cell configuration and the resolution arithmetic. */
class DeliveryIncidentPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "CUSTOMER_ABSENT,    CUSTOMER, REFUND_MINUS_DELIVERY_FEE, RETURN_TO_STORE",
            "WRONG_ADDRESS,      CUSTOMER, REFUND_MINUS_DELIVERY_FEE, RETURN_TO_STORE",
            "CUSTOMER_REFUSED,   CUSTOMER, REFUND_MINUS_DELIVERY_FEE, RETURN_TO_STORE",
            "STORE_FAULT,        MERCHANT, REFUND_FULL,               RETURN_TO_STORE",
            "DAMAGED_IN_TRANSIT, PLATFORM, REFUND_FULL,               DISCARDED",
            "ACCIDENT,           PLATFORM, REFUND_FULL,               DISCARDED",
            "OTHER,              PLATFORM, REFUND_FULL,               RETURN_TO_STORE"})
    void defaultOutcomeByReason(DeliveryFailureReason reason, IncidentCostBearer bearer, IncidentOutcome outcome,
                                IncidentGoodsDisposition goods) {
        assertThat(new DeliveryIncidentPolicy().defaultFor(reason)).isEqualTo(new Decision(bearer, outcome, goods));
    }

    @Test
    void everyReasonHasADefault() {
        for (DeliveryFailureReason reason : DeliveryFailureReason.values()) {
            assertThat(DeliveryIncidentPolicy.DEFAULTS).containsKey(reason);
        }
    }

    @Test
    void aConfiguredCellReplacesOnlyThatCell() {
        DeliveryIncidentPolicy policy = new DeliveryIncidentPolicy();
        DeliveryIncidentPolicy.Rule rule = new DeliveryIncidentPolicy.Rule();
        rule.setCostBearer(IncidentCostBearer.PLATFORM);
        policy.getPolicy().put(DeliveryFailureReason.WRONG_ADDRESS, rule);

        assertThat(policy.defaultFor(DeliveryFailureReason.WRONG_ADDRESS)).isEqualTo(new Decision(IncidentCostBearer.PLATFORM,
                IncidentOutcome.REFUND_MINUS_DELIVERY_FEE, IncidentGoodsDisposition.RETURN_TO_STORE));
        assertThat(policy.defaultFor(DeliveryFailureReason.CUSTOMER_ABSENT).costBearer()).isEqualTo(IncidentCostBearer.CUSTOMER);
    }

    @Test
    void customerFaultRetainsTheDeliveryFeeAndRefundsTheRest() {
        Amounts amounts = DeliveryIncidentPolicy.amounts(DeliveryIncidentPolicy.DEFAULTS.get(DeliveryFailureReason.CUSTOMER_ABSENT),
                money("110.00"), money("7.00"), money("10.00"), money("100.00"));
        assertThat(amounts).isEqualTo(new Amounts(money("103.00"), money("7.00"), money("0.00"), money("0.00")));
    }

    @Test
    void theRetainedFeeIsCappedAtWhatIsStillRefundable() {
        Amounts amounts = DeliveryIncidentPolicy.amounts(DeliveryIncidentPolicy.DEFAULTS.get(DeliveryFailureReason.CUSTOMER_REFUSED),
                money("5.00"), money("7.00"), money("10.00"), money("100.00"));
        assertThat(amounts).isEqualTo(new Amounts(money("0.00"), money("5.00"), money("0.00"), money("0.00")));
    }

    @Test
    void storeFaultRefundsInFullAndChargesTheRefundedFeesToTheMerchant() {
        Amounts amounts = DeliveryIncidentPolicy.amounts(DeliveryIncidentPolicy.DEFAULTS.get(DeliveryFailureReason.STORE_FAULT),
                money("110.00"), money("7.00"), money("10.00"), money("100.00"));
        assertThat(amounts).isEqualTo(new Amounts(money("110.00"), money("0.00"), money("10.00"), money("0.00")));
    }

    @Test
    void platformFaultWithLostGoodsCompensatesTheMerchantAtGoodsValue() {
        Amounts amounts = DeliveryIncidentPolicy.amounts(DeliveryIncidentPolicy.DEFAULTS.get(DeliveryFailureReason.ACCIDENT),
                money("110.00"), money("7.00"), money("10.00"), money("100.00"));
        assertThat(amounts).isEqualTo(new Amounts(money("110.00"), money("0.00"), money("0.00"), money("100.00")));
    }

    @Test
    void platformFaultWithGoodsBackAtTheStoreHasNoCompensation() {
        Amounts amounts = DeliveryIncidentPolicy.amounts(DeliveryIncidentPolicy.DEFAULTS.get(DeliveryFailureReason.OTHER),
                money("110.00"), money("7.00"), money("10.00"), money("100.00"));
        assertThat(amounts).isEqualTo(new Amounts(money("110.00"), money("0.00"), money("0.00"), money("0.00")));
    }

    @Test
    void merchantFaultWithLostGoodsIsTheMerchantsOwnLoss() {
        Decision decision = new Decision(IncidentCostBearer.MERCHANT, IncidentOutcome.REFUND_FULL, IncidentGoodsDisposition.DISCARDED);
        Amounts amounts = DeliveryIncidentPolicy.amounts(decision, money("110.00"), money("7.00"), money("10.00"), money("100.00"));
        assertThat(amounts.platformCompensation()).isEqualByComparingTo("0.00");
        assertThat(amounts.merchantCharge()).isEqualByComparingTo("10.00");
    }

    @Test
    void nothingCapturedMeansNoRefundNoChargeButLostGoodsAreStillCompensated() {
        Amounts amounts = DeliveryIncidentPolicy.amounts(DeliveryIncidentPolicy.DEFAULTS.get(DeliveryFailureReason.DAMAGED_IN_TRANSIT),
                BigDecimal.ZERO, money("7.00"), money("10.00"), money("100.00"));
        assertThat(amounts).isEqualTo(new Amounts(money("0.00"), money("0.00"), money("0.00"), money("100.00")));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
