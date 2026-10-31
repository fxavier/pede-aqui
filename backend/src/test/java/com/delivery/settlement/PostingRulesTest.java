package com.delivery.settlement;

import static com.delivery.settlement.entity.LedgerAccountType.CUSTOMER_FUNDS_HELD;
import static com.delivery.settlement.entity.LedgerAccountType.CUSTOMER_REFUNDS_PAYABLE;
import static com.delivery.settlement.entity.LedgerAccountType.MERCHANT_PAYABLE;
import static com.delivery.settlement.entity.LedgerAccountType.PLATFORM_CASH_CLEARING;
import static com.delivery.settlement.entity.LedgerAccountType.PLATFORM_PROMOTION_EXPENSE;
import static com.delivery.settlement.entity.LedgerAccountType.PLATFORM_REVENUE_COMMISSION;
import static com.delivery.settlement.entity.LedgerAccountType.PLATFORM_REVENUE_FEES;
import static com.delivery.settlement.entity.LedgerAccountType.PLATFORM_VAT_PAYABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.settlement.entity.LedgerAccountType;
import com.delivery.settlement.service.PostingRules;
import com.delivery.settlement.service.PostingRules.DeliveredInput;
import com.delivery.settlement.service.PostingRules.Line;
import com.delivery.settlement.service.PostingRules.Recognition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Posting rules of the settlement ledger (LAC-33/34, ADR-0005): amounts, signs, rounding residue and balance. */
class PostingRulesTest {
    private static final UUID VENDOR = UUID.randomUUID();
    private static final BigDecimal RATE_15 = new BigDecimal("0.15");

    @Test
    void paymentCapturedDebitsCashClearingAndCreditsFundsHeld() {
        List<Line> lines = PostingRules.paymentCaptured(new BigDecimal("126"));

        assertThat(amounts(lines)).containsExactlyInAnyOrderEntriesOf(Map.of(
                PLATFORM_CASH_CLEARING, m("126.00"), CUSTOMER_FUNDS_HELD, m("-126.00")));
    }

    @Test
    void deliveryRecognisesMerchantPayableCommissionAndFees() {
        // goods 100 + fees 10 + tax on goods 16 = 126 captured; commission 15 % of 100.
        Recognition r = PostingRules.orderDelivered(input("126.00", "100.00", "10.00", "0.00", "0.00", RATE_15, BigDecimal.ZERO, true));

        assertThat(amounts(r.lines())).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("126.00"),
                PLATFORM_REVENUE_COMMISSION, m("-15.00"),
                PLATFORM_REVENUE_FEES, m("-10.00"),
                MERCHANT_PAYABLE, m("-101.00"))); // subtotal + tax − commission
        assertThat(r.commissionAmount()).isEqualByComparingTo("15.00");
        assertThat(r.commissionBasis()).isEqualByComparingTo("100.00");
        assertThat(merchantLine(r.lines()).ownerId()).isEqualTo(VENDOR);
        assertThat(PostingRules.sum(r.lines())).isEqualByComparingTo("0");
    }

    @Test
    void platformFundedDiscountIsAPromotionExpenseAndLeavesTheMerchantWhole() {
        // 100 + 10 + 16 − 20 discount = 106 captured.
        Recognition r = PostingRules.orderDelivered(input("106.00", "100.00", "10.00", "20.00", "0.00", RATE_15, BigDecimal.ZERO, true));

        assertThat(amounts(r.lines())).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("106.00"),
                PLATFORM_PROMOTION_EXPENSE, m("20.00"),
                PLATFORM_REVENUE_COMMISSION, m("-15.00"),
                PLATFORM_REVENUE_FEES, m("-10.00"),
                MERCHANT_PAYABLE, m("-101.00")));
    }

    @Test
    void merchantFundedDiscountReducesTheMerchantPayable() {
        Recognition r = PostingRules.orderDelivered(input("106.00", "100.00", "10.00", "20.00", "0.00", RATE_15, BigDecimal.ZERO, false));

        assertThat(amounts(r.lines())).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("106.00"),
                PLATFORM_REVENUE_COMMISSION, m("-15.00"),
                PLATFORM_REVENUE_FEES, m("-10.00"),
                MERCHANT_PAYABLE, m("-81.00")));
    }

    @Test
    void platformVatSplitsCommissionAndFeesIntoNetRevenueAndVatPayable() {
        Recognition r = PostingRules.orderDelivered(input("126.00", "100.00", "10.00", "0.00", "0.00", RATE_15, new BigDecimal("0.16"), true));

        // 15 / 1.16 = 12.93 (+2.07 VAT); 10 / 1.16 = 8.62 (+1.38 VAT).
        assertThat(amounts(r.lines())).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("126.00"),
                PLATFORM_REVENUE_COMMISSION, m("-12.93"),
                PLATFORM_REVENUE_FEES, m("-8.62"),
                PLATFORM_VAT_PAYABLE, m("-3.45"),
                MERCHANT_PAYABLE, m("-101.00")));
        assertThat(r.commissionAmount()).isEqualByComparingTo("15.00"); // gross commission recorded
    }

    @Test
    void vatInclusiveOrderCreditsTheStoredFeeVatToVatPayableAndLeavesGoodsVatWithTheMerchant() {
        // W6-A order (V064 example): goods 18.00 (VAT 2.48 inside), delivery 5.00 + service 0.90 (VAT 0.69 + 0.12 inside),
        // taxes 0, captured 23.90. Fee VAT comes from the order's quote, not from app.settlement.platform-vat-rate (0).
        Recognition r = PostingRules.orderDelivered(new DeliveredInput(VENDOR, m("23.90"), m("18.00"), m("5.90"), m("0.00"),
                m("0.00"), RATE_15, BigDecimal.ZERO, true, m("0.81")));

        assertThat(amounts(r.lines())).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("23.90"),
                PLATFORM_REVENUE_COMMISSION, m("-2.70"),
                PLATFORM_REVENUE_FEES, m("-5.09"),
                PLATFORM_VAT_PAYABLE, m("-0.81"),
                MERCHANT_PAYABLE, m("-15.30"))); // 18.00 gross goods (incl. 2.48 VAT, the merchant's) − 2.70 commission
        assertThat(r.commissionBasis()).isEqualByComparingTo("18.00");
    }

    @Test
    void storedFeeVatIsNotResplitByPlatformVatRateWhileCommissionStillIs() {
        Recognition r = PostingRules.orderDelivered(new DeliveredInput(VENDOR, m("23.90"), m("18.00"), m("5.90"), m("0.00"),
                m("0.00"), RATE_15, new BigDecimal("0.16"), true, m("0.81")));

        // Commission 2.70 / 1.16 = 2.33 (+0.37 VAT); fees 5.90 − 0.81 stored VAT = 5.09.
        assertThat(amounts(r.lines())).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("23.90"),
                PLATFORM_REVENUE_COMMISSION, m("-2.33"),
                PLATFORM_REVENUE_FEES, m("-5.09"),
                PLATFORM_VAT_PAYABLE, m("-1.18"),
                MERCHANT_PAYABLE, m("-15.30")));
    }

    @Test
    void storedFeeVatIsScaledByRefundsBeforeDeliveryAndReversedByRefundsAfter() {
        // 3.90 of 23.90 refunded before delivery: base 20.00, factor 20/23.90.
        Recognition r = PostingRules.orderDelivered(new DeliveredInput(VENDOR, m("23.90"), m("18.00"), m("5.90"), m("0.00"),
                m("3.90"), RATE_15, BigDecimal.ZERO, true, m("0.81")));

        assertThat(amount(r.lines(), PLATFORM_VAT_PAYABLE)).isEqualByComparingTo("-0.68");   // 0.81 × 20/23.90
        assertThat(amount(r.lines(), PLATFORM_REVENUE_FEES)).isEqualByComparingTo("-4.26");  // 4.94 − 0.68
        assertThat(PostingRules.sum(r.lines())).isEqualByComparingTo("0");

        List<Line> reversal = PostingRules.refundAfterDelivery(r.lines(), m("20.00"));
        Map<LedgerAccountType, BigDecimal> net = amounts(r.lines());
        amounts(reversal).forEach((account, value) -> net.merge(account, value, BigDecimal::add));
        assertThat(net.get(PLATFORM_VAT_PAYABLE)).isEqualByComparingTo("0");
        assertThat(net.get(PLATFORM_REVENUE_FEES)).isEqualByComparingTo("0");
    }

    @Test
    void refundsApprovedBeforeDeliveryScaleTheRecognition() {
        // 26 of 126 refunded before delivery: base 100, factor 100/126.
        Recognition r = PostingRules.orderDelivered(input("126.00", "100.00", "10.00", "0.00", "26.00", RATE_15, BigDecimal.ZERO, true));

        assertThat(amount(r.lines(), CUSTOMER_FUNDS_HELD)).isEqualByComparingTo("100.00");
        assertThat(r.commissionBasis()).isEqualByComparingTo("79.37"); // 100 × 100/126
        assertThat(amount(r.lines(), PLATFORM_REVENUE_COMMISSION)).isEqualByComparingTo("-11.91"); // 15 % of 79.37
        assertThat(amount(r.lines(), PLATFORM_REVENUE_FEES)).isEqualByComparingTo("-7.94");
        assertThat(PostingRules.sum(r.lines())).isEqualByComparingTo("0");
    }

    @Test
    void nothingToRecogniseWhenEverythingWasRefundedBeforeDelivery() {
        Recognition r = PostingRules.orderDelivered(input("126.00", "100.00", "10.00", "0.00", "126.00", RATE_15, BigDecimal.ZERO, true));

        assertThat(r.lines()).isEmpty();
    }

    @Test
    void refundAfterDeliveryReversesProportionallyIntoRefundsPayable() {
        List<Line> delivery = PostingRules.orderDelivered(input("126.00", "100.00", "10.00", "0.00", "0.00", RATE_15, BigDecimal.ZERO, true)).lines();

        List<Line> reversal = PostingRules.refundAfterDelivery(delivery, new BigDecimal("63.00")); // half

        assertThat(amounts(reversal)).containsExactlyInAnyOrderEntriesOf(Map.of(
                PLATFORM_REVENUE_COMMISSION, m("7.50"),
                PLATFORM_REVENUE_FEES, m("5.00"),
                CUSTOMER_REFUNDS_PAYABLE, m("-63.00"),
                MERCHANT_PAYABLE, m("50.50")));
        assertThat(merchantLine(reversal).ownerId()).isEqualTo(VENDOR);
    }

    @Test
    void aFullRefundAfterDeliveryExactlyCancelsTheRecognition() {
        List<Line> delivery = PostingRules.orderDelivered(input("106.00", "100.00", "10.00", "20.00", "0.00", RATE_15, new BigDecimal("0.16"), true)).lines();

        List<Line> reversal = PostingRules.refundAfterDelivery(delivery, new BigDecimal("106.00"));

        Map<LedgerAccountType, BigDecimal> net = amounts(delivery);
        amounts(reversal).forEach((account, value) -> net.merge(account, value, BigDecimal::add));
        net.merge(CUSTOMER_REFUNDS_PAYABLE, m("106.00"), BigDecimal::add); // what the customer is owed back
        net.merge(CUSTOMER_FUNDS_HELD, m("-106.00"), BigDecimal::add);     // what was held
        assertThat(net.values()).allSatisfy(value -> assertThat(value).isEqualByComparingTo("0"));
    }

    @Test
    void roundingResidueLandsOnTheMerchantAndTheReversalBalances() {
        List<Line> delivery = PostingRules.orderDelivered(input("33.33", "27.77", "3.33", "0.00", "0.00", RATE_15, BigDecimal.ZERO, true)).lines();

        List<Line> reversal = PostingRules.refundAfterDelivery(delivery, new BigDecimal("10.01"));

        assertThat(PostingRules.sum(reversal)).isEqualByComparingTo("0");
        assertThat(amount(reversal, CUSTOMER_REFUNDS_PAYABLE)).isEqualByComparingTo("-10.01");
    }

    @Test
    void retainedCancellationFeeGoesToTheMerchantLessCommission() {
        Recognition r = PostingRules.cancellationFeeRetained(VENDOR, new BigDecimal("10.00"), RATE_15, BigDecimal.ZERO);

        assertThat(amounts(r.lines())).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("10.00"),
                PLATFORM_REVENUE_COMMISSION, m("-1.50"),
                MERCHANT_PAYABLE, m("-8.50")));
        assertThat(r.commissionAmount()).isEqualByComparingTo("1.50");
    }

    @Test
    void refundBeforeDeliveryMovesFundsHeldToRefundsPayable() {
        assertThat(amounts(PostingRules.refundBeforeDelivery(new BigDecimal("40")))).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("40.00"), CUSTOMER_REFUNDS_PAYABLE, m("-40.00")));
    }

    /** Property: random orders (pre-W6-A and VAT-inclusive), discounts, VAT and refund sequences always give balanced postings and never overdraw funds held. */
    @Test
    void randomSequencesAlwaysBalanceAndFundsHeldNeverGoesNegative() {
        Random random = new Random(42);
        for (int run = 0; run < 2_000; run++) {
            BigDecimal subtotal = cents(random, 1, 500_000);
            BigDecimal fees = cents(random, 0, 50_000);
            // Half the orders are W6-A VAT-inclusive (taxes 0, fee VAT stored on the quote), half pre-W6-A (VAT added).
            boolean vatInclusive = random.nextBoolean();
            BigDecimal tax = vatInclusive ? BigDecimal.ZERO : subtotal.multiply(new BigDecimal("0.16")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal feesVat = vatInclusive
                    ? fees.multiply(new BigDecimal("0.16")).divide(new BigDecimal("1.16"), 2, RoundingMode.HALF_UP) : null;
            BigDecimal discount = cents(random, 0, subtotal.movePointRight(2).intValue());
            BigDecimal total = subtotal.add(fees).add(tax).subtract(discount);
            BigDecimal rate = new BigDecimal(random.nextInt(31)).movePointLeft(2);
            BigDecimal vat = random.nextBoolean() ? BigDecimal.ZERO : new BigDecimal("0.16");
            BigDecimal before = random.nextInt(4) == 0 ? cents(random, 0, total.movePointRight(2).intValue() / 2) : BigDecimal.ZERO;

            List<Line> all = new ArrayList<>(PostingRules.paymentCaptured(total));
            if (before.signum() > 0) {
                all.addAll(PostingRules.refundBeforeDelivery(before));
            }
            List<Line> delivery = PostingRules.orderDelivered(new DeliveredInput(VENDOR, total, subtotal, fees, discount, before, rate, vat,
                    random.nextBoolean(), feesVat)).lines();
            assertThat(PostingRules.sum(delivery)).isEqualByComparingTo("0");
            if (vatInclusive && before.signum() == 0 && total.signum() > 0) {
                // Fee revenue + VAT payable = fees; with platform VAT off the VAT line is exactly the stored fee VAT.
                assertThat(amount(delivery, PLATFORM_REVENUE_FEES).negate()).isEqualByComparingTo(fees.subtract(feesVat));
                if (vat.signum() == 0) {
                    assertThat(amount(delivery, PLATFORM_VAT_PAYABLE).negate()).isEqualByComparingTo(feesVat);
                }
            }
            all.addAll(delivery);
            BigDecimal remaining = total.subtract(before);
            while (remaining.signum() > 0 && random.nextBoolean()) {
                BigDecimal refund = cents(random, 1, remaining.movePointRight(2).intValue());
                List<Line> reversal = PostingRules.refundAfterDelivery(delivery, refund);
                assertThat(PostingRules.sum(reversal)).isEqualByComparingTo("0");
                all.addAll(reversal);
                remaining = remaining.subtract(refund);
            }
            assertThat(PostingRules.sum(all)).isEqualByComparingTo("0");
            assertThat(amount(all, CUSTOMER_FUNDS_HELD)).isEqualByComparingTo("0"); // fully recognised or refunded
        }
    }

    // --- delivery incidents (LAC-44, W7-B) ---

    @Test
    void incidentCustomerFaultRetainsTheDeliveryFeeAsFeeRevenue() {
        List<Line> lines = PostingRules.incidentSettled(new PostingRules.IncidentInput(VENDOR, m("7.00"), m("0"), m("0"),
                m("10.00"), null, BigDecimal.ZERO));

        assertThat(amounts(lines)).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("7.00"), PLATFORM_REVENUE_FEES, m("-7.00")));
    }

    @Test
    void incidentMerchantFaultDebitsTheMerchantPayable() {
        List<Line> lines = PostingRules.incidentSettled(new PostingRules.IncidentInput(VENDOR, m("0"), m("10.00"), m("0"),
                m("10.00"), null, BigDecimal.ZERO));

        assertThat(amounts(lines)).containsExactlyInAnyOrderEntriesOf(Map.of(
                MERCHANT_PAYABLE, m("10.00"), PLATFORM_REVENUE_FEES, m("-10.00")));
        assertThat(merchantLine(lines).ownerId()).isEqualTo(VENDOR);
    }

    @Test
    void incidentPlatformFaultBooksAnExpenseCreditedToTheMerchant() {
        List<Line> lines = PostingRules.incidentSettled(new PostingRules.IncidentInput(VENDOR, m("0"), m("0"), m("100.00"),
                m("10.00"), null, BigDecimal.ZERO));

        assertThat(amounts(lines)).containsExactlyInAnyOrderEntriesOf(Map.of(
                LedgerAccountType.PLATFORM_INCIDENT_EXPENSE, m("100.00"), MERCHANT_PAYABLE, m("-100.00")));
    }

    @Test
    void incidentFeeRevenueCarriesTheStoredFeeVatProRata() {
        // Fees 10.00 containing 1.38 VAT (16 % inclusive); 7.00 retained → VAT 0.97, net 6.03.
        List<Line> lines = PostingRules.incidentSettled(new PostingRules.IncidentInput(VENDOR, m("7.00"), m("0"), m("0"),
                m("10.00"), m("1.38"), BigDecimal.ZERO));

        assertThat(amounts(lines)).containsExactlyInAnyOrderEntriesOf(Map.of(
                CUSTOMER_FUNDS_HELD, m("7.00"), PLATFORM_REVENUE_FEES, m("-6.03"), PLATFORM_VAT_PAYABLE, m("-0.97")));
        assertThat(PostingRules.sum(lines)).isEqualByComparingTo("0");
    }

    @Test
    void incidentWithNothingToRecordHasNoLines() {
        assertThat(PostingRules.incidentSettled(new PostingRules.IncidentInput(VENDOR, m("0"), m("0"), m("0"), m("10.00"), null,
                BigDecimal.ZERO))).isEmpty();
    }

    // --- helpers ---

    private static DeliveredInput input(String total, String subtotal, String fees, String discount, String refundedBefore,
                                        BigDecimal rate, BigDecimal vat, boolean platformFunded) {
        return new DeliveredInput(VENDOR, m(total), m(subtotal), m(fees), m(discount), m(refundedBefore), rate, vat, platformFunded);
    }

    private static Map<LedgerAccountType, BigDecimal> amounts(List<Line> lines) {
        Map<LedgerAccountType, BigDecimal> map = new HashMap<>();
        lines.forEach(line -> map.merge(line.account(), line.amount(), BigDecimal::add));
        return map;
    }

    private static BigDecimal amount(List<Line> lines, LedgerAccountType account) {
        return amounts(lines).getOrDefault(account, BigDecimal.ZERO);
    }

    private static Line merchantLine(List<Line> lines) {
        return lines.stream().filter(line -> line.account() == MERCHANT_PAYABLE).findFirst().orElseThrow();
    }

    private static BigDecimal cents(Random random, int min, int max) {
        return new BigDecimal(min + random.nextInt(Math.max(1, max - min + 1))).movePointLeft(2);
    }

    private static BigDecimal m(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
