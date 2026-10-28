package com.delivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Immutable copy of the delivery address taken at checkout (V047). Every column is insert-only here and
 * guarded by a database trigger, so later edits or deletion of the customer's saved address never change
 * where an order goes. Null on the order for pre-INC-12 orders and the legacy no-address checkout window.
 */
@Embeddable
public class DeliveryAddressSnapshot {
    @Column(name = "delivery_address_id", updatable = false)
    private UUID sourceAddressId;
    @Column(name = "delivery_recipient_name", updatable = false)
    private String recipientName;
    @Column(name = "delivery_phone", updatable = false)
    private String phone;
    @Column(name = "delivery_line1", updatable = false)
    private String line1;
    @Column(name = "delivery_line2", updatable = false)
    private String line2;
    @Column(name = "delivery_city", updatable = false)
    private String city;
    @Column(name = "delivery_state", updatable = false)
    private String state;
    @Column(name = "delivery_postal_code", updatable = false)
    private String postalCode;
    @Column(name = "delivery_latitude", updatable = false)
    private Double latitude;
    @Column(name = "delivery_longitude", updatable = false)
    private Double longitude;
    @Column(name = "delivery_instructions", updatable = false)
    private String instructions;

    protected DeliveryAddressSnapshot() {}

    public DeliveryAddressSnapshot(UUID sourceAddressId, String recipientName, String phone, String line1, String line2, String city,
                                   String state, String postalCode, Double latitude, Double longitude, String instructions) {
        this.sourceAddressId = sourceAddressId;
        this.recipientName = recipientName;
        this.phone = phone;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.instructions = instructions;
    }

    public UUID getSourceAddressId() { return sourceAddressId; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getInstructions() { return instructions; }
}
