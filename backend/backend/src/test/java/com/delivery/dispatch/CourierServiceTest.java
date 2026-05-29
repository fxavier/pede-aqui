package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierVerificationStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.dto.CreateCourierRequest;
import com.delivery.dispatch.repository.CourierDocumentRepository;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.service.CourierService;
import com.delivery.delivery.repository.DeliveryRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CourierServiceTest {
    @Test
    void returnsOnlyVerifiedOnlineCouriersInZone() {
        UUID tenantId = UUID.randomUUID();
        UUID zoneA = UUID.randomUUID();
        CourierRepository courierRepository = mock(CourierRepository.class);
        when(courierRepository.findByTenantIdAndVerificationStatusAndAvailableAndOperatingZoneId(
                        tenantId, CourierVerificationStatus.APPROVED, true, zoneA))
                .thenReturn(List.of(new Courier(UUID.randomUUID(), tenantId, UUID.randomUUID(), zoneA)));

        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));

        CourierService service = new CourierService(courierRepository, mock(CourierDocumentRepository.class), mock(AppUserProfileRepository.class), mock(DeliveryRepository.class), new DispatchMapper(), tenantContext);

        var eligible = service.eligibleInZone(zoneA);

        assertThat(eligible).hasSize(1);
        assertThat(eligible.getFirst().getOperatingZoneId()).isEqualTo(zoneA);
    }

    @Test
    void createThrowsExceptionWhenCourierTriesToRegisterDifferentProfile() {
        UUID tenantId = UUID.randomUUID();
        UUID callerProfileId = UUID.randomUUID();
        UUID differentProfileId = UUID.randomUUID();
        String callerKeycloakUserId = "caller-user-id";

        // Mock COURIER role authentication
        GrantedAuthority courierAuthority = new SimpleGrantedAuthority("ROLE_COURIER");
        Authentication authentication = mock(Authentication.class);
        doReturn(List.of(courierAuthority)).when(authentication).getAuthorities();
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Mock repositories and context
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(callerKeycloakUserId));

        AppUserProfile callerProfile = mock(AppUserProfile.class);
        when(callerProfile.getId()).thenReturn(callerProfileId);
        AppUserProfileRepository userProfileRepository = mock(AppUserProfileRepository.class);
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, callerKeycloakUserId))
                .thenReturn(Optional.of(callerProfile));

        CourierService service = new CourierService(
                mock(CourierRepository.class),
                mock(CourierDocumentRepository.class),
                userProfileRepository,
                mock(DeliveryRepository.class),
                new DispatchMapper(),
                tenantContext
        );

        CreateCourierRequest request = new CreateCourierRequest(differentProfileId, UUID.randomUUID(), null, null, null, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.create(request);
        });

        assertThat(exception.getCode()).isEqualTo("forbidden_user_profile");
        assertThat(exception.getMessage()).isEqualTo("Couriers may only register their own profile");
    }

    @Test
    void createPersistsProfileFieldsFromRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID operatingZoneId = UUID.randomUUID();
        String keycloakUserId = "admin-user";

        // Mock ADMIN role authentication
        GrantedAuthority adminAuthority = new SimpleGrantedAuthority("ROLE_ADMIN");
        Authentication authentication = mock(Authentication.class);
        doReturn(List.of(adminAuthority)).when(authentication).getAuthorities();
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Mock tenant context
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));

        // Mock repository
        CourierRepository courierRepository = mock(CourierRepository.class);
        ArgumentCaptor<Courier> courierCaptor = ArgumentCaptor.forClass(Courier.class);

        CourierService service = new CourierService(
                courierRepository,
                mock(CourierDocumentRepository.class),
                mock(AppUserProfileRepository.class),
                mock(DeliveryRepository.class),
                new DispatchMapper(),
                tenantContext
        );

        // Create request with profile fields
        LocalDate dateOfBirth = LocalDate.of(1990, 1, 15);
        CreateCourierRequest request = new CreateCourierRequest(
                profileId, 
                operatingZoneId, 
                "John Doe", 
                "123456789", 
                "123456789", 
                "Motorcycle", 
                "AB-12-34", 
                dateOfBirth
        );

        service.create(request);

        // Verify courier was saved with profile fields
        verify(courierRepository).save(courierCaptor.capture());
        Courier savedCourier = courierCaptor.getValue();
        
        assertThat(savedCourier.getTenantId()).isEqualTo(tenantId);
        assertThat(savedCourier.getUserProfileId()).isEqualTo(profileId);
        assertThat(savedCourier.getOperatingZoneId()).isEqualTo(operatingZoneId);
        assertThat(savedCourier.getFullName()).isEqualTo("John Doe");
        assertThat(savedCourier.getPhone()).isEqualTo("123456789");
        assertThat(savedCourier.getNif()).isEqualTo("123456789");
        assertThat(savedCourier.getVehicleType()).isEqualTo("Motorcycle");
        assertThat(savedCourier.getVehiclePlate()).isEqualTo("AB-12-34");
        assertThat(savedCourier.getDateOfBirth()).isEqualTo(dateOfBirth);
    }
}
