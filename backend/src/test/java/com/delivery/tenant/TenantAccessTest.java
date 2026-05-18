package com.delivery.tenant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.mapper.TenantMapper;
import com.delivery.tenant.repository.TenantRepository;
import com.delivery.tenant.service.TenantService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantAccessTest {

    @Test
    void inactiveTenantIsRejectedByValidation() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant(tenantId, "Test Tenant", "test", "USD");
        tenant.updateStatus("INACTIVE");

        TenantRepository repository = mock(TenantRepository.class);
        when(repository.findById(tenantId)).thenReturn(Optional.of(tenant));

        TenantService service = new TenantService(repository, new TenantMapper());

        assertThatThrownBy(() -> service.validateActiveTenant(tenantId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tenant is not active");
    }
}
