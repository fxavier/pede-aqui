package com.delivery.common.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * VAT contained in a checkout (W6-A, LAC-22). With VAT-inclusive prices (fee_policies.tax_inclusive, the default)
 * VAT is extracted, never added: vat = gross × rate / (1 + rate), computed per goods line and per fee (delivery,
 * service), each rounded HALF_UP to 2 decimals and then summed (ADR-0003). A discount is treated as a reduction of
 * the goods price: it is allocated to the goods categories pro rata to their gross (HALF_UP, the last category takes
 * the rounding remainder) and the VAT it contains is removed from the goods VAT; any discount above the goods gross
 * reduces the fees. Platform fees are VAT-inclusive at the standard rate. All of this is a fiscal assumption
 * "a validar com contabilista" (LAC-38).
 *
 * <p>With VAT-exclusive policies (tax_inclusive = false, the pre-W6-A behaviour) the tax line is added on top and is
 * the whole VAT: {@code vatTotal = taxesAdded}, fees carry none.
 *
 * @param taxInclusive        whether prices and fees already contain VAT
 * @param standardRate        standard VAT rate used (fraction)
 * @param goodsStandardGross  goods at the standard rate, after discount
 * @param goodsZeroGross      zero-rated goods, after discount
 * @param goodsExemptGross    exempt goods, after discount
 * @param goodsVat            VAT contained in the goods, after discount
 * @param feesVat             VAT contained in delivery + service fees (after any discount overflow)
 * @param vatTotal            goodsVat + feesVat
 * @param grossTotal          total charged to the customer
 * @param netTotal            grossTotal − vatTotal
 */
public record VatBreakdown(
        boolean taxInclusive,
        BigDecimal standardRate,
        BigDecimal goodsStandardGross,
        BigDecimal goodsZeroGross,
        BigDecimal goodsExemptGross,
        BigDecimal goodsVat,
        BigDecimal feesVat,
        BigDecimal vatTotal,
        BigDecimal grossTotal,
        BigDecimal netTotal) {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    /** VAT contained in a VAT-inclusive amount: amount × rate / (1 + rate), HALF_UP, 2 decimals. */
    public static BigDecimal vatIn(BigDecimal gross, BigDecimal rate) {
        if (gross == null || rate == null || gross.signum() == 0 || rate.signum() == 0) {
            return ZERO;
        }
        return gross.multiply(rate).divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
    }

    /**
     * Extracts the VAT of a checkout. {@code taxesAdded} is the tax line charged on top (zero when inclusive) and
     * {@code total} the amount charged (subtotal + fees + taxesAdded − discount).
     */
    public static VatBreakdown of(boolean taxInclusive, BigDecimal standardRate, List<TaxableLine> lines,
                                  BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal discountTotal,
                                  BigDecimal taxesAdded, BigDecimal total) {
        Map<TaxCategory, BigDecimal> gross = new EnumMap<>(TaxCategory.class);
        BigDecimal lineVat = ZERO;
        for (TaxableLine line : lines) {
            gross.merge(line.taxCategory(), line.gross(), BigDecimal::add);
            if (taxInclusive) {
                lineVat = lineVat.add(vatIn(line.gross(), line.taxCategory().rate(standardRate)));
            }
        }
        BigDecimal goodsGross = gross.values().stream().reduce(ZERO, BigDecimal::add);
        BigDecimal discount = discountTotal == null ? ZERO : discountTotal.max(ZERO);
        BigDecimal goodsDiscount = discount.min(goodsGross);
        BigDecimal feesDiscount = discount.subtract(goodsDiscount);

        Map<TaxCategory, BigDecimal> discountShare = allocate(gross, goodsGross, goodsDiscount);
        BigDecimal standardAfter = scale(gross.getOrDefault(TaxCategory.STANDARD, ZERO).subtract(discountShare.get(TaxCategory.STANDARD)));
        BigDecimal zeroAfter = scale(gross.getOrDefault(TaxCategory.ZERO, ZERO).subtract(discountShare.get(TaxCategory.ZERO)));
        BigDecimal exemptAfter = scale(gross.getOrDefault(TaxCategory.EXEMPT, ZERO).subtract(discountShare.get(TaxCategory.EXEMPT)));

        if (!taxInclusive) {
            BigDecimal vat = scale(taxesAdded);
            return new VatBreakdown(false, standardRate, standardAfter, zeroAfter, exemptAfter, vat, ZERO, vat,
                    scale(total), scale(total.subtract(vat)));
        }
        BigDecimal goodsVat = lineVat.subtract(vatIn(discountShare.get(TaxCategory.STANDARD), standardRate)).max(ZERO);
        BigDecimal feesVat = vatIn(deliveryFee, standardRate).add(vatIn(serviceFee, standardRate))
                .subtract(vatIn(feesDiscount, standardRate)).max(ZERO);
        BigDecimal vatTotal = goodsVat.add(feesVat);
        return new VatBreakdown(true, standardRate, standardAfter, zeroAfter, exemptAfter, scale(goodsVat), scale(feesVat),
                scale(vatTotal), scale(total), scale(total.subtract(vatTotal)));
    }

    /** Pro-rata split of {@code amount} over the categories by gross; the last category with gross takes the remainder. */
    private static Map<TaxCategory, BigDecimal> allocate(Map<TaxCategory, BigDecimal> gross, BigDecimal goodsGross, BigDecimal amount) {
        Map<TaxCategory, BigDecimal> share = new EnumMap<>(TaxCategory.class);
        for (TaxCategory category : TaxCategory.values()) {
            share.put(category, ZERO);
        }
        if (amount.signum() == 0 || goodsGross.signum() == 0) {
            return share;
        }
        List<TaxCategory> present = gross.entrySet().stream().filter(e -> e.getValue().signum() > 0).map(Map.Entry::getKey).toList();
        BigDecimal remaining = amount;
        for (int i = 0; i < present.size(); i++) {
            TaxCategory category = present.get(i);
            BigDecimal part = i == present.size() - 1 ? remaining
                    : amount.multiply(gross.get(category)).divide(goodsGross, 2, RoundingMode.HALF_UP);
            share.put(category, part);
            remaining = remaining.subtract(part);
        }
        return share;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
