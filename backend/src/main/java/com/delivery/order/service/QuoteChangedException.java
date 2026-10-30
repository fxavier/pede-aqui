package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.order.dto.CheckoutQuoteResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 409 quote_changed (W6-A): the checkout quote the customer confirmed expired or its prices/fees no longer hold.
 * {@code details.reason} is {@code expired | prices_changed | amounts_changed}; {@code details.quote} is a fresh quote
 * (new quoteId) to show and resubmit; {@code details.items} lists repriced SKUs when the reason is prices_changed.
 */
public class QuoteChangedException extends BusinessException {
    /** Why the quote no longer holds. */
    public enum Reason { EXPIRED, PRICES_CHANGED, AMOUNTS_CHANGED }

    private final Reason reason;
    private final transient CheckoutQuoteResponse freshQuote;
    private final transient List<PriceChangedException.PriceChange> items;

    public QuoteChangedException(Reason reason) {
        this(reason, null, List.of());
    }

    private QuoteChangedException(Reason reason, CheckoutQuoteResponse freshQuote, List<PriceChangedException.PriceChange> items) {
        super("quote_changed", "The checkout quote expired or its prices/fees changed; review the new quote and confirm again", HttpStatus.CONFLICT);
        this.reason = reason;
        this.freshQuote = freshQuote;
        this.items = List.copyOf(items);
    }

    /** Same failure carrying the fresh quote (and repriced SKUs) for the response. */
    public QuoteChangedException withFreshQuote(CheckoutQuoteResponse quote, List<PriceChangedException.PriceChange> changedItems) {
        return new QuoteChangedException(reason, quote, changedItems == null ? List.of() : changedItems);
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public Object getDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason.name().toLowerCase());
        if (freshQuote != null) {
            details.put("quote", freshQuote);
        }
        if (!items.isEmpty()) {
            details.put("items", items);
        }
        return details;
    }
}
