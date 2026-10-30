package com.delivery.order.service;

import com.delivery.common.exception.NotFoundException;
import com.delivery.order.entity.CheckoutQuote;
import com.delivery.order.repository.CheckoutQuoteRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores and loads checkout quotes with a validity window (W6-A). TTL = {@code app.checkout.quote-ttl} (default 10
 * min). Quotes are owner-scoped: another customer's (or tenant's) quote id answers 404 like a missing one.
 */
@Component
public class CheckoutQuoteStore {
    private final CheckoutQuoteRepository repository;
    private final Duration ttl;

    public CheckoutQuoteStore(CheckoutQuoteRepository repository, @Value("${app.checkout.quote-ttl:PT10M}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("app.checkout.quote-ttl must be positive");
        }
        this.repository = repository;
        this.ttl = ttl;
    }

    /** Persists a new quote valid from now for the configured TTL (runs in the caller's transaction). */
    public CheckoutQuote save(UUID tenantId, UUID customerId, UUID cartId, UUID addressId, UUID vendorId, CheckoutQuote.Amounts amounts) {
        Instant now = Instant.now();
        return repository.save(new CheckoutQuote(UUID.randomUUID(), tenantId, customerId, cartId, addressId, vendorId, amounts, now, now.plus(ttl)));
    }

    /** The caller's quote; 404 when it does not exist, belongs to another tenant or to another customer. */
    public CheckoutQuote requireOwn(UUID tenantId, UUID customerId, UUID quoteId) {
        return repository.findByTenantIdAndId(tenantId, quoteId)
                .filter(quote -> quote.getCustomerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("Checkout quote was not found"));
    }

    /** Deletes quotes that expired more than {@code grace} ago (checkout-quote-cleanup job); returns rows removed. */
    @Transactional
    public int purgeExpired(Duration grace) {
        return repository.deleteExpiredBefore(Instant.now().minus(grace));
    }
}
