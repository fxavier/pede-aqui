package com.delivery.dispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a courier profile used by dispatch assignment logic. Operational state is {@link #getStatus()} (LAC-42,
 * V060); the legacy {@code available} column is kept in step with it (= status AVAILABLE or BUSY) and is deprecated.
 */
@Entity
@Table(name = "couriers")
public class Courier {
    /** The only capacity the V035 index uq_deliveries_active_courier allows (V060 CHECK). */
    public static final int MVP_MAX_ACTIVE_DELIVERIES = 1;

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "user_profile_id", nullable = false)
    private UUID userProfileId;
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private CourierVerificationStatus verificationStatus;
    @Column(nullable = false)
    private boolean available;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourierStatus status;
    @Column(name = "status_changed_at")
    private Instant statusChangedAt;
    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;
    @Column(name = "max_active_deliveries", nullable = false)
    private short maxActiveDeliveries;
    @Column(name = "operating_zone_id")
    private UUID operatingZoneId;
    /** Average stars, maintained by the rating module (LAC-28) through the api port; read-only here. */
    @Column(nullable = false, insertable = false, updatable = false)
    private double rating;
    @Column(name = "rating_count", nullable = false, insertable = false, updatable = false)
    private int ratingCount;
    @Column(name = "full_name")
    private String fullName;
    @Column
    private String phone;
    @Column
    private String nif;
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type")
    private CourierVehicleType vehicleType;
    @Column(name = "vehicle_plate")
    private String vehiclePlate;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "location_consent_accepted_at")
    private Instant locationConsentAcceptedAt;
    @Column(name = "location_consent_version", length = 40)
    private String locationConsentVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Courier() {}

    public Courier(UUID id, UUID tenantId, UUID userProfileId, UUID operatingZoneId) {
        this.id = id;
        this.tenantId = tenantId;
        this.userProfileId = userProfileId;
        this.verificationStatus = CourierVerificationStatus.PENDING;
        this.status = CourierStatus.OFFLINE;
        this.available = false;
        this.maxActiveDeliveries = MVP_MAX_ACTIVE_DELIVERIES;
        this.operatingZoneId = operatingZoneId;
        this.rating = 0.0d;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.statusChangedAt = this.createdAt;
    }

    public void approve() { this.verificationStatus = CourierVerificationStatus.APPROVED; this.updatedAt = Instant.now(); }

    /** Rejects the registration; an on-duty courier goes OFFLINE (a SUSPENDED one stays suspended). */
    public void reject() {
        this.verificationStatus = CourierVerificationStatus.REJECTED;
        if (status != CourierStatus.SUSPENDED && status != CourierStatus.OFFLINE) {
            changeStatus(CourierStatus.OFFLINE);
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Legacy availability toggle (pre-LAC-42 callers and tests): true = AVAILABLE, false = OFFLINE. Prefer
     * {@link #changeStatus(CourierStatus)}.
     */
    public void setAvailable(boolean available) { changeStatus(available ? CourierStatus.AVAILABLE : CourierStatus.OFFLINE); }

    /** Sets the operational status (callers enforce the transition rules) and keeps the legacy flag derived. */
    public void changeStatus(CourierStatus next) {
        if (next != status) {
            this.status = next;
            this.statusChangedAt = Instant.now();
        }
        this.available = next.isOnDuty();
        if (next != CourierStatus.SUSPENDED) {
            this.suspensionReason = null;
        }
        this.updatedAt = Instant.now();
    }

    /** OPS suspension with a reason (kept on the row only; never logged or audited as text). */
    public void suspend(String reason) {
        changeStatus(CourierStatus.SUSPENDED);
        this.suspensionReason = reason;
    }

    /** Records the courier's explicit GPS consent (LAC-40): when and which consent text version. */
    public void acceptLocationConsent(String version, Instant acceptedAt) {
        this.locationConsentVersion = version;
        this.locationConsentAcceptedAt = acceptedAt;
        this.updatedAt = Instant.now();
    }

    /** Withdraws the GPS consent; callers take the courier off duty first. */
    public void withdrawLocationConsent() {
        this.locationConsentVersion = null;
        this.locationConsentAcceptedAt = null;
        this.updatedAt = Instant.now();
    }

    /** True when the courier accepted exactly the given consent text version. */
    public boolean hasLocationConsent(String currentVersion) {
        return locationConsentAcceptedAt != null && currentVersion != null && currentVersion.equals(locationConsentVersion);
    }

    public void updateProfile(String fullName, String phone, String nif, CourierVehicleType vehicleType, String vehiclePlate, LocalDate dateOfBirth) {
        this.fullName = fullName;
        this.phone = phone;
        this.nif = nif;
        this.vehicleType = vehicleType;
        this.vehiclePlate = vehiclePlate;
        this.dateOfBirth = dateOfBirth;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserProfileId() { return userProfileId; }
    public CourierVerificationStatus getVerificationStatus() { return verificationStatus; }
    /** Deprecated mirror of {@link #getStatus()} (AVAILABLE or BUSY). */
    public boolean isAvailable() { return available; }
    public CourierStatus getStatus() { return status; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public String getSuspensionReason() { return suspensionReason; }
    public int getMaxActiveDeliveries() { return maxActiveDeliveries; }
    public UUID getOperatingZoneId() { return operatingZoneId; }
    public double getRating() { return rating; }
    public int getRatingCount() { return ratingCount; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getNif() { return nif; }
    public CourierVehicleType getVehicleType() { return vehicleType; }
    public String getVehiclePlate() { return vehiclePlate; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Instant getLocationConsentAcceptedAt() { return locationConsentAcceptedAt; }
    public String getLocationConsentVersion() { return locationConsentVersion; }
}
