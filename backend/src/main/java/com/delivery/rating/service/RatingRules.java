package com.delivery.rating.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Pure rating rules (LAC-28), free of I/O so they are unit-tested directly: who can rate when, how long a rating stays
 * editable, comment normalisation and the aggregate rounding. Error codes returned here are the API codes.
 */
public final class RatingRules {
    public static final String DELIVERED = "DELIVERED";
    public static final String ORDER_NOT_DELIVERED = "order_not_delivered";
    public static final String WINDOW_CLOSED = "rating_window_closed";
    public static final String ALREADY_SUBMITTED = "rating_already_submitted";

    private RatingRules() {
    }

    /** Last instant at which a delivered order can still be rated (inclusive); null when never delivered. */
    public static Instant rateableUntil(Instant deliveredAt, int windowDays) {
        return deliveredAt == null ? null : deliveredAt.plus(Duration.ofDays(windowDays));
    }

    /**
     * Why a new rating is refused, or empty when it is allowed: an existing rating wins over everything, then the
     * order must be DELIVERED (with a known delivery instant), then the window must still be open.
     */
    public static Optional<String> notRateableReason(String orderStatus, Instant deliveredAt, boolean alreadyRated,
                                                     Instant now, int windowDays) {
        if (alreadyRated) {
            return Optional.of(ALREADY_SUBMITTED);
        }
        if (!DELIVERED.equals(orderStatus) || deliveredAt == null) {
            return Optional.of(ORDER_NOT_DELIVERED);
        }
        if (now.isAfter(rateableUntil(deliveredAt, windowDays))) {
            return Optional.of(WINDOW_CLOSED);
        }
        return Optional.empty();
    }

    /** Last instant at which a rating can be edited (inclusive). */
    public static Instant editableUntil(Instant createdAt, int editWindowHours) {
        return createdAt.plus(Duration.ofHours(editWindowHours));
    }

    /** True while the rating is inside its edit window. */
    public static boolean isEditable(Instant createdAt, Instant now, int editWindowHours) {
        return !now.isAfter(editableUntil(createdAt, editWindowHours));
    }

    /** Trims free text; blank becomes null (no empty comments/replies stored). */
    public static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Aggregate average rounded to 2 decimals (HALF_UP); 0 when there are no ratings. */
    public static BigDecimal average(Number rawAverage) {
        if (rawAverage == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(rawAverage.doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }
}
