package com.delivery.privacy.dto;

import java.time.Instant;

/**
 * State of one consent purpose for the subject (W7-C): the current text version, and the subject's latest grant
 * ({@code version}/{@code grantedAt}, null when never granted) with its withdrawal. {@code active} = granted, not
 * withdrawn and for the current version.
 */
public record ConsentResponse(String purpose, String currentVersion, String version, Instant grantedAt, Instant withdrawnAt,
                              boolean active) {
}
