package com.delivery.order.dto;

import com.delivery.order.entity.DeliveryAddressSnapshot;

/**
 * Full delivery address of an order (its checkout snapshot). Personal data: returned only to the order's
 * customer and to the courier currently assigned to its delivery.
 */
public record DeliveryAddressResponse(
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        Double latitude,
        Double longitude,
        String instructions) {

    /** Maps a snapshot; null in, null out (orders placed without an address). */
    public static DeliveryAddressResponse from(DeliveryAddressSnapshot s) {
        if (s == null) {
            return null;
        }
        return new DeliveryAddressResponse(s.getRecipientName(), s.getPhone(), s.getLine1(), s.getLine2(), s.getCity(), s.getState(),
                s.getPostalCode(), s.getLatitude(), s.getLongitude(), s.getInstructions());
    }
}
