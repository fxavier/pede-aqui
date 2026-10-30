package com.delivery.vendor.dto;

import com.delivery.vendor.entity.VendorClosure;
import java.time.Instant;
import java.util.UUID;

/** Exceptional store closure as returned by the closures API (LAC-27). */
public record VendorClosureResponse(UUID id, UUID vendorId, Instant startsAt, Instant endsAt, String reason, Instant createdAt) {
    /** Maps the entity; created_by is internal (audit carries the actor). */
    public static VendorClosureResponse of(VendorClosure closure) {
        return new VendorClosureResponse(closure.getId(), closure.getVendorId(), closure.getStartsAt(), closure.getEndsAt(),
                closure.getReason(), closure.getCreatedAt());
    }
}
