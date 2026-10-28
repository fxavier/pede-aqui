package com.delivery.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.catalog.service.CatalogAccessGuard;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.vendor.api.MerchantScopeGuard;
import com.delivery.vendor.config.MerchantScopeProperties;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.MerchantUserRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** CatalogAccessGuard delegates the caller's vendor to MerchantScopeGuard (ADR-0001 step 3 / ADR-0002 C6). */
class CatalogAccessGuardTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final MerchantUserRepository merchantUsers = mock(MerchantUserRepository.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final CatalogAccessGuard guard = new CatalogAccessGuard(
            new MerchantScopeGuard(tenantContext, merchantUsers, vendorRepository, new MerchantScopeProperties()));

    CatalogAccessGuardTest() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("user-1"));
    }

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
    void merchantUsersAssociationDecidesInAMultiVendorTenant() {
        UUID otherVendor = UUID.randomUUID();
        when(merchantUsers.findVendorIdsByTenantAndKeycloakUser(tenantId, "user-1")).thenReturn(List.of(vendorId));
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorId), vendor(otherVendor)));

        authenticate("ROLE_VENDOR_STAFF", Map.of());
        assertThatCode(() -> guard.ensureCanManageVendor(vendorId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.ensureCanManageVendor(otherVendor))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("vendor_access_denied"));
    }

    @Test
    void vendorAdminWithoutClaimFallsBackToTheTenantsSingleVendorOrFailsClosed() {
        authenticate("ROLE_VENDOR_ADMIN", Map.of());
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorId)));
        assertThatCode(() -> guard.ensureCanManageVendor(vendorId)).doesNotThrowAnyException();

        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of());
        assertThatThrownBy(() -> guard.ensureCanManageVendor(vendorId))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("vendor_scope_unresolved");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void opsAndAdminAreTenantWideEvenWithForeignVendorClaim() {
        authenticate("ROLE_OPS", Map.of("vendor_id", UUID.randomUUID().toString()));
        assertThatCode(() -> guard.ensureCanManageVendor(vendorId)).doesNotThrowAnyException();

        authenticate("ROLE_ADMIN", Map.of("vendor_id", UUID.randomUUID().toString()));
        assertThatCode(() -> guard.ensureCanManageVendor(vendorId)).doesNotThrowAnyException();
    }

    private Vendor vendor(UUID id) {
        return new Vendor(id, tenantId, "Loja", null, null, null, "Owner", null, null, null, null, null);
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
