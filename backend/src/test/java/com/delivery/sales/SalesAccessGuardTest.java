package com.delivery.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.vendor.MerchantScopeFixtures;
import com.delivery.vendor.api.MerchantScopeGuard;
import com.delivery.vendor.config.MerchantScopeProperties;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.MerchantUserRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** SalesAccessGuard delegates vendor scope to MerchantScopeGuard (ADR-0001 step 3) and keeps PII masking. */
class SalesAccessGuardTest {
    private final UUID tenantId = UUID.randomUUID();
    private final TenantContext tenantContext = mock(TenantContext.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final MerchantUserRepository merchantUsers = mock(MerchantUserRepository.class);
    private final SalesAccessGuard guard = new SalesAccessGuard(
            new MerchantScopeGuard(tenantContext, merchantUsers, vendorRepository, new MerchantScopeProperties()));

    SalesAccessGuardTest() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("kc-user"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "n/a", roles));
    }

    @Test
    void vendorAdminIsVendorScopedUnlessAlsoTenantWide() {
        authenticate("ROLE_VENDOR_ADMIN");
        assertThat(guard.isVendorScoped()).isTrue();

        authenticate("ROLE_VENDOR_ADMIN", "ROLE_ADMIN");
        assertThat(guard.isVendorScoped()).isFalse();

        authenticate("ROLE_OPS");
        assertThat(guard.isVendorScoped()).isFalse();
    }

    @Test
    void onlyPureSupportGetsMaskedPii() {
        authenticate("ROLE_SUPPORT");
        assertThat(guard.shouldMaskCustomerPii()).isTrue();

        authenticate("ROLE_SUPPORT", "ROLE_FINANCE");
        assertThat(guard.shouldMaskCustomerPii()).isFalse();

        authenticate("ROLE_ADMIN");
        assertThat(guard.shouldMaskCustomerPii()).isFalse();
    }

    @Test
    void ownVendorComesFromMerchantUsersInAMultiVendorTenant() {
        UUID vendorA = UUID.randomUUID();
        UUID vendorB = UUID.randomUUID();
        when(merchantUsers.findVendorIdsByTenantAndKeycloakUser(tenantId, "kc-user")).thenReturn(List.of(vendorB));
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorA), vendor(vendorB)));
        authenticate("ROLE_VENDOR_ADMIN");

        assertThat(guard.resolveOwnVendorId(tenantId)).isEqualTo(vendorB);
        assertThatCode(() -> guard.ensureOwnVendorOrder(tenantId, vendorB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.ensureOwnVendorOrder(tenantId, vendorA))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("vendor_access_denied"));
    }

    @Test
    void ownVendorFallsBackToTheTenantsSingleVendorWhileTheFlagIsOn() {
        UUID vendorId = UUID.randomUUID();
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(vendorId)));
        authenticate("ROLE_VENDOR_ADMIN");

        assertThat(guard.resolveOwnVendorId(tenantId)).isEqualTo(vendorId);
    }

    @Test
    void unresolvedScopeFailsClosedInsteadOfWideningToTheTenant() {
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(UUID.randomUUID()), vendor(UUID.randomUUID())));
        authenticate("ROLE_VENDOR_ADMIN");

        assertThatThrownBy(() -> guard.resolveOwnVendorId(tenantId))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("vendor_scope_unresolved");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        assertThatThrownBy(() -> guard.ensureOwnVendorOrder(tenantId, UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("vendor_scope_unresolved"));
    }

    @Test
    void merchantScopeCoversVendorAdminAndVendorStaffButNotTenantWideRoles() {
        authenticate("ROLE_VENDOR_STAFF");
        assertThat(guard.isMerchantScoped()).isTrue();
        assertThat(guard.isVendorScoped()).isFalse(); // sales semantics unchanged

        authenticate("ROLE_VENDOR_ADMIN");
        assertThat(guard.isMerchantScoped()).isTrue();

        authenticate("ROLE_VENDOR_STAFF", "ROLE_OPS");
        assertThat(guard.isMerchantScoped()).isFalse();
    }

    @Test
    void requireOwnVendorIdFailsClosedWhenAmbiguous() {
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(vendor(UUID.randomUUID()), vendor(UUID.randomUUID())));
        authenticate("ROLE_VENDOR_STAFF");

        assertThatThrownBy(() -> guard.requireOwnVendorId(tenantId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void vendorClaimIsStillHonouredByTheLegacyFixtureGuard() {
        SalesAccessGuard legacy = new SalesAccessGuard(MerchantScopeFixtures.legacyFallback(tenantContext));
        UUID claimed = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("kc-user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("vendor_id", claimed.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_VENDOR_ADMIN")));

        assertThat(legacy.requireOwnVendorId(tenantId)).isEqualTo(claimed);
    }

    @Test
    void maskNameKeepsFirstLetterOfEachWord() {
        assertThat(SalesAccessGuard.maskName("Ana  Lima")).isEqualTo("A*** L***");
        assertThat(SalesAccessGuard.maskName(null)).isNull();
    }

    @Test
    void tenantWideRolesBypassVendorScoping() {
        authenticate("ROLE_ADMIN");
        assertThatCode(() -> guard.ensureOwnVendorOrder(tenantId, UUID.randomUUID())).doesNotThrowAnyException();
    }

    private Vendor vendor(UUID id) {
        return new Vendor(id, tenantId, "Loja", null, null, null, "Owner", null, null, null, null, null);
    }
}
