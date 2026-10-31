package com.delivery.privacy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/me/data-export (right of access, LAC-63/W7-C): the personal data the platform holds about the caller —
 * profile, notification preferences, saved addresses, favourite stores, order summaries, consents and deletion
 * requests. Push tokens are never exported (only their count). {@code formatVersion} is bumped on shape changes.
 */
public record PersonalDataExport(
        int formatVersion,
        Instant generatedAt,
        Profile profile,
        Preferences notificationPreferences,
        int registeredPushDevices,
        List<Address> addresses,
        List<UUID> favouriteVendorIds,
        List<OrderSummary> orders,
        List<ConsentRecord> consents,
        List<DeletionRequestResponse> deletionRequests) {

    /** Profile fields (as stored). */
    public record Profile(UUID id, UUID tenantId, String email, String displayName, String fullName, String phone,
                          String phoneE164, Instant phoneVerifiedAt, String nif, LocalDate dateOfBirth, String address,
                          String preferredLocale, String status, Instant createdAt, Instant updatedAt) {}

    /** Notification opt-ins. */
    public record Preferences(boolean push, boolean sms, boolean email) {}

    /** A saved delivery address. */
    public record Address(UUID id, String label, String recipientName, String phone, String line1, String line2, String city,
                          String state, String postalCode, Double latitude, Double longitude, String deliveryInstructions,
                          Instant createdAt) {}

    /** An order of the subject (summary). */
    public record OrderSummary(UUID id, String reference, String status, String cancellationReason, String vendorName,
                               BigDecimal subtotal, BigDecimal fees, BigDecimal taxes, BigDecimal discountTotal,
                               BigDecimal total, String deliveryCity, Instant createdAt) {}

    /** One consent grant (history, newest first). */
    public record ConsentRecord(String purpose, String version, Instant grantedAt, Instant withdrawnAt) {}
}
