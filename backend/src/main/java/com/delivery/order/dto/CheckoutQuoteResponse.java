package com.delivery.order.dto;

import com.delivery.common.pricing.DeliveryFeeBasis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Checkout price quote (GET /api/v1/checkout/quote, INC-13): the fees checkout will charge for this cart and
 * address. distanceKm is the straight-line distance used, null when vendor or address has no coordinates
 * (deliveryFeeBasis then tells which fallback applied).
 *
 * <p>W6-A (additive): {@code quoteId} + {@code expiresAt} identify the stored quote — send quoteId on POST /checkout
 * to be charged exactly this or get 409 quote_changed. {@code taxes} is the tax line added on top (0 when prices
 * include VAT, the default); {@code vatIncluded} is the VAT contained in {@code total} ("IVA incluído"), split into
 * goods and fees, and {@code netTotal} = total − vatIncluded. VAT treatment: a validar com contabilista.
 */
public record CheckoutQuoteResponse(
        UUID cartId,
        UUID addressId,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal serviceFee,
        BigDecimal taxes,
        BigDecimal discountTotal,
        BigDecimal total,
        DeliveryFeeBasis deliveryFeeBasis,
        BigDecimal distanceKm,
        UUID quoteId,
        Instant expiresAt,
        boolean taxInclusive,
        BigDecimal vatRate,
        BigDecimal vatIncluded,
        BigDecimal goodsVat,
        BigDecimal feesVat,
        BigDecimal netTotal) {}
