package com.delivery.privacy.dto;

import com.delivery.privacy.entity.AccountDeletionRequest;
import java.time.Instant;
import java.util.UUID;

/** An account deletion request as shown to its subject (W7-C). {@code status}: PENDING, CANCELLED, ERASED, COMPLETED. */
public record DeletionRequestResponse(UUID id, String status, Instant requestedAt, Instant scheduledFor, Instant cancelledAt,
                                      Instant completedAt) {

    /** Maps the entity (no internal fields such as the Keycloak id, attempts or error codes). */
    public static DeletionRequestResponse of(AccountDeletionRequest request) {
        return new DeletionRequestResponse(request.getId(), request.getStatus().name(), request.getRequestedAt(),
                request.getScheduledFor(), request.getCancelledAt(), request.getCompletedAt());
    }
}
