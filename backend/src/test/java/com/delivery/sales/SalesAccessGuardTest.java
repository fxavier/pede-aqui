package com.delivery.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SalesAccessGuardTest {
    private final UUID tenantId = UUID.randomUUID();
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final SalesAccessGuard guard = new SalesAccessGuard(vendorRepository);

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
    void ownVendorFallsBackToTheTenantsSingleVendor() {
        UUID vendorId = UUID.randomUUID();
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(
                new Vendor(vendorId, tenantId, "Loja", null, null, null, "Owner", null, null, null, null, null)));
        authenticate("ROLE_VENDOR_ADMIN");

        assertThat(guard.resolveOwnVendorId(tenantId)).isEqualTo(vendorId);
    }

    @Test
    void ownVendorIsUnresolvedWhenTheTenantHasSeveralVendors() {
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(
                new Vendor(UUID.randomUUID(), tenantId, "A", null, null, null, "O", null, null, null, null, null),
                new Vendor(UUID.randomUUID(), tenantId, "B", null, null, null, "O", null, null, null, null, null)));
        authenticate("ROLE_VENDOR_ADMIN");

        assertThat(guard.resolveOwnVendorId(tenantId)).isNull();
    }

    @Test
    void ensureOwnVendorOrderBlocksForeignVendorsAndAllowsOwn() {
        UUID ownVendorId = UUID.randomUUID();
        when(vendorRepository.findByTenantId(tenantId)).thenReturn(List.of(
                new Vendor(ownVendorId, tenantId, "Loja", null, null, null, "Owner", null, null, null, null, null)));
        authenticate("ROLE_VENDOR_ADMIN");

        assertThatCode(() -> guard.ensureOwnVendorOrder(tenantId, ownVendorId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.ensureOwnVendorOrder(tenantId, UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void tenantWideRolesBypassVendorScoping() {
        authenticate("ROLE_ADMIN");
        assertThatCode(() -> guard.ensureOwnVendorOrder(tenantId, UUID.randomUUID())).doesNotThrowAnyException();
    }
}
