package com.delivery.common.pricing;

import java.math.BigDecimal;

/** One priced cart/order line for VAT purposes: the gross amount charged (unit price × quantity) and its category. */
public record TaxableLine(BigDecimal gross, TaxCategory taxCategory) {

    public TaxableLine {
        taxCategory = taxCategory == null ? TaxCategory.STANDARD : taxCategory;
    }
}
