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
}
