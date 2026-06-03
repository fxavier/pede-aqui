package com.delivery.auth;

import com.delivery.auth.dto.MerchantRegistrationRequest;
import com.delivery.auth.dto.MerchantRegistrationResponse;
import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.auth.service.MerchantRegistrationService;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.repository.TenantRepository;
import com.delivery.vendor.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MerchantRegistrationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AppUserProfileRepository appUserProfileRepository;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private KeycloakAdminService keycloakAdminService;

    private MerchantRegistrationService merchantRegistrationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        merchantRegistrationService = new MerchantRegistrationService(
                tenantRepository,
                appUserProfileRepository,
                keycloakAdminService,
                vendorRepository
        );
    }

    @Test
    void register_HappyPath_ShouldCompleteSuccessfully() {
        // Arrange
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Company",
                "test-company",
                "USD",
                "Test Company LLC",
                "12-3456789",
                "LLC",
                "Food & Beverage",
                "United States",
                "New York",
                "123 Main St",
                "contact@test.com",
                "+1-555-0123",
                "John",
                "Doe",
                "john@test.com",
                "+1-555-0124",
                "password123",
                null,
                null
        );

        String fakeUserId = "fake-keycloak-user-id";
        
        when(tenantRepository.existsBySlug("test-company")).thenReturn(false);
        when(appUserProfileRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(fakeUserId);

        // Act
        MerchantRegistrationResponse response = merchantRegistrationService.register(request);

        // Assert
        assertNotNull(response);
        assertEquals("test-company", response.tenantSlug());
        assertEquals("john@test.com", response.email());
        assertEquals("John Doe", response.displayName());

        verify(tenantRepository, times(1)).save(any(Tenant.class));
        verify(appUserProfileRepository, times(1)).save(any(AppUserProfile.class));
        verify(vendorRepository, times(1)).save(any()); // This will pass after Ticket 2
        verify(keycloakAdminService, never()).deleteUser(anyString());
    }

    @Test
    void register_CompensationPath_ShouldCallDeleteUser() {
        // Arrange
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Company",
                "test-company",
                "USD",
                "Test Company LLC",
                "12-3456789",
                "LLC",
                "Food & Beverage",
                "United States",
                "New York",
                "123 Main St",
                "contact@test.com",
                "+1-555-0123",
                "John",
                "Doe",
                "john@test.com",
                "+1-555-0124",
                "password123",
                null,
                null
        );

        String fakeUserId = "fake-keycloak-user-id";
        
        when(tenantRepository.existsBySlug("test-company")).thenReturn(false);
        when(appUserProfileRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(fakeUserId);
        when(tenantRepository.save(any(Tenant.class))).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(Exception.class, () -> merchantRegistrationService.register(request));

        verify(keycloakAdminService, times(1)).deleteUser(fakeUserId);
    }
}