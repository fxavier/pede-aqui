package com.delivery.common.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** W6-A VAT extraction: per-line HALF_UP rounding, tax categories, discount allocation and the exclusive mode. */
class VatBreakdownTest {
    private static final BigDecimal RATE = new BigDecimal("0.16");

    @Test
    void extractsVatFromEachLineAndFeeRoundingEachHalfUp() {
        // 3 × 0.10 lines: per line 0.0138 → 0.01 each = 0.03 (whole-basket extraction would give 0.04): per-line rounding is pinned.
        List<TaxableLine> lines = List.of(line("0.10"), line("0.10"), line("0.10"));
        VatBreakdown vat = VatBreakdown.of(true, RATE, lines, new BigDecimal("5.00"), new BigDecimal("0.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("5.30"));

        assertThat(vat.goodsVat()).isEqualByComparingTo("0.03");
        assertThat(vat.feesVat()).isEqualByComparingTo("0.69");
        assertThat(vat.vatTotal()).isEqualByComparingTo("0.72");
        assertThat(vat.netTotal()).isEqualByComparingTo("4.58");
    }

    @Test
    void zeroRatedAndExemptGoodsCarryNoVatButAreReportedByCategory() {
        List<TaxableLine> lines = List.of(line("116.00"), new TaxableLine(new BigDecimal("50.00"), TaxCategory.ZERO),
                new TaxableLine(new BigDecimal("30.00"), TaxCategory.EXEMPT));
        VatBreakdown vat = VatBreakdown.of(true, RATE, lines, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("196.00"));

        assertThat(vat.goodsVat()).isEqualByComparingTo("16.00");
        assertThat(vat.goodsStandardGross()).isEqualByComparingTo("116.00");
        assertThat(vat.goodsZeroGross()).isEqualByComparingTo("50.00");
        assertThat(vat.goodsExemptGross()).isEqualByComparingTo("30.00");
        assertThat(vat.netTotal()).isEqualByComparingTo("180.00");
    }

    @Test
    void discountIsAllocatedProRataAcrossCategoriesAndReducesTheGoodsVat() {
        // 100 standard + 100 exempt, discount 20 → 10 each; standard VAT 13.79 − vatIn(10.00)=1.38 → 12.41.
        List<TaxableLine> lines = List.of(line("100.00"), new TaxableLine(new BigDecimal("100.00"), TaxCategory.EXEMPT));
        VatBreakdown vat = VatBreakdown.of(true, RATE, lines, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.00"),
                BigDecimal.ZERO, new BigDecimal("180.00"));

        assertThat(vat.goodsStandardGross()).isEqualByComparingTo("90.00");
        assertThat(vat.goodsExemptGross()).isEqualByComparingTo("90.00");
        assertThat(vat.goodsVat()).isEqualByComparingTo("12.41");
    }

    @Test
    void discountAboveTheGoodsReducesTheFeesVat() {
        VatBreakdown vat = VatBreakdown.of(true, RATE, List.of(line("10.00")), new BigDecimal("11.60"), BigDecimal.ZERO,
                new BigDecimal("15.80"), BigDecimal.ZERO, new BigDecimal("5.80"));

        assertThat(vat.goodsVat()).isEqualByComparingTo("0.00");
        // fees 11.60 → 1.60, minus vatIn(5.80) = 0.80
        assertThat(vat.feesVat()).isEqualByComparingTo("0.80");
    }

    @Test
    void exclusiveModeReportsTheAddedTaxLineAsTheWholeVat() {
        VatBreakdown vat = VatBreakdown.of(false, new BigDecimal("0.08"), List.of(line("18.00")), new BigDecimal("5.00"),
                new BigDecimal("0.90"), BigDecimal.ZERO, new BigDecimal("1.44"), new BigDecimal("25.34"));

        assertThat(vat.taxInclusive()).isFalse();
        assertThat(vat.vatTotal()).isEqualByComparingTo("1.44");
        assertThat(vat.feesVat()).isEqualByComparingTo("0.00");
        assertThat(vat.netTotal()).isEqualByComparingTo("23.90");
    }

    private static TaxableLine line(String gross) {
        return new TaxableLine(new BigDecimal(gross), TaxCategory.STANDARD);
    }
}
