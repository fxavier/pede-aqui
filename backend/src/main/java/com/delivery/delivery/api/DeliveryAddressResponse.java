package com.delivery.delivery.api;

/**
 * Full delivery address of an order (its checkout snapshot). Personal data: returned only to the order's
 * customer and to the courier currently assigned to its delivery. Part of the delivery API since ADR-0002 W3-A (was
 * {@code order.dto}): the courier address endpoint returns it and the order module maps its snapshot into it
 * ({@code OrderMapper.toAddressResponse}), so delivery does not depend on order.
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
}
