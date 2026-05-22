package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierVerificationStatus;
import com.delivery.dispatch.mapper.DispatchMapper;
import com.delivery.dispatch.repository.CourierRepository;
import com.delivery.dispatch.service.CourierService;
import com.delivery.delivery.repository.DeliveryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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

        CourierService service = new CourierService(courierRepository, mock(AppUserProfileRepository.class), mock(DeliveryRepository.class), new DispatchMapper(), tenantContext);

        var eligible = service.eligibleInZone(zoneA);

        assertThat(eligible).hasSize(1);
        assertThat(eligible.getFirst().getOperatingZoneId()).isEqualTo(zoneA);
    }
}
