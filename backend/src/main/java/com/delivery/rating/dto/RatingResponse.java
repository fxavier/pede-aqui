package com.delivery.rating.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A rating as returned to its viewer. {@code comment} is null when the viewer may not see it (a hidden comment seen by
 * the vendor) — {@code commentHidden} still says it was hidden. {@code commentHiddenReason} is only filled for
 * moderators (OPS/ADMIN/SUPPORT).
 */
public record RatingResponse(
        UUID id,
        UUID orderId,
        UUID vendorId,
        UUID courierId,
        int vendorStars,
        Integer courierStars,
        String comment,
        boolean commentHidden,
        String commentHiddenReason,
        String vendorReply,
        Instant vendorRepliedAt,
        Instant createdAt,
        Instant updatedAt) {}
