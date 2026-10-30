package com.delivery.geo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** LAC-51: the vendorSearch cache key carries every filter, the tenant and ~100 m rounded coordinates. */
class VendorSearchQueryTest {
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void roundsCoordinatesToThreeDecimalsAndNormalisesDefaults() {
        VendorSearchQuery q = VendorSearchQuery.of(TENANT, -25.96551, 32.58349, null, null, null, null, null);

        assertThat(q.latitude()).isEqualTo(-25.966);
        assertThat(q.longitude()).isEqualTo(32.583);
        assertThat(q.radiusMeters()).isEqualTo(5000);
        assertThat(q.sort()).isEqualTo("RELEVANCE");
    }

    @Test
    void keyIncludesEveryFilterAndTheTenant() {
        String base = key(TENANT, 4.0, 30);

        assertThat(base).isEqualTo(key(TENANT, 4.0, 30));
        assertThat(key(TENANT, 4.5, 30)).isNotEqualTo(base);
        assertThat(key(TENANT, 4.0, 45)).isNotEqualTo(base);
        assertThat(key(UUID.randomUUID(), 4.0, 30)).isNotEqualTo(base);
        assertThat(key(null, 4.0, 30)).startsWith("*|");
    }

    @Test
    void nullCoordinatesStayNull() {
        VendorSearchQuery q = VendorSearchQuery.of(null, null, null, 1000, "c", null, null, "rating");

        assertThat(q.latitude()).isNull();
        assertThat(q.cacheKey()).isEqualTo("*|null|null|1000|c|null|null|RATING");
    }

    private static String key(UUID tenant, Double minRating, Integer maxMinutes) {
        return VendorSearchQuery.of(tenant, -25.965, 32.583, 5000, null, minRating, maxMinutes, "DISTANCE").cacheKey();
    }
}
