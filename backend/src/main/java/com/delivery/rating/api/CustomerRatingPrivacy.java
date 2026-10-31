package com.delivery.rating.api;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rating side of the data-subject rights (LAC-19 erasure, W7-C, extended for the W7-D ratings API): the customer's
 * free-text {@code ratings.comment} is the only personal data a rating holds, so an account erasure clears it.
 *
 * <p>The rating row itself is kept: its stars still count in the vendor/courier averages ({@code rating_count} and
 * {@code rating} stay consistent) and {@code rater_user_id} keeps pointing at the profile, which the erasure
 * pseudonymises but never deletes ({@code fk_ratings_rater_user_id}, V072). The vendor's reply and the moderation
 * fields are not the customer's data and stay as they are.
 */
@Component
public class CustomerRatingPrivacy {
    private final JdbcTemplate jdbc;

    public CustomerRatingPrivacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Clears the comment of every rating the customer wrote in the tenant. Runs inside the caller's erasure
     * transaction; returns the number of ratings changed. Idempotent (a second call changes nothing).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int pseudonymiseCustomerComments(UUID tenantId, UUID customerProfileId) {
        return jdbc.update("""
                UPDATE ratings
                   SET comment = NULL, updated_at = now(), version = version + 1
                 WHERE tenant_id = ? AND rater_user_id = ? AND comment IS NOT NULL""",
                tenantId, customerProfileId);
    }
}
