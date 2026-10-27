package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** 409 raised when a cart line's price snapshot no longer matches the SKU's current price (AS-22). */
public class PriceChangedException extends BusinessException {
    /** One cart line whose price moved since it was added to the cart. */
    public record PriceChange(UUID skuId, BigDecimal previousUnitPrice, BigDecimal currentUnitPrice) {}

    private final List<PriceChange> changes;

    public PriceChangedException(List<PriceChange> changes) {
        super("price_changed", "Prices changed since the items were added to the cart; review the cart and confirm again", HttpStatus.CONFLICT);
        this.changes = List.copyOf(changes);
    }

    public List<PriceChange> getChanges() {
        return changes;
    }

    /** Serialized as {@code details.items}: each changed SKU with its old and new unit price. */
    @Override
    public Object getDetails() {
        return Map.of("items", changes);
    }
}
