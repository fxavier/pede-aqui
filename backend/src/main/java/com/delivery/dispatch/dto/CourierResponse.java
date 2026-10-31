package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.entity.CourierVerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Courier profile data exposed to API clients. {@code status} is the operational state (LAC-42); {@code available}
 * is deprecated and equals status AVAILABLE or BUSY. {@code vehicleType} is one of MOTORBIKE, BICYCLE, CAR, ON_FOOT,
 * OTHER (or null). {@code locationConsent*} record the courier's GPS consent (LAC-40). {@code rating}/{@code ratingCount}
 * are the real rating aggregate (LAC-28).
 */
public record CourierResponse(UUID id, UUID userProfileId, CourierVerificationStatus verificationStatus,
                              @Schema(deprecated = true, description = "Deprecated (LAC-42): true when status is AVAILABLE or BUSY") boolean available,
                              UUID operatingZoneId,
                              String fullName, String phone, String nif, String vehicleType, String vehiclePlate, double rating,
                              CourierStatus status, Instant statusChangedAt, String suspensionReason, int maxActiveDeliveries,
                              Instant locationConsentAcceptedAt, String locationConsentVersion, int ratingCount) {}
