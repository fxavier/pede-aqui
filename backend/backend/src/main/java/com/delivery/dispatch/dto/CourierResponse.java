package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.CourierVerificationStatus;
import java.util.UUID;

/** Courier profile data exposed to API clients. */
public record CourierResponse(UUID id, UUID userProfileId, CourierVerificationStatus verificationStatus, boolean available, UUID operatingZoneId) {}
