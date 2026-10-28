package com.delivery.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** ADR-0001 step 3: merchant scope comes from merchant_users; the legacy heuristic only behind the flag; fail closed. */
class MerchantScopeGuardTest {
    private static final String USER = "kc-merchant-user";

    private final UUID tenantId = UUID.randomUUID();
    private final UUID vendorA = UUID.randomUUID();
    private final UUID vendorB = UUID.randomUUID();
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final MerchantUserRepository merchantUsers = mock(MerchantUserRepository.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final MerchantScopeProperties properties = new MerchantScopeProperties();
    private final MerchantScopeGuard guard = new MerchantScopeGuard(tenantContext, merchantUsers, vendorRepository, properties);

    MerchantScopeGuardTest() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(USER));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- merchant_users is the source of truth ---

    @Test
    void vendorAdminResolvesToItsAssociatedVendorInAMultiVendorTenant() {
        associated(vendorA);
        twoVendorsInTenant();
        authenticate(null, "ROLE_VENDOR_ADMIN");

        assertThat(guard.requireOwnVendorId(tenantId)).isEqualTo(vendorA);
        assertThatCode(() -> guard.ensureCallerOwnsVendor(vendorA, "denied")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.ensureCallerOwnsVendor(vendorB, "denied"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("vendor_access_denied");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        verify(vendorRepository, never()).findByTenantId(tenantId);
    }

    @Test
    void vendorStaffResolvesThroughMerchantUsersToo() {
        associated(vendorB);
        twoVendorsInTenant();
        authenticate(null, "ROLE_VENDOR_STAFF");

        assertThat(guard.isMerchantScoped()).isTrue();
        assertThat(guard.isVendorAdminScoped()).isFalse();
        assertThat(guard.requireOwnVendorId(tenantId)).isEqualTo(vendorB);
    }

    @Test
    void userAssociatedWithSeveralVendorsNeedsAMatchingClaimOtherwiseUnresolved() {
        associated(vendorA, vendorB);
        authenticate(null, "ROLE_VENDOR_ADMIN");
        assertThatThrownBy(() -> guard.requireOwnVendorId(tenantId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("vendor_scope_unresolved"));

        authenticate(vendorB, "ROLE_VENDOR_ADMIN");
        assertThat(guard.requireOwnVendorId(tenantId)).isEqualTo(vendorB);
    }

    @Test
    void claimOutsideTheCallersAssociationsIsRefusedEvenWithFallbackOn() {
        associated(vendorA);
        authenticate(vendorB, "ROLE_VENDOR_ADMIN");

        assertThatThrownBy(() -> guard.resolveOwnVendorId(tenantId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("vendor_access_denied"));
    }

    // --- legacy fallback (flag on) ---

    @Test
    void withoutAssociationAndFallbackOnTheClaimThenTheSingleVendorAreUsed() {
        authenticate(vendorA, "ROLE_VENDOR_ADMIN");
        assertThat(guard.resolveOwnVendorId(tenantId)).contains(vendorA);

        authenticate(null, "ROLE_VENDOR_ADMIN");
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorB)));
        assertThat(guard.resolveOwnVendorId(tenantId)).contains(vendorB);
    }

    @Test
    void withoutAssociationInAMultiVendorTenantTheFallbackFailsClosed() {
        twoVendorsInTenant();
        authenticate(null, "ROLE_VENDOR_STAFF");

        assertThat(guard.resolveOwnVendorId(tenantId)).isEmpty();
        assertThatThrownBy(() -> guard.requireOwnVendorId(tenantId))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("vendor_scope_unresolved");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    // --- fallback off: merchant_users only ---

    @Test
    void withFallbackOffNeitherClaimNorSingleVendorResolveAnything() {
        properties.setSingleVendorFallbackEnabled(false);
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorA)));

        authenticate(vendorA, "ROLE_VENDOR_ADMIN");
        assertThat(guard.resolveOwnVendorId(tenantId)).isEmpty();
        authenticate(null, "ROLE_VENDOR_ADMIN");
        assertThatThrownBy(() -> guard.requireOwnVendorId(tenantId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("vendor_scope_unresolved"));
        verify(vendorRepository, never()).findByTenantId(tenantId);

        associated(vendorA);
        assertThat(guard.requireOwnVendorId(tenantId)).isEqualTo(vendorA);
    }

    @Test
    void malformedClaimIsRefused() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(USER)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim(MerchantScopeGuard.VENDOR_ID_CLAIM, "not-a-uuid").build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_VENDOR_ADMIN")));

        assertThatThrownBy(() -> guard.resolveOwnVendorId(tenantId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("vendor_access_denied"));
    }

    @Test
    void ensureCallerOwnsVendorRequiresATenantContext() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.empty());
        authenticate(null, "ROLE_VENDOR_ADMIN");

        assertThatThrownBy(() -> guard.ensureCallerOwnsVendor(vendorA, "denied"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("tenant_required"));
    }

    @Test
    void tenantWideRolesAreNeverMerchantScoped() {
        for (String role : List.of("ROLE_ADMIN", "ROLE_OPS", "ROLE_FINANCE")) {
            authenticate(null, "ROLE_VENDOR_ADMIN", role);
            assertThat(guard.isMerchantScoped()).as(role).isFalse();
            assertThat(guard.isVendorAdminScoped()).as(role).isFalse();
        }
    }

    private void associated(UUID... vendorIds) {
        when(merchantUsers.findVendorIdsByTenantAndKeycloakUser(tenantId, USER)).thenReturn(List.of(vendorIds));
    }

    private void twoVendorsInTenant() {
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorA), vendor(vendorB)));
    }

    private Vendor vendor(UUID id) {
        return new Vendor(id, tenantId, "Loja " + id, null, null, null, "Owner", null, null, null, null, null);
    }

    private static void authenticate(UUID vendorClaim, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject(USER)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (vendorClaim != null) {
            builder.claim(MerchantScopeGuard.VENDOR_ID_CLAIM, vendorClaim.toString());
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(builder.build(), AuthorityUtils.createAuthorityList(roles)));
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> noClaims() {
        return Map.of();
    }
}
