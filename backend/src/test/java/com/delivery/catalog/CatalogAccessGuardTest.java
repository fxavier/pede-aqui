package com.delivery.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CatalogAccessGuardTest {
    private final CatalogAccessGuard guard = new CatalogAccessGuard();
    private final UUID vendorId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void vendorAdminWithMatchingVendorClaimIsAllowed() {
        authenticate("ROLE_VENDOR_ADMIN", Map.of("vendor_id", vendorId.toString()));
        assertThatCode(() -> guard.ensureCanManageVendor(vendorId)).doesNotThrowAnyException();
    }

    @Test
    void vendorAdminWithDifferentVendorClaimIsDenied() {
        authenticate("ROLE_VENDOR_ADMIN", Map.of("vendor_id", UUID.randomUUID().toString()));
        assertThatThrownBy(() -> guard.ensureCanManageVendor(vendorId))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void vendorAdminWithoutVendorClaimFallsBackToTenantScope() {
        // Merchant registration provisions one vendor per tenant, so tenant scoping is the boundary.
        authenticate("ROLE_VENDOR_ADMIN", Map.of());
        assertThatCode(() -> guard.ensureCanManageVendor(vendorId)).doesNotThrowAnyException();
    }

    @Test
    void opsAndAdminAreTenantWideEvenWithForeignVendorClaim() {
        authenticate("ROLE_OPS", Map.of("vendor_id", UUID.randomUUID().toString()));
        assertThatCode(() -> guard.ensureCanManageVendor(vendorId)).doesNotThrowAnyException();

        authenticate("ROLE_ADMIN", Map.of("vendor_id", UUID.randomUUID().toString()));
        assertThatCode(() -> guard.ensureCanManageVendor(vendorId)).doesNotThrowAnyException();
    }

    private void authenticate(String role, Map<String, String> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        extraClaims.forEach(builder::claim);
        Jwt jwt = builder.build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
    }
}
