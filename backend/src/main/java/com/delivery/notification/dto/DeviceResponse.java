package com.delivery.notification.dto;

import java.time.Instant;
import java.util.UUID;

/** A registered device; the token is never echoed back, only its last 4 characters ({@code ****abcd}). */
public record DeviceResponse(UUID id, String platform, String tokenMasked, Instant lastSeenAt) {}
