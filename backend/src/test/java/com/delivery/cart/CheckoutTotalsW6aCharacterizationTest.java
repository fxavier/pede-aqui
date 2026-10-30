package com.delivery.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.cart.service.PricingService;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.common.pricing.PriceQuote;
import com.delivery.common.pricing.PricingMath;
import com.delivery.geo.entity.DeliveryFeeRule;
import com.delivery.geo.repository.DeliveryFeeRuleRepository;
import com.delivery.tenant.entity.FeePolicy;
import com.delivery.tenant.repository.FeePolicyRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * W6-A (LAC-22) characterization of the checkout totals produced by the pricing engine, one row per scenario.
 * Each row pins subtotal, delivery fee, service fee, tax line, discount and total for the policy shapes in use:
 * the V048 seed (5.00 / 5 % / tax 8 % added on top), an admin-configured policy and a distance band. The table in
 * the W6-A report (old → new) is built from this test before and after the VAT change.
 */
class CheckoutTotalsW6aCharacterizationTest {
    private static final GeoPoint CUSTOMER = new GeoPoint(-25.94, 32.59);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final FeePolicyRepository policies = mock(FeePolicyRepository.class);
    private final DeliveryFeeRuleRepository rules = mock(DeliveryFeeRuleRepository.class);
    private final VendorRepository vendors = mock(VendorRepository.class);
    private final PricingService service = new PricingService(policies, rules, vendors);

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // scenario,            policy,  band,  subtotal, discount, delivery, service, taxes, total
            "seed-18,               SEED,    NONE,  18.00,    0.00,     5.00,     0.90,    1.44,  25.34",
            "seed-100,              SEED,    NONE,  100.00,   0.00,     5.00,     5.00,    8.00,  118.00",
            "seed-rounding-10.05,   SEED,    NONE,  10.05,    0.00,     5.00,     0.50,    0.80,  16.35",
            "seed-coupon-10pct,     SEED,    NONE,  18.00,    1.80,     5.00,     0.90,    1.44,  23.54",
            "seed-distance-band-60, SEED,    BAND,  18.00,    0.00,     60.00,    0.90,    1.44,  80.34",
            "admin-7/1+2%/16%,      ADMIN16, NONE,  18.00,    0.00,     7.00,     1.36,    2.88,  29.24",
            "no-policy-default,     NONE,    NONE,  18.00,    0.00,     5.00,     0.90,    1.44,  25.34",
    })
    void checkoutTotals(String scenario, String policy, String band, String subtotal, String discount,
                        String delivery, String serviceFee, String taxes, String total) {
        when(policies.findByTenantId(tenantId)).thenReturn(switch (policy) {
            case "SEED" -> Optional.of(policy("5.00", "0.00", "0.0500", "0.0800"));
            case "ADMIN16" -> Optional.of(policy("7.00", "1.00", "0.0200", "0.1600"));
            default -> Optional.empty();
        });
        GeoPoint destination = null;
        if ("BAND".equals(band)) {
            destination = CUSTOMER;
            when(rules.findByTenantIdAndVendorIdAndActive(tenantId, vendorId, true)).thenReturn(List.of(
                    new DeliveryFeeRule(UUID.randomUUID(), tenantId, vendorId, new BigDecimal("0"), new BigDecimal("3"), new BigDecimal("40.00")),
                    new DeliveryFeeRule(UUID.randomUUID(), tenantId, vendorId, new BigDecimal("3"), new BigDecimal("6"), new BigDecimal("60.00"))));
            VendorRepository.VendorDistance row = new VendorRepository.VendorDistance() {
                public UUID getVendorId() { return vendorId; }
                public Double getDistanceKm() { return 3.66; }
            };
            when(vendors.straightLineDistancesKm(anyCollection(), anyDouble(), anyDouble())).thenReturn(List.of(row));
        }

        PriceQuote quote = service.quote(tenantId, vendorId, new BigDecimal(subtotal), destination);
        BigDecimal charged = PricingMath.orderTotal(quote.subtotal(), quote.fees(), quote.taxes(), new BigDecimal(discount));

        assertThat(quote.deliveryFee()).as("delivery").isEqualByComparingTo(delivery);
        assertThat(quote.serviceFee()).as("service fee").isEqualByComparingTo(serviceFee);
        assertThat(quote.taxes()).as("taxes added on top").isEqualByComparingTo(taxes);
        assertThat(charged).as("total charged").isEqualByComparingTo(total);
    }

    private FeePolicy policy(String deliveryFee, String serviceFee, String serviceFeeRate, String taxRate) {
        FeePolicy policy = new FeePolicy(UUID.randomUUID(), tenantId, new BigDecimal(deliveryFee), new BigDecimal(serviceFee),
                new BigDecimal(taxRate), BigDecimal.ZERO, "test");
        policy.changeServiceFeeRate(new BigDecimal(serviceFeeRate));
        return policy;
    }
}
