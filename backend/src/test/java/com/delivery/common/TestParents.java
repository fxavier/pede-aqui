package com.delivery.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Minimal parent rows for SQL-seeded Postgres tests. Since V050/V051 (LAC-58) orders, carts and addresses must
 * reference a real customer profile and vendor, and couriers a real zone; seeds use these instead of random ids.
 * Callers delete them with their tenant (children first: orders before app_user_profiles/vendors, couriers before zones).
 */
public final class TestParents {

    private TestParents() {
    }

    /** Inserts a customer profile (app_user_profiles) in the tenant and returns its id. */
    public static UUID customer(JdbcTemplate jdbc, UUID tenantId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'Customer', 'ACTIVE', ?, ?)",
                id, tenantId, "kc-customer-" + id, "customer-" + id + "@test.local", now, now);
        return id;
    }

    /** Inserts an approved, active vendor in the tenant and returns its id. */
    public static UUID vendor(JdbcTemplate jdbc, UUID tenantId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Vendor', 'ACTIVE', 'APPROVED', ?, ?)",
                id, tenantId, now, now);
        return id;
    }

    /** Inserts the given zone id in the tenant (couriers.operating_zone_id references zones). */
    public static UUID zone(JdbcTemplate jdbc, UUID tenantId, UUID zoneId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO zones (id, tenant_id, name, status, created_at, updated_at) VALUES (?, ?, 'Zone', 'ACTIVE', ?, ?) "
                        + "ON CONFLICT (id) DO NOTHING",
                zoneId, tenantId, now, now);
        return zoneId;
    }
}
