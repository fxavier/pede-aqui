package com.delivery.common.pricing;

import java.math.BigDecimal;

/**
 * VAT category of a product (products.tax_category, V064, W6-A). STANDARD is charged at the tenant's standard VAT
 * rate (fee_policies.tax_rate, 16 % in Mozambique); ZERO (taxable at 0 %) and EXEMPT carry no VAT and differ only
 * for fiscal reporting. Which goods are zero-rated or exempt is a fiscal decision (a validar com contabilista).
 */
public enum TaxCategory {
    STANDARD,
    ZERO,
    EXEMPT;

    /** VAT rate of this category given the tenant's standard rate (fraction, 0.16 = 16 %). */
    public BigDecimal rate(BigDecimal standardRate) {
        return this == STANDARD ? standardRate : BigDecimal.ZERO;
    }
}
