package com.delivery.settlement.service;

import com.delivery.settlement.entity.LedgerAccountType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure posting rules of the settlement ledger (LAC-33/34, ADR-0005 — model "a validar" with the accountant, LAC-38).
 * Lines are signed: positive = debit, negative = credit; every rule returns a balanced list (Σ = 0) with zero lines
 * dropped. Money is MZN, scale 2, HALF_UP (ADR-0003); rounding residues always land on the merchant payable line so a
 * transaction balances exactly.
 */
public final class PostingRules {
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PostingRules() {}

    /** One signed ledger line; {@code ownerId} is the vendor for MERCHANT_PAYABLE, null for platform accounts. */
    public record Line(LedgerAccountType account, UUID ownerId, BigDecimal amount) {
        public boolean isDebit() { return amount.signum() > 0; }
    }

    /** Lines plus the commission figures recorded in {@code commissions} (gross commission and its basis). */
    public record Recognition(List<Line> lines, BigDecimal commissionBasis, BigDecimal commissionRate, BigDecimal commissionAmount) {}

    /** Inputs of the delivery recognition; {@code total} is what was captured for the order. */
    public record DeliveredInput(UUID vendorId, BigDecimal total, BigDecimal subtotal, BigDecimal fees, BigDecimal discount,
                                 BigDecimal refundedBeforeDelivery, BigDecimal commissionRate, BigDecimal platformVatRate,
                                 boolean discountFundedByPlatform) {}

    /** Payment captured: Dr PLATFORM_CASH_CLEARING / Cr CUSTOMER_FUNDS_HELD. */
    public static List<Line> paymentCaptured(BigDecimal amount) {
        BigDecimal value = money(amount);
        return clean(List.of(
                new Line(LedgerAccountType.PLATFORM_CASH_CLEARING, null, value),
                new Line(LedgerAccountType.CUSTOMER_FUNDS_HELD, null, value.negate())));
    }

    /** Refund approved while the order was not delivered: Dr CUSTOMER_FUNDS_HELD / Cr CUSTOMER_REFUNDS_PAYABLE. */
    public static List<Line> refundBeforeDelivery(BigDecimal amount) {
        BigDecimal value = money(amount);
        return clean(List.of(
                new Line(LedgerAccountType.CUSTOMER_FUNDS_HELD, null, value),
                new Line(LedgerAccountType.CUSTOMER_REFUNDS_PAYABLE, null, value.negate())));
    }

    /**
     * Order delivered. Base = captured total − refunds approved before delivery; when that is less than the total the
     * goods, fees and discount are scaled by base/total first. Commission = rate × (scaled) goods subtotal.
     * <pre>
     * Dr CUSTOMER_FUNDS_HELD              base
     * Dr PLATFORM_PROMOTION_EXPENSE       discount            (platform-funded discount only)
     * Cr PLATFORM_REVENUE_COMMISSION      commission (net of VAT when platform-vat-rate > 0)
     * Cr PLATFORM_REVENUE_FEES            fees (net of VAT)
     * Cr PLATFORM_VAT_PAYABLE             VAT on commission + fees (platform-vat-rate > 0 only)
     * Cr MERCHANT_PAYABLE[vendor]         the rest = subtotal + taxes − commission (− discount when merchant-funded)
     * </pre>
     * Returns no lines when nothing is left to recognise (base ≤ 0).
     */
    public static Recognition orderDelivered(DeliveredInput in) {
        BigDecimal total = money(in.total());
        BigDecimal base = total.subtract(money(in.refundedBeforeDelivery()));
        BigDecimal rate = in.commissionRate();
        if (base.signum() <= 0 || total.signum() <= 0) {
            return new Recognition(List.of(), BigDecimal.ZERO.setScale(SCALE), rate, BigDecimal.ZERO.setScale(SCALE));
        }
        boolean scaled = base.compareTo(total) != 0;
        BigDecimal subtotal = scaled ? scale(in.subtotal(), base, total) : money(in.subtotal());
        BigDecimal fees = scaled ? scale(in.fees(), base, total) : money(in.fees());
        BigDecimal discount = scaled ? scale(in.discount(), base, total) : money(in.discount());
        BigDecimal commission = money(subtotal.multiply(rate));
        BigDecimal[] commissionSplit = vatSplit(commission, in.platformVatRate());
        BigDecimal[] feesSplit = vatSplit(fees, in.platformVatRate());

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(LedgerAccountType.CUSTOMER_FUNDS_HELD, null, base));
        if (in.discountFundedByPlatform()) {
            lines.add(new Line(LedgerAccountType.PLATFORM_PROMOTION_EXPENSE, null, discount));
        }
        lines.add(new Line(LedgerAccountType.PLATFORM_REVENUE_COMMISSION, null, commissionSplit[0].negate()));
        lines.add(new Line(LedgerAccountType.PLATFORM_REVENUE_FEES, null, feesSplit[0].negate()));
        lines.add(new Line(LedgerAccountType.PLATFORM_VAT_PAYABLE, null, commissionSplit[1].add(feesSplit[1]).negate()));
        lines.add(new Line(LedgerAccountType.MERCHANT_PAYABLE, in.vendorId(), sum(lines).negate()));
        return new Recognition(clean(lines), subtotal, rate, commission);
    }

    /**
     * Refund approved after delivery: reverses the delivery recognition in proportion refund / base (base = the
     * CUSTOMER_FUNDS_HELD debit of the delivery transaction), crediting CUSTOMER_REFUNDS_PAYABLE with the refund. Each
     * platform line is reversed rounded; the merchant line takes the residue so the transaction balances.
     */
    public static List<Line> refundAfterDelivery(List<Line> deliveryLines, BigDecimal refund) {
        BigDecimal amount = money(refund);
        BigDecimal base = deliveryLines.stream()
                .filter(line -> line.account() == LedgerAccountType.CUSTOMER_FUNDS_HELD)
                .map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (base.signum() <= 0) {
            throw new IllegalArgumentException("delivery transaction has no customer funds line");
        }
        UUID merchant = deliveryLines.stream()
                .filter(line -> line.account() == LedgerAccountType.MERCHANT_PAYABLE)
                .map(Line::ownerId).findFirst().orElse(null);
        List<Line> lines = new ArrayList<>();
        for (Line line : deliveryLines) {
            if (line.account() == LedgerAccountType.CUSTOMER_FUNDS_HELD || line.account() == LedgerAccountType.MERCHANT_PAYABLE) {
                continue;
            }
            BigDecimal reversed = line.amount().multiply(amount).divide(base, SCALE, ROUNDING).negate();
            lines.add(new Line(line.account(), line.ownerId(), reversed));
        }
        lines.add(new Line(LedgerAccountType.CUSTOMER_REFUNDS_PAYABLE, null, amount.negate()));
        lines.add(new Line(LedgerAccountType.MERCHANT_PAYABLE, merchant, sum(lines).negate()));
        return clean(lines);
    }

    /**
     * Customer cancellation fee withheld (LAC-26): the fee leaves customer funds held; the platform keeps its
     * commission on it and the merchant is owed the rest.
     * Dr CUSTOMER_FUNDS_HELD fee / Cr PLATFORM_REVENUE_COMMISSION (net) / Cr PLATFORM_VAT_PAYABLE / Cr MERCHANT_PAYABLE rest.
     */
    public static Recognition cancellationFeeRetained(UUID vendorId, BigDecimal fee, BigDecimal commissionRate, BigDecimal platformVatRate) {
        BigDecimal amount = money(fee);
        if (amount.signum() <= 0) {
            return new Recognition(List.of(), BigDecimal.ZERO.setScale(SCALE), commissionRate, BigDecimal.ZERO.setScale(SCALE));
        }
        BigDecimal commission = money(amount.multiply(commissionRate));
        BigDecimal[] split = vatSplit(commission, platformVatRate);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(LedgerAccountType.CUSTOMER_FUNDS_HELD, null, amount));
        lines.add(new Line(LedgerAccountType.PLATFORM_REVENUE_COMMISSION, null, split[0].negate()));
        lines.add(new Line(LedgerAccountType.PLATFORM_VAT_PAYABLE, null, split[1].negate()));
        lines.add(new Line(LedgerAccountType.MERCHANT_PAYABLE, vendorId, sum(lines).negate()));
        return new Recognition(clean(lines), amount, commissionRate, commission);
    }

    /** Σ of the signed lines (0 for a balanced transaction). */
    public static BigDecimal sum(List<Line> lines) {
        return lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** {@code [net, vat]} of a VAT-inclusive amount; {@code [amount, 0]} when the rate is zero. */
    static BigDecimal[] vatSplit(BigDecimal grossAmount, BigDecimal vatRate) {
        BigDecimal gross = money(grossAmount);
        if (vatRate == null || vatRate.signum() <= 0) {
            return new BigDecimal[] {gross, BigDecimal.ZERO.setScale(SCALE)};
        }
        BigDecimal net = gross.divide(BigDecimal.ONE.add(vatRate), SCALE, ROUNDING);
        return new BigDecimal[] {net, gross.subtract(net)};
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal numerator, BigDecimal denominator) {
        return money(value).multiply(numerator).divide(denominator, SCALE, ROUNDING);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }

    private static List<Line> clean(List<Line> lines) {
        List<Line> kept = lines.stream()
                .map(line -> new Line(line.account(), line.ownerId(), money(line.amount())))
                .filter(line -> line.amount().signum() != 0)
                .toList();
        if (sum(kept).signum() != 0) {
            throw new IllegalStateException("unbalanced posting " + kept);
        }
        return kept;
    }
}
