package com.delivery.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.rating.service.RatingRules;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/** LAC-28 rating rules: who may rate when, edit window, text normalisation and aggregate rounding. */
class RatingRulesTest {
    private static final Instant DELIVERED_AT = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void deliveredOrderIsRateableUntilTheEndOfTheWindowInclusive() {
        Instant until = RatingRules.rateableUntil(DELIVERED_AT, 7);
        assertThat(until).isEqualTo(Instant.parse("2026-10-27T12:00:00Z"));
        assertThat(RatingRules.notRateableReason("DELIVERED", DELIVERED_AT, false, until, 7)).isEmpty();
        assertThat(RatingRules.notRateableReason("DELIVERED", DELIVERED_AT, false, until.plusMillis(1), 7))
                .contains("rating_window_closed");
    }

    @Test
    void onlyDeliveredOrdersWithAKnownDeliveryInstantAreRateable() {
        Instant now = DELIVERED_AT.plus(1, ChronoUnit.HOURS);
        for (String status : new String[] {"PREPARING", "DELIVERING", "CANCELLED", "REFUNDED", "PICKED_UP"}) {
            assertThat(RatingRules.notRateableReason(status, DELIVERED_AT, false, now, 7)).contains("order_not_delivered");
        }
        assertThat(RatingRules.notRateableReason("DELIVERED", null, false, now, 7)).contains("order_not_delivered");
        assertThat(RatingRules.rateableUntil(null, 7)).isNull();
    }

    @Test
    void anExistingRatingWinsOverEveryOtherReason() {
        assertThat(RatingRules.notRateableReason("PREPARING", null, true, DELIVERED_AT, 7)).contains("rating_already_submitted");
        assertThat(RatingRules.notRateableReason("DELIVERED", DELIVERED_AT, true, DELIVERED_AT.plus(30, ChronoUnit.DAYS), 7))
                .contains("rating_already_submitted");
    }

    @Test
    void ratingIsEditableForTheConfiguredHoursInclusive() {
        Instant created = Instant.parse("2026-10-21T08:00:00Z");
        assertThat(RatingRules.editableUntil(created, 24)).isEqualTo(Instant.parse("2026-10-22T08:00:00Z"));
        assertThat(RatingRules.isEditable(created, created.plus(24, ChronoUnit.HOURS), 24)).isTrue();
        assertThat(RatingRules.isEditable(created, created.plus(24, ChronoUnit.HOURS).plusSeconds(1), 24)).isFalse();
        assertThat(RatingRules.isEditable(created, created.plus(2, ChronoUnit.HOURS), 1)).isFalse();
    }

    @Test
    void textIsTrimmedAndBlankBecomesNull() {
        assertThat(RatingRules.normalizeText("  Bom  ")).isEqualTo("Bom");
        assertThat(RatingRules.normalizeText(" \n\t ")).isNull();
        assertThat(RatingRules.normalizeText("")).isNull();
        assertThat(RatingRules.normalizeText(null)).isNull();
    }

    @Test
    void averageIsRoundedHalfUpToTwoDecimalsAndZeroWithoutRatings() {
        assertThat(RatingRules.average(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(RatingRules.average(13.0 / 3)).isEqualByComparingTo("4.33");
        assertThat(RatingRules.average(4.665)).isEqualByComparingTo("4.67");
        assertThat(RatingRules.average(5)).isEqualByComparingTo("5.00");
    }
}
