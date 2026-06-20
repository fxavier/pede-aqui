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

/** Represents a courier profile used by dispatch assignment logic. */
@Entity
@Table(name = "couriers")
public class Courier {
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
    @Column(name = "operating_zone_id")
    private UUID operatingZoneId;
    @Column(nullable = false)
    private double rating;
    @Column(name = "full_name")
    private String fullName;
    @Column
    private String phone;
    @Column
    private String nif;
    @Column(name = "vehicle_type")
    private String vehicleType;
    @Column(name = "vehicle_plate")
    private String vehiclePlate;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
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
        this.available = false;
        this.operatingZoneId = operatingZoneId;
        this.rating = 0.0d;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void approve() { this.verificationStatus = CourierVerificationStatus.APPROVED; this.updatedAt = Instant.now(); }
    public void reject() { this.verificationStatus = CourierVerificationStatus.REJECTED; this.available = false; this.updatedAt = Instant.now(); }
    public void setAvailable(boolean available) { this.available = available; this.updatedAt = Instant.now(); }
    
    public void updateProfile(String fullName, String phone, String nif, String vehicleType, String vehiclePlate, LocalDate dateOfBirth) {
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
    public boolean isAvailable() { return available; }
    public UUID getOperatingZoneId() { return operatingZoneId; }
    public double getRating() { return rating; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getNif() { return nif; }
    public String getVehicleType() { return vehicleType; }
    public String getVehiclePlate() { return vehiclePlate; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
}
