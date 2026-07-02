package com.delivery.catalog.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "service_slots")
public class ServiceSlot {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private Integer capacity = 1;

    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount = 0;

    @Column(nullable = false)
    private Boolean available = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceSlot() {
    }

    public ServiceSlot(UUID id, UUID tenantId, UUID vendorId, Integer dayOfWeek, 
                      LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.tenantId = tenantId;
        this.vendorId = vendorId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = 1;
        this.bookedCount = 0;
        this.available = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
        this.updatedAt = Instant.now();
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        this.updatedAt = Instant.now();
    }

    public void incrementBookedCount() {
        this.bookedCount++;
        this.updatedAt = Instant.now();
    }

    public void decrementBookedCount() {
        if (this.bookedCount > 0) {
            this.bookedCount--;
            this.updatedAt = Instant.now();
        }
    }

    public void setAvailable(Boolean available) {
        this.available = available;
        this.updatedAt = Instant.now();
    }

    public boolean hasAvailableCapacity() {
        return available && bookedCount < capacity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public UUID getProductId() {
        return productId;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public Integer getBookedCount() {
        return bookedCount;
    }

    public Boolean getAvailable() {
        return available;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}