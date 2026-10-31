package com.delivery.customer.api;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-module personal data for the data-subject rights (LAC-19/LAC-63, W7-C): the saved addresses and favourite
 * stores of a customer, for the personal-data export, and their deletion when the account is pseudonymised. Favourite
 * rows are keyed by the profile id or, in older rows, by the Keycloak subject (V014/V050), so both are matched.
 */
@Component
public class CustomerPersonalData {
    /** One saved address, as exported to its owner. */
    public record SavedAddress(UUID id, String label, String recipientName, String phone, String line1, String line2,
                               String city, String state, String postalCode, Double latitude, Double longitude,
                               String deliveryInstructions, Instant createdAt) {}

    /** Result of an erasure: rows deleted per kind. */
    public record Erasure(int addresses, int favourites) {}

    private final JdbcTemplate jdbc;

    public CustomerPersonalData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The customer's saved addresses in the tenant, oldest first. */
    @Transactional(readOnly = true)
    public List<SavedAddress> addresses(UUID tenantId, UUID customerProfileId) {
        return jdbc.query("""
                SELECT id, label, recipient_name, phone, line1, line2, city, state, postal_code, latitude, longitude,
                       delivery_instructions, created_at
                  FROM customer_addresses WHERE tenant_id = ? AND customer_id = ? ORDER BY created_at, id""",
                (rs, i) -> new SavedAddress(rs.getObject("id", UUID.class), rs.getString("label"), rs.getString("recipient_name"),
                        rs.getString("phone"), rs.getString("line1"), rs.getString("line2"), rs.getString("city"),
                        rs.getString("state"), rs.getString("postal_code"), (Double) rs.getObject("latitude"),
                        (Double) rs.getObject("longitude"), rs.getString("delivery_instructions"),
                        instant(rs.getTimestamp("created_at"))),
                tenantId, customerProfileId);
    }

    /** Vendor ids of the customer's favourite stores in the tenant. */
    @Transactional(readOnly = true)
    public List<UUID> favouriteVendorIds(UUID tenantId, UUID customerProfileId, String keycloakUserId) {
        List<UUID> keys = keys(customerProfileId, keycloakUserId);
        return jdbc.queryForList("SELECT vendor_id FROM customer_favorite_vendors WHERE tenant_id = ? AND customer_id = ANY (?) ORDER BY created_at",
                UUID.class, tenantId, keys.toArray(UUID[]::new));
    }

    /** Deletes the customer's saved addresses and favourite stores; runs in the caller's erasure transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Erasure erase(UUID tenantId, UUID customerProfileId, String keycloakUserId) {
        int addresses = jdbc.update("DELETE FROM customer_addresses WHERE tenant_id = ? AND customer_id = ?", tenantId, customerProfileId);
        int favourites = jdbc.update("DELETE FROM customer_favorite_vendors WHERE tenant_id = ? AND customer_id = ANY (?)",
                tenantId, keys(customerProfileId, keycloakUserId).toArray(UUID[]::new));
        return new Erasure(addresses, favourites);
    }

    private static List<UUID> keys(UUID customerProfileId, String keycloakUserId) {
        List<UUID> keys = new ArrayList<>(List.of(customerProfileId));
        parseUuid(keycloakUserId).ifPresent(keys::add);
        return keys;
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
