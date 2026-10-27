package com.delivery.auth;

import com.delivery.auth.dto.MerchantRegistrationRequest;
import com.delivery.auth.dto.MerchantRegistrationResponse;
import com.delivery.auth.dto.TenantProvisioningRequest;
import com.delivery.auth.dto.TenantProvisioningResponse;
import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.entity.ProvisioningRequest;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.auth.repository.ProvisioningRequestRepository;
import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.auth.service.MerchantRegistrationService;
import com.delivery.auth.service.SubscriptionProvisioningService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.MarketplaceRole;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SubscriptionProvisioningServiceTest {

    @Mock
    private MerchantRegistrationService merchantRegistrationService;
    @Mock
    private KeycloakAdminService keycloakAdminService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private AppUserProfileRepository appUserProfileRepository;
    @Mock
    private ProvisioningRequestRepository provisioningRequestRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query nativeQuery;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SubscriptionProvisioningService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userProfileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyInt(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(0);

        service = new SubscriptionProvisioningService(
                merchantRegistrationService,
                keycloakAdminService,
                tenantRepository,
                appUserProfileRepository,
                provisioningRequestRepository,
                objectMapper,
                transactionManager,
                50,
                10);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private TenantProvisioningRequest lead() {
        return new TenantProvisioningRequest(
                "Restaurante Costa Nova", "Ana Machava", "Ana@costanova.co.mz",
                "+258-84-123-4567", "Maputo", "pro-monthly", "MONTHLY", "sub_1");
    }

    private void stubSuccessfulSaga() {
        when(merchantRegistrationService.register(any())).thenReturn(new MerchantRegistrationResponse(
                tenantId, "restaurante-costa-nova", userProfileId, "ana@costanova.co.mz", "Ana Machava"));
        when(appUserProfileRepository.findById(userProfileId)).thenReturn(Optional.of(new AppUserProfile(
                userProfileId, tenantId, "kc-user-1", "ana@costanova.co.mz", "Ana Machava",
                Set.of(MarketplaceRole.VENDOR_ADMIN))));
    }

    @Test
    void provision_NewLead_MapsDefaultsAndDelegatesToSaga() {
        stubSuccessfulSaga();

        TenantProvisioningResponse response = service.provision("key-1", lead());

        ArgumentCaptor<MerchantRegistrationRequest> captor = ArgumentCaptor.forClass(MerchantRegistrationRequest.class);
        verify(merchantRegistrationService).register(captor.capture());
        MerchantRegistrationRequest mapped = captor.getValue();
        assertEquals("MZN", mapped.defaultCurrency());
        assertEquals("MZ", mapped.country());
        assertEquals("restaurante-costa-nova", mapped.companySlug());
        assertEquals("Ana", mapped.firstName());
        assertEquals("Machava", mapped.lastName());
        assertEquals("ana@costanova.co.mz", mapped.email());
        assertTrue(mapped.password().length() >= 8);

        assertTrue(response.provisioned());
        assertFalse(response.alreadyExisted());
        assertEquals(tenantId, response.tenantId());
        assertEquals("ACTIVE", response.status());

        ArgumentCaptor<ProvisioningRequest> rowCaptor = ArgumentCaptor.forClass(ProvisioningRequest.class);
        verify(provisioningRequestRepository).save(rowCaptor.capture());
        assertEquals(ProvisioningRequest.STATUS_SUCCEEDED, rowCaptor.getValue().getStatus());
        verify(keycloakAdminService).sendPasswordSetupEmail("kc-user-1");
    }

    @Test
    void provision_SlugCollision_AppendsNumericSuffix() {
        stubSuccessfulSaga();
        when(tenantRepository.existsBySlug("restaurante-costa-nova")).thenReturn(true);

        service.provision("key-1", lead());

        ArgumentCaptor<MerchantRegistrationRequest> captor = ArgumentCaptor.forClass(MerchantRegistrationRequest.class);
        verify(merchantRegistrationService).register(captor.capture());
        assertEquals("restaurante-costa-nova-2", captor.getValue().companySlug());
    }

    @Test
    void provision_SingleWordContactName_UsesItForBothNames() {
        stubSuccessfulSaga();
        TenantProvisioningRequest request = new TenantProvisioningRequest(
                "Costa Nova", "Ana", "ana@costanova.co.mz", "+258", null, "pro", "MONTHLY", null);

        service.provision("key-1", request);

        ArgumentCaptor<MerchantRegistrationRequest> captor = ArgumentCaptor.forClass(MerchantRegistrationRequest.class);
        verify(merchantRegistrationService).register(captor.capture());
        assertEquals("Ana", captor.getValue().firstName());
        assertEquals("Ana", captor.getValue().lastName());
    }

    @Test
    void provision_ReplayedKey_ReturnsCachedResponseWithoutSaga() throws Exception {
        TenantProvisioningResponse original = new TenantProvisioningResponse(
                tenantId, "restaurante-costa-nova", userProfileId, "ana@costanova.co.mz",
                "Ana Machava", "ACTIVE", true, false);
        ProvisioningRequest row = new ProvisioningRequest(
                UUID.randomUUID(), "key-1", "ana@costanova.co.mz", null, ProvisioningRequest.STATUS_SUCCEEDED);
        row.markSucceeded(tenantId, objectMapper.writeValueAsString(original));
        when(provisioningRequestRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(row));

        TenantProvisioningResponse response = service.provision("key-1", lead());

        assertTrue(response.alreadyExisted());
        assertFalse(response.provisioned());
        assertEquals(tenantId, response.tenantId());
        verify(merchantRegistrationService, never()).register(any());
        verify(keycloakAdminService, never()).sendPasswordSetupEmail(anyString());
    }

    @Test
    void provision_KnownEmailWithNewKey_ReturnsExistingTenant() {
        AppUserProfile profile = new AppUserProfile(
                userProfileId, tenantId, "kc-user-1", "ana@costanova.co.mz", "Ana Machava",
                Set.of(MarketplaceRole.VENDOR_ADMIN));
        when(appUserProfileRepository.findFirstByEmail("ana@costanova.co.mz")).thenReturn(Optional.of(profile));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(
                new Tenant(tenantId, "Restaurante Costa Nova", "restaurante-costa-nova", "MZN")));

        TenantProvisioningResponse response = service.provision("key-2", lead());

        assertTrue(response.alreadyExisted());
        assertEquals(tenantId, response.tenantId());
        verify(merchantRegistrationService, never()).register(any());
    }

    @Test
    void provision_KeycloakFailure_RecordsFailedAttemptAndMapsToDownstreamIdp() {
        when(merchantRegistrationService.register(any()))
                .thenThrow(new BusinessException("keycloak_error", "boom", HttpStatus.INTERNAL_SERVER_ERROR));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> service.provision("key-1", lead()));

        assertEquals("downstream_idp", thrown.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, thrown.getStatus());
        ArgumentCaptor<ProvisioningRequest> rowCaptor = ArgumentCaptor.forClass(ProvisioningRequest.class);
        verify(provisioningRequestRepository).save(rowCaptor.capture());
        assertEquals(ProvisioningRequest.STATUS_FAILED, rowCaptor.getValue().getStatus());
    }

    @Test
    void provision_EmailExistsRace_FallsBackToExistingTenant() {
        when(merchantRegistrationService.register(any()))
                .thenThrow(new BusinessException("email_exists", "exists", HttpStatus.CONFLICT));
        AppUserProfile profile = new AppUserProfile(
                userProfileId, tenantId, "kc-user-1", "ana@costanova.co.mz", "Ana Machava",
                Set.of(MarketplaceRole.VENDOR_ADMIN));
        when(appUserProfileRepository.findFirstByEmail("ana@costanova.co.mz"))
                .thenReturn(Optional.empty(), Optional.of(profile));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(
                new Tenant(tenantId, "Restaurante Costa Nova", "restaurante-costa-nova", "MZN")));

        TenantProvisioningResponse response = service.provision("key-2", lead());

        assertTrue(response.alreadyExisted());
        assertEquals(tenantId, response.tenantId());
        verify(provisioningRequestRepository, never()).save(any());
    }

    @Test
    void provision_HourlyCapReached_RejectsWithRateLimited() {
        when(provisioningRequestRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(50L);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> service.provision("key-1", lead()));

        assertEquals("rate_limited", thrown.getCode());
        verify(merchantRegistrationService, never()).register(any());
    }
}
