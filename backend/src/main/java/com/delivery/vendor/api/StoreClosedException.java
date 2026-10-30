package com.delivery.vendor.api;

import com.delivery.common.exception.BusinessException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** 422 {@code store_closed} (LAC-27): the store does not accept orders now; details carry the next opening, if known. */
public class StoreClosedException extends BusinessException {
    private final UUID vendorId;
    private final Instant nextOpeningAt;

    public StoreClosedException(UUID vendorId, Instant nextOpeningAt) {
        super(StoreAvailabilityService.STORE_CLOSED_CODE, "The store is closed and is not accepting orders right now", HttpStatus.UNPROCESSABLE_ENTITY);
        this.vendorId = vendorId;
        this.nextOpeningAt = nextOpeningAt;
    }

    public UUID getVendorId() { return vendorId; }
    public Instant getNextOpeningAt() { return nextOpeningAt; }

    /** Serialized as {@code details}: {@code vendorId} and {@code nextOpeningAt} (ISO-8601, null when unknown). */
    @Override
    public Object getDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("vendorId", vendorId);
        details.put("nextOpeningAt", nextOpeningAt);
        return details;
    }
}
