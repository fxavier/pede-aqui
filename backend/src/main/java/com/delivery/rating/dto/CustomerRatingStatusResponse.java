package com.delivery.rating.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Rating state of one of the caller's orders, so a client can decide whether to show the rating prompt:
 * {@code rateable} (no rating yet, DELIVERED, within the window) with {@code rateableUntil}; otherwise
 * {@code notRateableReason} = {@code order_not_delivered} | {@code rating_window_closed} | {@code rating_already_submitted}.
 * {@code courierRateable} is true when a courier delivered the order. {@code editable}/{@code editableUntil} refer to
 * the existing {@code rating} (null when there is none).
 */
public record CustomerRatingStatusResponse(
        UUID orderId,
        boolean rateable,
        String notRateableReason,
        Instant rateableUntil,
        boolean courierRateable,
        boolean editable,
        Instant editableUntil,
        RatingResponse rating) {}
