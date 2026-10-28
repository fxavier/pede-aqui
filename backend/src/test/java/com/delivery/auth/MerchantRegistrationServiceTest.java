package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.dto.MerchantRegistrationRequest;
import com.delivery.auth.dto.MerchantRegistrationResponse;
import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.auth.service.MerchantRegistrationService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.MarketplaceRole;
import com.delivery.common.security.TenancyProperties;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.repository.TenantRepository;
import com.delivery.vendor.entity.MerchantRole;
import com.delivery.vendor.entity.MerchantUser;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.entity.VendorVerificationStatus;
import com.delivery.vendor.repository.MerchantUserRepository;
import com.delivery.vendor.repository.VendorRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** ADR-0001 step 4: self-service registration lands in the operator tenant; M2M provisioning still mints a tenant. */
class MerchantRegistrationServiceTest {
    private static final String KEYCLOAK_USER = "fake-keycloak-user-id";

    private final UUID operatorTenantId = UUID.randomUUID();
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final AppUserProfileRepository appUserProfileRepository = mock(AppUserProfileRepository.class);
    private final VendorRepository vendorRepository = mock(VendorRepository.class);
    private final MerchantUserRepository merchantUserRepository = mock(MerchantUserRepository.class);
    private final KeycloakAdminService keycloakAdminService = mock(KeycloakAdminService.class);
    private MerchantRegistrationService service;

    @BeforeEach
    void setUp() {
        TenancyProperties tenancy = new TenancyProperties();
        tenancy.setOperatorTenantId(operatorTenantId);
        when(tenantRepository.findById(operatorTenantId))
                .thenReturn(Optional.of(new Tenant(operatorTenantId, "Pede-Aqui", "pede-aqui-operador", "MZN")));
        when(appUserProfileRepository.existsByEmail(anyString())).thenReturn(false);
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(KEYCLOAK_USER);
        service = new MerchantRegistrationService(tenantRepository, appUserProfileRepository, keycloakAdminService,
                vendorRepository, merchantUserRepository, tenancy);
    }

    @Test
    void registerCreatesPendingVendorOwnerAndProfileInTheOperatorTenantWithoutATenant() {
        MerchantRegistrationResponse response = service.register(request());

        assertThat(response.tenantId()).isEqualTo(operatorTenantId);
        assertThat(response.tenantSlug()).isEqualTo("pede-aqui-operador");
        assertThat(response.email()).isEqualTo("john@test.com");
        assertThat(response.displayName()).isEqualTo("John Doe");
        assertThat(response.vendorVerificationStatus()).isEqualTo("PENDING");
        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(tenantRepository, never()).existsBySlug(anyString());
        verify(keycloakAdminService).createUser(eq("john@test.com"), eq("John"), eq("Doe"), eq("password123"), eq(operatorTenantId.toString()));

        ArgumentCaptor<AppUserProfile> profile = ArgumentCaptor.forClass(AppUserProfile.class);
        verify(appUserProfileRepository).save(profile.capture());
        assertThat(profile.getValue().getTenantId()).isEqualTo(operatorTenantId);
        assertThat(profile.getValue().getRoles()).containsExactly(MarketplaceRole.VENDOR_ADMIN);

        ArgumentCaptor<Vendor> vendor = ArgumentCaptor.forClass(Vendor.class);
        verify(vendorRepository).save(vendor.capture());
        assertThat(vendor.getValue().getTenantId()).isEqualTo(operatorTenantId);
        assertThat(vendor.getValue().getVerificationStatus()).isEqualTo(VendorVerificationStatus.PENDING);
        assertThat(vendor.getValue().isAvailable()).isFalse();
        assertThat(vendor.getValue().getId()).isEqualTo(response.vendorId());

        ArgumentCaptor<MerchantUser> association = ArgumentCaptor.forClass(MerchantUser.class);
        verify(merchantUserRepository).save(association.capture());
        assertThat(association.getValue().getTenantId()).isEqualTo(operatorTenantId);
        assertThat(association.getValue().getVendorId()).isEqualTo(response.vendorId());
        assertThat(association.getValue().getAppUserProfileId()).isEqualTo(response.userProfileId());
        assertThat(association.getValue().getRole()).isEqualTo(MerchantRole.OWNER);
        verify(keycloakAdminService, never()).deleteUser(anyString());
    }

    @Test
    void registerRefusesADuplicateEmailBeforeTouchingKeycloak() {
        when(appUserProfileRepository.existsByEmail("john@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("email_exists");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(keycloakAdminService, never()).createUser(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void registerCompensatesTheKeycloakUserWhenPersistenceFails() {
        when(vendorRepository.save(any(Vendor.class))).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> service.register(request()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("registration_error"));

        verify(keycloakAdminService, times(1)).deleteUser(KEYCLOAK_USER);
        verify(merchantUserRepository, never()).save(any());
    }

    @Test
    void provisionTenantStillCreatesATenantWithOwnerVendorAndAssociation() {
        when(tenantRepository.existsBySlug("test-company")).thenReturn(false);

        MerchantRegistrationResponse response = service.provisionTenant(request());

        assertThat(response.tenantId()).isNotEqualTo(operatorTenantId);
        assertThat(response.tenantSlug()).isEqualTo("test-company");
        ArgumentCaptor<Tenant> tenant = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenant.capture());
        assertThat(tenant.getValue().getId()).isEqualTo(response.tenantId());
        verify(keycloakAdminService).createUser(anyString(), anyString(), anyString(), anyString(), eq(response.tenantId().toString()));
        ArgumentCaptor<MerchantUser> association = ArgumentCaptor.forClass(MerchantUser.class);
        verify(merchantUserRepository).save(association.capture());
        assertThat(association.getValue().getTenantId()).isEqualTo(response.tenantId());
        assertThat(association.getValue().getRole()).isEqualTo(MerchantRole.OWNER);
    }

    @Test
    void provisionTenantRefusesATakenSlug() {
        when(tenantRepository.existsBySlug("test-company")).thenReturn(true);

        assertThatThrownBy(() -> service.provisionTenant(request()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("tenant_slug_exists"));
        verify(keycloakAdminService, never()).createUser(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void provisionTenantCompensatesWhenTheTenantCannotBeSaved() {
        when(tenantRepository.save(any(Tenant.class))).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> service.provisionTenant(request())).isInstanceOf(BusinessException.class);

        verify(keycloakAdminService, times(1)).deleteUser(KEYCLOAK_USER);
    }

    private static MerchantRegistrationRequest request() {
        return new MerchantRegistrationRequest(
                "Test Company", "test-company", "USD", "Test Company LLC", "12-3456789", "LLC", "Food & Beverage",
                "United States", "New York", "123 Main St", "contact@test.com", "+1-555-0123",
                "John", "Doe", "john@test.com", "+1-555-0124", "password123", null, null);
    }
}
