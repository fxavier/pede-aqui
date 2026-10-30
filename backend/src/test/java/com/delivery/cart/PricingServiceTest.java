package com.delivery.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.cart.service.PricingService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.pricing.DeliveryFeeBasis;
import com.delivery.common.pricing.DeliveryFeeQuote;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.common.pricing.PriceQuote;
import com.delivery.geo.entity.DeliveryFeeRule;
import com.delivery.geo.repository.DeliveryFeeRuleRepository;
import com.delivery.tenant.entity.FeePolicy;
import com.delivery.tenant.repository.FeePolicyRepository;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** INC-13 pricing engine: fee_policies + delivery_fee_rules + server distance, with deterministic fallbacks. */
class PricingServiceTest {
    private static final GeoPoint CUSTOMER = new GeoPoint(-25.94, 32.59);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final FeePolicyRepository policies = mock(FeePolicyRepository.class);
    private final DeliveryFeeRuleRepository rules = mock(DeliveryFeeRuleRepository.class);
    private final VendorRepository vendors = mock(VendorRepository.class);
    private final PricingService service = new PricingService(policies, rules, vendors);

    // ---- Platform defaults: 5.00 / 5 % / VAT 16 % included (W6-A; was 8 % added: taxes 1.44, total 25.34) ----

    @Test
    void tenantWithoutPolicyUsesThePlatformDefaultsWithVatIncluded() {
        PriceQuote quote = service.quote(tenantId, vendorId, new BigDecimal("18.00"), null);

        assertThat(quote.deliveryFee()).isEqualByComparingTo("5.00");
        assertThat(quote.serviceFee()).isEqualByComparingTo("0.90");
        assertThat(quote.taxes()).isEqualByComparingTo("0.00");
        assertThat(quote.total()).isEqualByComparingTo("23.90");
        assertThat(quote.taxInclusive()).isTrue();
        assertThat(quote.taxRate()).isEqualByComparingTo("0.16");
        assertThat(quote.vat(BigDecimal.ZERO).vatTotal()).isEqualByComparingTo("3.29");
        assertThat(quote.delivery().basis()).isEqualTo(DeliveryFeeBasis.PLATFORM_DEFAULT);
        assertThat(quote.engineVersion()).isEqualTo(PricingService.ENGINE_VERSION);
    }

    @Test
    void roundsServiceFeeAndTaxToCents() {
        PriceQuote quote = service.quote(tenantId, vendorId, new BigDecimal("10.05"), null);

        assertThat(quote.serviceFee()).isEqualByComparingTo("0.50");
        assertThat(quote.taxes()).isEqualByComparingTo("0.00");
        assertThat(quote.total()).isEqualByComparingTo("15.55");
        assertThat(quote.vat(BigDecimal.ZERO).goodsVat()).isEqualByComparingTo("1.39");
    }

    @Test
    void emptySubtotalChargesNothingWithAnExplicitBasis() {
        PriceQuote quote = service.quote(tenantId, vendorId, BigDecimal.ZERO, CUSTOMER);

        assertThat(quote.deliveryFee()).isEqualByComparingTo("0");
        assertThat(quote.serviceFee()).isEqualByComparingTo("0");
        assertThat(quote.taxes()).isEqualByComparingTo("0");
        assertThat(quote.total()).isEqualByComparingTo("0");
        assertThat(quote.delivery().basis()).isEqualTo(DeliveryFeeBasis.EMPTY_CART);
    }

    @Test
    void previewHasNoMagicCouponAndNoDiscount() {
        var preview = service.preview(tenantId, new BigDecimal("100.00"));

        assertThat(preview.discount()).isEqualByComparingTo("0");
        assertThat(preview.tax()).isEqualByComparingTo("0.00"); // W6-A: VAT included, nothing added (was 8.00)
        assertThat(preview.total()).isEqualByComparingTo("110.00"); // was 118.00
    }

    // ---- fee_policies are applied ----

    @Test
    void tenantPolicyDrivesDeliveryServiceFeeAndTaxAndIsRecorded() {
        FeePolicy policy = policy("7.00", "1.00", "0.0200", "0.1600");
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(policy));

        PriceQuote quote = service.quote(tenantId, vendorId, new BigDecimal("100.00"), null);

        assertThat(quote.deliveryFee()).isEqualByComparingTo("7.00");
        assertThat(quote.serviceFee()).isEqualByComparingTo("3.00"); // 1.00 flat + 2 % of 100
        assertThat(quote.taxes()).isEqualByComparingTo("0.00"); // VAT-inclusive (W6-A; was 16.00 added)
        assertThat(quote.total()).isEqualByComparingTo("110.00"); // was 126.00
        // 100 → 13.79, 7.00 → 0.97, 3.00 → 0.41
        assertThat(quote.vat(BigDecimal.ZERO).vatTotal()).isEqualByComparingTo("15.17");
        assertThat(quote.delivery().basis()).isEqualTo(DeliveryFeeBasis.POLICY_BASE);
        assertThat(quote.delivery().feePolicyId()).isEqualTo(policy.getId());
        assertThat(quote.delivery().feePolicyVersion()).isZero();
        assertThat(quote.delivery().distanceKm()).isNull();
    }

    @Test
    void exclusivePolicyKeepsThePreW6aFormulaWithTaxAddedOnTop() {
        FeePolicy policy = policy("5.00", "0.00", "0.0500", "0.0800");
        policy.changeTaxInclusive(false);
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(policy));

        PriceQuote quote = service.quote(tenantId, vendorId, new BigDecimal("18.00"), null);

        assertThat(quote.taxes()).isEqualByComparingTo("1.44");
        assertThat(quote.total()).isEqualByComparingTo("25.34");
        assertThat(quote.vat(BigDecimal.ZERO).vatTotal()).isEqualByComparingTo("1.44");
    }

    @Test
    void linesWithZeroRatedOrExemptProductsOnlyExtractVatFromStandardLines() {
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.empty());

        PriceQuote quote = service.quote(tenantId, vendorId, List.of(
                new com.delivery.common.pricing.TaxableLine(new BigDecimal("11.60"), com.delivery.common.pricing.TaxCategory.STANDARD),
                new com.delivery.common.pricing.TaxableLine(new BigDecimal("8.40"), com.delivery.common.pricing.TaxCategory.EXEMPT)), null);

        assertThat(quote.subtotal()).isEqualByComparingTo("20.00");
        assertThat(quote.vat(BigDecimal.ZERO).goodsVat()).isEqualByComparingTo("1.60");
        assertThat(quote.vat(BigDecimal.ZERO).goodsExemptGross()).isEqualByComparingTo("8.40");
    }

    @Test
    void tenantMaximumDeliveryDistanceIsExposedOnTheQuote() {
        FeePolicy policy = policy("5.00", "0.00", "0.0500", "0.1600");
        policy.changeMaxDeliveryDistanceKm(new BigDecimal("8.00"));
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(policy));

        assertThat(service.quote(tenantId, vendorId, new BigDecimal("10.00"), null).policyMaxDeliveryDistanceKm()).isEqualByComparingTo("8.00");
    }

    @Test
    void ratesAboveOneHundredPercentAreRefusedInsteadOfCharged() {
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(policy("5.00", "0.00", "0.0500", "8.0000")));

        assertThatThrownBy(() -> service.quote(tenantId, vendorId, new BigDecimal("10.00"), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "pricing_policy_invalid");
    }

    // ---- delivery_fee_rules by server-side distance ----

    @Test
    void distanceBandOfTheVendorAppliesWhenTheDistanceIsKnown() {
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(policy("5.00", "0.00", "0.0500", "0.0800")));
        DeliveryFeeRule near = rule("0", "3", "40.00");
        DeliveryFeeRule far = rule("3", "6", "60.00");
        when(rules.findByTenantIdAndVendorIdAndActive(tenantId, vendorId, true)).thenReturn(List.of(far, near));
        distance(3.664);

        PriceQuote quote = service.quote(tenantId, vendorId, new BigDecimal("18.00"), CUSTOMER);

        assertThat(quote.deliveryFee()).isEqualByComparingTo("60.00");
        assertThat(quote.delivery().basis()).isEqualTo(DeliveryFeeBasis.DISTANCE_RULE);
        assertThat(quote.delivery().distanceKm()).isEqualByComparingTo("3.66");
        assertThat(quote.delivery().deliveryFeeRuleId()).isEqualTo(far.getId());
        assertThat(quote.delivery().deliveryFeeRuleVersion()).isZero();
        assertThat(quote.total()).isEqualByComparingTo("78.90"); // 18 + 60 + 0.90, VAT included (was 80.34 with 1.44 added)
    }

    @Test
    void bandBoundariesAreInclusiveAndTheLowerBandWinsATie() {
        DeliveryFeeRule near = rule("0", "3", "40.00");
        DeliveryFeeRule far = rule("3", "6", "60.00");
        assertThat(feeAt(List.of(near, far), "3.00").amount()).isEqualByComparingTo("40.00");
        assertThat(feeAt(List.of(near, far), "0.00").amount()).isEqualByComparingTo("40.00");
        assertThat(feeAt(List.of(near, far), "6.00").amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void gapsAndDistancesBeyondTheLastBandUseTheNearestLowerBand() {
        DeliveryFeeRule near = rule("1", "3", "40.00");
        DeliveryFeeRule far = rule("5", "8", "60.00");
        assertThat(feeAt(List.of(near, far), "4.00").amount()).isEqualByComparingTo("40.00"); // gap 3–5
        assertThat(feeAt(List.of(near, far), "25.00").amount()).isEqualByComparingTo("60.00"); // beyond 8
        assertThat(feeAt(List.of(near, far), "0.50").amount()).isEqualByComparingTo("40.00"); // below the first band
    }

    @Test
    void unknownDistanceFallsBackToThePolicyBaseFeeEvenWhenTheVendorHasRules() {
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(policy("5.00", "0.00", "0.0500", "0.0800")));
        // Vendor without coordinates: the distance query returns no row.
        when(vendors.straightLineDistancesKm(anyCollection(), anyDouble(), anyDouble())).thenReturn(List.of());

        PriceQuote withAddress = service.quote(tenantId, vendorId, new BigDecimal("18.00"), CUSTOMER);
        PriceQuote withoutAddress = service.quote(tenantId, vendorId, new BigDecimal("18.00"), null);

        assertThat(withAddress.deliveryFee()).isEqualByComparingTo("5.00");
        assertThat(withAddress.delivery().basis()).isEqualTo(DeliveryFeeBasis.POLICY_BASE);
        assertThat(withoutAddress.deliveryFee()).isEqualByComparingTo("5.00");
        assertThat(withoutAddress.delivery().basis()).isEqualTo(DeliveryFeeBasis.POLICY_BASE);
        verify(rules, never()).findByTenantIdAndVendorIdAndActive(any(), any(), any());
    }

    // ---- search uses the same decision ----

    @Test
    void searchEstimatesUseTheSameRulesAndIgnoreRulesOfAnotherTenant() {
        Vendor vendor = new Vendor(vendorId, tenantId, "Loja", null, -25.9692, 32.5732, null, null, null, null, null, null);
        DeliveryFeeRule own = rule("3", "6", "60.00");
        DeliveryFeeRule foreign = new DeliveryFeeRule(UUID.randomUUID(), UUID.randomUUID(), vendorId, new BigDecimal("0"), new BigDecimal("10"), new BigDecimal("1.00"));
        when(policies.findByTenantIdIn(anyCollection())).thenReturn(List.of(policy("5.00", "0.00", "0.0500", "0.0800")));
        when(rules.findByVendorIdInAndActiveTrue(anyCollection())).thenReturn(List.of(own, foreign));
        distance(3.664);

        Map<UUID, DeliveryFeeQuote> fees = service.estimateDeliveryFees(List.of(vendor), CUSTOMER);
        Map<UUID, DeliveryFeeQuote> noPosition = service.estimateDeliveryFees(List.of(vendor), null);

        assertThat(fees.get(vendorId).amount()).isEqualByComparingTo("60.00");
        assertThat(fees.get(vendorId).basis()).isEqualTo(DeliveryFeeBasis.DISTANCE_RULE);
        assertThat(noPosition.get(vendorId).amount()).isEqualByComparingTo("5.00");
        assertThat(noPosition.get(vendorId).basis()).isEqualTo(DeliveryFeeBasis.POLICY_BASE);
    }

    // ---- helpers ----

    private DeliveryFeeQuote feeAt(List<DeliveryFeeRule> bandRules, String km) {
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(rules.findByTenantIdAndVendorIdAndActive(tenantId, vendorId, true)).thenReturn(bandRules);
        distance(Double.parseDouble(km));
        return service.quote(tenantId, vendorId, new BigDecimal("10.00"), CUSTOMER).delivery();
    }

    private void distance(double km) {
        VendorRepository.VendorDistance row = new VendorRepository.VendorDistance() {
            public UUID getVendorId() { return vendorId; }
            public Double getDistanceKm() { return km; }
        };
        when(vendors.straightLineDistancesKm(anyCollection(), anyDouble(), anyDouble())).thenReturn(List.of(row));
    }

    private FeePolicy policy(String deliveryFee, String serviceFee, String serviceFeeRate, String taxRate) {
        FeePolicy policy = new FeePolicy(UUID.randomUUID(), tenantId, new BigDecimal(deliveryFee), new BigDecimal(serviceFee),
                new BigDecimal(taxRate), BigDecimal.ZERO, "test");
        policy.changeServiceFeeRate(new BigDecimal(serviceFeeRate));
        return policy;
    }

    private DeliveryFeeRule rule(String minKm, String maxKm, String fee) {
        return new DeliveryFeeRule(UUID.randomUUID(), tenantId, vendorId, new BigDecimal(minKm), new BigDecimal(maxKm), new BigDecimal(fee));
    }
}
