package com.delivery.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.cart.service.PricingService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingServiceTest {
    private final PricingService service = new PricingService();

    @Test
    void calculatesFeesTaxDiscountAndTotalDeterministically() {
        var pricing = service.calculate(new BigDecimal("100.00"), "MVP10");

        assertThat(pricing.deliveryFee()).isEqualByComparingTo("5.00");
        assertThat(pricing.serviceFee()).isEqualByComparingTo("5.00");
        assertThat(pricing.tax()).isEqualByComparingTo("8.00");
        assertThat(pricing.discount()).isEqualByComparingTo("10.00");
        assertThat(pricing.total()).isEqualByComparingTo("108.00");
    }

    @Test
    void roundsServiceFeeAndTaxToCents() {
        var pricing = service.calculate(new BigDecimal("10.05"), null);

        assertThat(pricing.serviceFee()).isEqualByComparingTo("0.50");
        assertThat(pricing.tax()).isEqualByComparingTo("0.80");
        assertThat(pricing.total()).isEqualByComparingTo("16.35");
    }

    // ---- INC-13 characterization of the constant-based engine (before fee_policies/delivery_fee_rules) ----

    @Test
    void characterizationFlatDeliveryFeeFivePercentServiceFeeEightPercentTax() {
        var pricing = service.calculate(new BigDecimal("18.00"), null);

        assertThat(pricing.deliveryFee()).isEqualByComparingTo("5.00");
        assertThat(pricing.serviceFee()).isEqualByComparingTo("0.90");
        assertThat(pricing.tax()).isEqualByComparingTo("1.44");
        assertThat(pricing.discount()).isEqualByComparingTo("0");
        assertThat(pricing.total()).isEqualByComparingTo("25.34");
    }

    @Test
    void characterizationEmptySubtotalChargesNothing() {
        var pricing = service.calculate(BigDecimal.ZERO, null);

        assertThat(pricing.deliveryFee()).isEqualByComparingTo("0");
        assertThat(pricing.serviceFee()).isEqualByComparingTo("0");
        assertThat(pricing.tax()).isEqualByComparingTo("0");
        assertThat(pricing.total()).isEqualByComparingTo("0");
    }

    @Test
    void characterizationOnlyTheMvp10MagicCouponDiscounts() {
        assertThat(service.calculate(new BigDecimal("100.00"), "mvp10").discount()).isEqualByComparingTo("10.00");
        assertThat(service.calculate(new BigDecimal("100.00"), "OTHER").discount()).isEqualByComparingTo("0");
        assertThat(service.calculate(new BigDecimal("100.00"), "OTHER").total()).isEqualByComparingTo("118.00");
    }
}
